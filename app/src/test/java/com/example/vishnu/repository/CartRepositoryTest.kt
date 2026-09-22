package com.example.vishnu.repository

import com.example.vishnu.model.retailTotal
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression baseline for the retail cart, checkout and order-creation path
 * (Doc 4 §02 acceptance criterion). Phases 2 and 3 must keep this green.
 */
class CartRepositoryTest {

    private val plate = testProduct("plate", priceRetail = 250.0)
    private val bowl = testProduct("bowl", priceRetail = 120.0)
    private val catalog = listOf(plate, bowl).associateBy { it.id }

    private val user = FakeCurrentUser()
    private val cartDb = FakeCartDataSource(catalog)
    private val orderDb = FakeOrderDataSource()
    private val repo = CartRepository(cartDb, orderDb, user)

    // --- Cart ---

    @Test
    fun `fetch returns empty and clears local cart when logged out`() = runTest {
        cartDb.rows["plate"] = 2
        user.id = null

        assertTrue(repo.fetchCartItems().isEmpty())
        assertTrue(repo.cartItems.value.isEmpty())
    }

    @Test
    fun `fetch failure returns empty instead of throwing`() = runTest {
        cartDb.failFetch = true
        assertTrue(repo.fetchCartItems().isEmpty())
    }

    @Test
    fun `adding a new product inserts a row with quantity 1`() = runTest {
        val result = repo.addToCart(plate)

        assertEquals(AddToCartResult.Success, result)
        assertEquals(mapOf("plate" to 1), cartDb.rows)
        assertEquals(1, repo.cartItems.value.single().quantity)
    }

    @Test
    fun `adding a product already in the cart increments instead of duplicating`() = runTest {
        repo.addToCart(plate)
        repo.addToCart(plate)

        assertEquals(mapOf("plate" to 2), cartDb.rows)
    }

    @Test
    fun `adding checks the server cart when the local cache is empty`() = runTest {
        // Row exists server-side (e.g. added on another device) but the
        // repository hasn't fetched yet — must update, not insert a duplicate.
        cartDb.rows["plate"] = 3

        repo.addToCart(plate)

        assertEquals(mapOf("plate" to 4), cartDb.rows)
    }

    @Test
    fun `add failure is reported as an error`() = runTest {
        cartDb.failNextWrite = true
        val result = repo.addToCart(plate)
        assertTrue(result is AddToCartResult.Error)
    }

    @Test
    fun `increment updates local state and the server`() = runTest {
        repo.addToCart(plate)

        repo.incrementQuantity("plate")

        assertEquals(2, repo.cartItems.value.single().quantity)
        assertEquals(2, cartDb.rows["plate"])
    }

    @Test
    fun `failed increment resyncs local state from the server`() = runTest {
        repo.addToCart(plate)
        cartDb.failNextWrite = true

        repo.incrementQuantity("plate")

        assertEquals(1, repo.cartItems.value.single().quantity)
    }

    @Test
    fun `decrement above 1 lowers the quantity`() = runTest {
        repo.addToCart(plate)
        repo.addToCart(plate)

        repo.decrementQuantity("plate")

        assertEquals(1, repo.cartItems.value.single().quantity)
        assertEquals(1, cartDb.rows["plate"])
    }

    @Test
    fun `decrement at 1 removes the item`() = runTest {
        repo.addToCart(plate)

        repo.decrementQuantity("plate")

        assertTrue(cartDb.rows.isEmpty())
        assertTrue(repo.cartItems.value.isEmpty())
    }

    @Test
    fun `decrement of a product not in the cart is a no-op`() = runTest {
        repo.addToCart(plate)
        repo.decrementQuantity("bowl")
        assertEquals(mapOf("plate" to 1), cartDb.rows)
    }

    @Test
    fun `remove deletes only that product`() = runTest {
        repo.addToCart(plate)
        repo.addToCart(bowl)

        repo.removeFromCart("plate")

        assertEquals(mapOf("bowl" to 1), cartDb.rows)
        assertEquals(listOf("bowl"), repo.cartItems.value.map { it.product.id })
    }

