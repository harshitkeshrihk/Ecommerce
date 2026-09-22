package com.example.vishnu.repository

import com.example.vishnu.model.RfqDraftLine
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Wholesale order creation from the Quick-Order Pad (Doc 4 §02). */
class QuickOrderRepositoryTest {

    private val thali = testProduct("thali", priceWholesale = 100.0)
    private val jug = testProduct("jug", priceWholesale = 300.0)

    private val user = FakeCurrentUser()
    private val orderDb = FakeOrderDataSource()
    private val repo = QuickOrderRepository(orderDb, user)

    @Test
    fun `places a confirmed wholesale order at the resolved prices`() = runTest {
        val lines = listOf(RfqDraftLine(thali, 50), RfqDraftLine(jug, 10))
        val resolved = mapOf("thali" to 80.0, "jug" to 270.0)

        assertTrue(repo.placeOrder(lines, resolved, "Warehouse 4"))

        val (orderId, order) = orderDb.orders.single()
        assertEquals("wholesale", order.channel)
        assertEquals("CONFIRMED", order.status)
        assertTrue(order.paymentId.startsWith("QUICK-ORDER-"))
        assertEquals(50 * 80.0 + 10 * 270.0, order.totalAmount, 0.0)
        assertEquals("Warehouse 4", order.address)

        assertTrue(orderDb.items.all { it.orderId == orderId })
        assertEquals(80.0, orderDb.items.single { it.productId == "thali" }.price, 0.0)
        assertEquals(270.0, orderDb.items.single { it.productId == "jug" }.price, 0.0)
    }

    @Test
    fun `order total matches the sum of its line items`() = runTest {
        val lines = listOf(RfqDraftLine(thali, 7), RfqDraftLine(jug, 3))
        repo.placeOrder(lines, mapOf("thali" to 95.5), "addr")

        val total = orderDb.orders.single().second.totalAmount
        assertEquals(total, orderDb.items.sumOf { it.price * it.quantity }, 0.0)
    }

    @Test
    fun `falls back to the wholesale base price when a line has no resolved price`() = runTest {
        repo.placeOrder(listOf(RfqDraftLine(jug, 2)), emptyMap(), "addr")

        assertEquals(300.0, orderDb.items.single().price, 0.0)
        assertEquals(600.0, orderDb.orders.single().second.totalAmount, 0.0)
    }

    @Test
    fun `fails without a signed-in user`() = runTest {
        user.id = null
        assertFalse(repo.placeOrder(listOf(RfqDraftLine(thali, 1)), emptyMap(), "addr"))
        assertTrue(orderDb.orders.isEmpty())
    }

    @Test
    fun `fails for an empty pad`() = runTest {
        assertFalse(repo.placeOrder(emptyList(), emptyMap(), "addr"))
        assertTrue(orderDb.orders.isEmpty())
    }

    @Test
    fun `fails when the item insert is rejected`() = runTest {
        orderDb.failItemsInsert = true
        assertFalse(repo.placeOrder(listOf(RfqDraftLine(thali, 1)), emptyMap(), "addr"))
    }
}
