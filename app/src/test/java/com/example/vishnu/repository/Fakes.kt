package com.example.vishnu.repository

import com.example.vishnu.model.CartRequest
import com.example.vishnu.model.CartResponse
import com.example.vishnu.model.OrderItemRequest
import com.example.vishnu.model.OrderRequest
import com.example.vishnu.model.Product

fun testProduct(
    id: String,
    priceRetail: Double = 100.0,
    priceWholesale: Double = 80.0,
    storeId: String = "store-1"
) = Product(
    id = id,
    name = "Product $id",
    category = "Kitchen",
    subcategory = "Plates",
    priceRetail = priceRetail,
    priceWholesale = priceWholesale,
    imageUrl = "",
    stockCount = 10,
    isAvailable = true,
    storeId = storeId
)

class FakeCurrentUser(var id: String? = "user-1") : CurrentUserProvider {
    override fun userId(): String? = id
}

/** In-memory stand-in for the `cart_items` table (RLS-scoped to one user). */
class FakeCartDataSource(
    private val catalog: Map<String, Product>
) : CartDataSource {
    /** productId -> quantity, insertion-ordered like the table. */
    val rows = linkedMapOf<String, Int>()
    var failNextWrite = false
    var failFetch = false

    override suspend fun fetchCart(): List<CartResponse> {
        if (failFetch) error("network down")
        return rows.entries.mapIndexed { i, (productId, qty) ->
            CartResponse(id = i.toLong(), quantity = qty, product = catalog.getValue(productId))
        }
    }

    override suspend fun insertItem(request: CartRequest) {
        maybeFail()
        check(request.productId !in rows) { "duplicate cart row for ${request.productId}" }
        rows[request.productId] = request.quantity
    }

    override suspend fun updateQuantity(productId: String, quantity: Int) {
        maybeFail()
        if (productId in rows) rows[productId] = quantity
    }

    override suspend fun deleteItem(productId: String) {
        maybeFail()
        rows.remove(productId)
    }

    override suspend fun deleteAll() {
        maybeFail()
        rows.clear()
    }

    private fun maybeFail() {
        if (failNextWrite) {
            failNextWrite = false
            error("write rejected")
        }
    }
}

/** In-memory stand-in for the `orders` + `order_items` tables. */
class FakeOrderDataSource : OrderDataSource {
    val orders = mutableListOf<Pair<Long, OrderRequest>>()
    val items = mutableListOf<OrderItemRequest>()
    var failOrderInsert = false
    var failItemsInsert = false
    private var nextId = 100L

    override suspend fun insertOrder(request: OrderRequest): Long {
        if (failOrderInsert) error("orders insert rejected")
        val id = nextId++
        orders += id to request
        return id
    }

    override suspend fun insertOrderItems(items: List<OrderItemRequest>) {
        if (failItemsInsert) error("order_items insert rejected")
        this.items += items
    }
}

/** In-memory stand-in for the `gifting_orders` table. */
class FakeGiftingOrderDataSource : GiftingOrderDataSource {
    val rows = mutableListOf<com.example.vishnu.model.GiftingOrderRequest>()
    var failInsert = false

    override suspend fun insertGiftingOrder(request: com.example.vishnu.model.GiftingOrderRequest) {
        if (failInsert) error("gifting_orders insert rejected")
        rows += request
    }
}
