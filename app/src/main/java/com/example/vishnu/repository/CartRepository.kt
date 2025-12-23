package com.example.vishnu.repository

import android.util.Log
import com.example.vishnu.model.CartItem
import com.example.vishnu.model.CartRequest
import com.example.vishnu.model.CartResponse
import com.example.vishnu.model.Product
import com.example.vishnu.model.toCartItem
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CartRepository @Inject constructor(
    private val postgrest: Postgrest,
    private val auth: Auth
) {
    // We still keep a local flow to update the UI instantly
    private val _cartItems = MutableStateFlow<List<CartItem>>(emptyList())
    val cartItems = _cartItems.asStateFlow()

    suspend fun fetchCartItems() = withContext(Dispatchers.IO){
        if(auth.currentUserOrNull() == null){
            Log.w("CartRepo", "User not logged in, skipping cart fetch.")
            _cartItems.value = emptyList()
            return@withContext
        }
        try {
            // "select" with joined table: products(*) fetches the full product details
            val response = postgrest["cart_items"]
                .select(columns = Columns.raw("id, quantity, product:products(*)"))
                .decodeList<CartResponse>()

            _cartItems.value = response.map { it.toCartItem() }
        } catch (e: Exception) {
            Log.e("CartRepo", "Error fetching cart", e)
        }
    }

    suspend fun addToCart(productId: String) = withContext(Dispatchers.IO) {
        try {
            // 1. Check if item exists in local list (Optimistic check)
            val existingItem = _cartItems.value.find { it.product.id == productId }

            if (existingItem != null) {
                // UPDATE existing quantity
                val newQty = existingItem.quantity + 1
                postgrest["cart_items"].update(
                    { set("quantity", newQty) }
                ) {
                    filter {
                        eq("product_id", productId)
                    }
                }
            } else {
                // INSERT new row
                val request = CartRequest(productId = productId, quantity = 1)
                postgrest["cart_items"].insert(request)
            }

            // 2. Refresh the UI
            fetchCartItems()
        } catch (e: Exception) {
            Log.e("CartRepo", "Error adding to cart", e)
        }
    }

    suspend fun removeFromCart(productId: String) = withContext(Dispatchers.IO) {
        try {
            postgrest["cart_items"].delete {
                filter { eq("product_id", productId) }
            }
            fetchCartItems()
        } catch (e: Exception) {
            Log.e("CartRepo", "Error removing item", e)
        }
    }

    // In CartRepository

    // Increment Quantity
    suspend fun incrementQuantity(productId: String) =
        withContext(Dispatchers.IO) {
            // Optimistic update: Find current qty from local list first
            _cartItems.update { list ->
                list.map { if (it.product.id == productId) it.copy(quantity = it.quantity + 1) else it }
            }

            try {
                // Update Supabase
                postgrest["cart_items"].update(
                    { set("quantity", _cartItems.value.find { it.product.id == productId }!!.quantity) }
                ) {
                    filter {
                        eq("product_id", productId)
                        // Safety: Ensure we only update OUR cart (RLS does this too, but good practice)
                        // eq("user_id", auth.currentUserOrNull()?.id)
                    }
                }
                // Refresh to sync state
                //fetchCartItems()
            } catch (e: Exception) {
                Log.e("CartRepo", "Failed to increment", e)
                fetchCartItems()
            }
    }

    // Decrement Quantity (Handles removal if qty becomes 0)
    fun decrementQuantity(productId: String) {
        CoroutineScope(Dispatchers.IO).launch {
            val currentItem = _cartItems.value.find { it.product.id == productId } ?: return@launch

            if (currentItem.quantity > 1) {
                // Case A: Just lower the number
                _cartItems.update { list ->
                    list.map { if (it.product.id == productId) it.copy(quantity = it.quantity - 1) else it }
                }
                try {
                    postgrest["cart_items"].update(
                        { set("quantity", currentItem.quantity - 1) }
                    ) {
                        filter { eq("product_id", productId) }
                    }
//                    fetchCartItems()
                } catch (e: Exception) {
                    Log.e("CartRepo", "Failed to decrement", e)
                    fetchCartItems()
                }
            } else {
                // Case B: Quantity is 1, so remove the item entirely
                removeFromCart(productId)
            }
        }
    }


}