    @Test
    fun `clear empties the cart`() = runTest {
        repo.addToCart(plate)
        repo.addToCart(bowl)

        repo.clearCart()

        assertTrue(cartDb.rows.isEmpty())
        assertTrue(repo.cartItems.value.isEmpty())
    }

    // --- Checkout total ---

    @Test
    fun `retail total multiplies retail price by quantity across lines`() = runTest {
        repo.addToCart(plate)
        repo.addToCart(plate)
        repo.addToCart(bowl)

        assertEquals(2 * 250.0 + 120.0, repo.cartItems.value.retailTotal(), 0.0)
    }

    @Test
    fun `retail total ignores wholesale price`() {
        val items = listOf(com.example.vishnu.model.CartItem(testProduct("x", priceRetail = 10.0, priceWholesale = 1.0), 3))
        assertEquals(30.0, items.retailTotal(), 0.0)
    }

    // --- Order creation ---

    @Test
    fun `createOrder writes a paid retail order with one item per cart line`() = runTest {
        repo.addToCart(plate)
        repo.addToCart(plate)
        repo.addToCart(bowl)
        val items = repo.cartItems.value

        val ok = repo.createOrder("pay_123", items.retailTotal(), "12 MG Road", items)

        assertTrue(ok)
        val (orderId, order) = orderDb.orders.single()
        assertEquals("user-1", order.userId)
        assertEquals("pay_123", order.paymentId)
        assertEquals(620.0, order.totalAmount, 0.0)
        assertEquals("12 MG Road", order.address)
        assertEquals("store-1", order.storeId)
        assertEquals("PAID", order.status)
        assertEquals("retail", order.channel)

        assertEquals(2, orderDb.items.size)
        assertTrue(orderDb.items.all { it.orderId == orderId && it.storeId == "store-1" })
        val plateLine = orderDb.items.single { it.productId == "plate" }
        assertEquals(2, plateLine.quantity)
        assertEquals(250.0, plateLine.price, 0.0)
        assertEquals("Product plate", plateLine.productName)
    }

    @Test
    fun `createOrder fails without a signed-in user and writes nothing`() = runTest {
        repo.addToCart(plate)
        val items = repo.cartItems.value
        user.id = null

        assertFalse(repo.createOrder("pay_1", 250.0, "addr", items))
        assertTrue(orderDb.orders.isEmpty())
    }

    @Test
    fun `createOrder fails for an empty cart`() = runTest {
        assertFalse(repo.createOrder("pay_1", 0.0, "addr", emptyList()))
        assertTrue(orderDb.orders.isEmpty())
    }

    @Test
    fun `createOrder fails when the order insert is rejected`() = runTest {
        repo.addToCart(plate)
        orderDb.failOrderInsert = true

        assertFalse(repo.createOrder("pay_1", 250.0, "addr", repo.cartItems.value))
        assertTrue(orderDb.items.isEmpty())
    }

    @Test
    fun `createOrder fails when the item insert is rejected`() = runTest {
        repo.addToCart(plate)
        orderDb.failItemsInsert = true

        assertFalse(repo.createOrder("pay_1", 250.0, "addr", repo.cartItems.value))
        // Known gap: the parent order row is left behind with no items
        // (no rollback). Update this assertion if cleanup is added.
        assertEquals(1, orderDb.orders.size)
    }

    // --- End-to-end checkout (what CartViewModel.onPaymentSuccess does) ---

    @Test
    fun `successful checkout creates the order and empties the cart`() = runTest {
        repo.addToCart(plate)
        repo.addToCart(bowl)

        val fresh = repo.fetchCartItems()
        val ok = repo.createOrder("pay_999", fresh.retailTotal(), "addr", fresh)
        if (ok) repo.clearCart()

        assertTrue(ok)
        assertEquals(370.0, orderDb.orders.single().second.totalAmount, 0.0)
        assertEquals(orderDb.orders.single().second.totalAmount, orderDb.items.sumOf { it.price * it.quantity }, 0.0)
        assertTrue(cartDb.rows.isEmpty())
    }
}
