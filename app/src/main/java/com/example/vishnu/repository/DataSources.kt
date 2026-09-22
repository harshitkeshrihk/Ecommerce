package com.example.vishnu.repository

import com.example.vishnu.model.CartRequest
import com.example.vishnu.model.CartResponse
import com.example.vishnu.model.OrderItemRequest
import com.example.vishnu.model.OrderRequest
import com.example.vishnu.model.OrderResponse
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.query.Columns
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Thin seams over Supabase so cart/checkout/order-creation logic can be unit
 * tested with in-memory fakes (see app/src/test). The Supabase implementations
 * below are the exact queries the repositories used to run inline.
 */
interface CurrentUserProvider {
    fun userId(): String?
}

interface CartDataSource {
    suspend fun fetchCart(): List<CartResponse>
    suspend fun insertItem(request: CartRequest)
    suspend fun updateQuantity(productId: String, quantity: Int)
    suspend fun deleteItem(productId: String)
    suspend fun deleteAll()
}

interface OrderDataSource {
    /** Inserts the parent order and returns its new id. */
    suspend fun insertOrder(request: OrderRequest): Long
    suspend fun insertOrderItems(items: List<OrderItemRequest>)
}

@Singleton
class SupabaseCurrentUserProvider @Inject constructor(
    private val auth: Auth
) : CurrentUserProvider {
    override fun userId(): String? = auth.currentUserOrNull()?.id
}

@Singleton
class SupabaseCartDataSource @Inject constructor(
    private val postgrest: Postgrest
) : CartDataSource {

    override suspend fun fetchCart(): List<CartResponse> =
        // "select" with joined table: products(*) fetches the full product details
        postgrest["cart_items"]
            .select(columns = Columns.raw("id, quantity, product:products(*)"))
            .decodeList<CartResponse>()

    override suspend fun insertItem(request: CartRequest) {
        postgrest["cart_items"].insert(request)
    }

    override suspend fun updateQuantity(productId: String, quantity: Int) {
        postgrest["cart_items"].update({ set("quantity", quantity) }) {
            // RLS scopes this to the current user's cart
            filter { eq("product_id", productId) }
        }
    }

    override suspend fun deleteItem(productId: String) {
        postgrest["cart_items"].delete {
            filter { eq("product_id", productId) }
        }
    }

    override suspend fun deleteAll() {
        // RLS ensures we only delete our own items
        postgrest["cart_items"].delete {
            filter { neq("id", -1) }
        }
    }
}

@Singleton
class SupabaseOrderDataSource @Inject constructor(
    private val postgrest: Postgrest
) : OrderDataSource {

    override suspend fun insertOrder(request: OrderRequest): Long =
        postgrest["orders"]
            .insert(request) { select() }
            .decodeSingle<OrderResponse>()
            .id

    override suspend fun insertOrderItems(items: List<OrderItemRequest>) {
        postgrest["order_items"].insert(items)
    }
}
