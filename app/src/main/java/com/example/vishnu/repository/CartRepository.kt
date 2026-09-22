package com.example.vishnu.repository

import android.util.Log
import com.example.vishnu.model.CartItem
import com.example.vishnu.model.CartRequest
import com.example.vishnu.model.CartResponse
import com.example.vishnu.model.OrderItemRequest
import com.example.vishnu.model.OrderRequest
import com.example.vishnu.model.Product
import com.example.vishnu.model.toCartItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

sealed class AddToCartResult {
    object Success : AddToCartResult()
    data class Error(val message: String) : AddToCartResult()
}

@Singleton
class CartRepository @Inject constructor(
    private val cartDataSource: CartDataSource,
    private val orderDataSource: OrderDataSource,
    private val currentUser: CurrentUserProvider
) {
    // We still keep a local flow to update the UI instantly
    private val _cartItems = MutableStateFlow<List<CartItem>>(emptyList())
    val cartItems = _cartItems.asStateFlow()

    suspend fun fetchCartItems(): List<CartItem> = withContext(Dispatchers.IO){
        if(currentUser.userId() == null){
            Log.w("CartRepo", "User not logged in, skipping cart fetch.")
            _cartItems.value = emptyList()
            return@withContext emptyList()
        }
        try {
            val items = cartDataSource.fetchCart().map { it.toCartItem() }
            _cartItems.value = items

            return@withContext items
        } catch (e: Exception) {
            Log.e("CartRepo", "Error fetching cart", e)
            return@withContext emptyList()
        }
    }

    suspend fun addToCart(product: Product) : AddToCartResult = withContext(Dispatchers.IO) {
        try {
            var currentCart = _cartItems.value

            if (currentCart.isEmpty()) {
                // This waits for the DB to return the actual list
                currentCart = fetchCartItems()
            }

            val existingItem = currentCart.find { it.product.id == product.id }

            if (existingItem != null) {
                // UPDATE existing quantity
                cartDataSource.updateQuantity(product.id, existingItem.quantity + 1)
            } else {
                // INSERT new row
                cartDataSource.insertItem(CartRequest(productId = product.id, quantity = 1))
            }

            // 2. Refresh the UI
            fetchCartItems()
            return@withContext AddToCartResult.Success
        } catch (e: Exception) {
            Log.e("CartRepo", "Error adding to cart", e)
            return@withContext AddToCartResult.Error(e.message ?: "Unknown Error")
        }
    }
    suspend fun removeFromCart(productId: String) = withContext(Dispatchers.IO) {
        try {
            cartDataSource.deleteItem(productId)
            fetchCartItems()
        } catch (e: Exception) {
            Log.e("CartRepo", "Error removing item", e)
        }
    }

    // Increment Quantity
    suspend fun incrementQuantity(productId: String) =
        withContext(Dispatchers.IO) {
            // Optimistic update: Find current qty from local list first
            _cartItems.update { list ->
                list.map { if (it.product.id == productId) it.copy(quantity = it.quantity + 1) else it }
            }
            val newQty = _cartItems.value.find { it.product.id == productId }?.quantity
                ?: return@withContext

            try {
                cartDataSource.updateQuantity(productId, newQty)
            } catch (e: Exception) {
                Log.e("CartRepo", "Failed to increment", e)
                fetchCartItems()
            }
    }

    // Decrement Quantity (Handles removal if qty becomes 0)
    suspend fun decrementQuantity(productId: String) = withContext(Dispatchers.IO) {
        val currentItem = _cartItems.value.find { it.product.id == productId } ?: return@withContext

        if (currentItem.quantity > 1) {
            // Case A: Just lower the number
            _cartItems.update { list ->
                list.map { if (it.product.id == productId) it.copy(quantity = it.quantity - 1) else it }
            }
            try {
                cartDataSource.updateQuantity(productId, currentItem.quantity - 1)
            } catch (e: Exception) {
                Log.e("CartRepo", "Failed to decrement", e)
                fetchCartItems()
            }
        } else {
            // Case B: Quantity is 1, so remove the item entirely
            removeFromCart(productId)
        }
    }

    suspend fun createOrder(
        paymentId: String,
        amount: Double,
        address: String,
        cartItems: List<CartItem>
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val userId = currentUser.userId() ?: return@withContext false
            Log.d("CartRepo", "Step 0: Starting Order for User: $userId")

            val storeId = cartItems.firstOrNull()?.product?.storeId ?: return@withContext false

            // 1. Insert Parent Order, get the new ID back
            val newOrderId = orderDataSource.insertOrder(
                OrderRequest(
                    userId = userId,
                    paymentId = paymentId,
                    totalAmount = amount,
                    address = address,
                    storeId = storeId
                )
            )
            Log.d("CartRepo", "Step 1 Success: Created Order #$newOrderId")

            // 2. Prepare Child Items
            val orderItemsList = cartItems.map { item ->
                OrderItemRequest(
                    orderId = newOrderId,
                    productId = item.product.id,
                    productName = item.product.name,
                    quantity = item.quantity,
                    price = item.product.priceRetail,
                    storeId = storeId
                )
            }

            // 3. Insert Child Items
            try {
                orderDataSource.insertOrderItems(orderItemsList)
                Log.d("CartRepo", "Step 3 Success: All items inserted!")
                true
            } catch (insertError: Exception) {
                // THIS IS THE CRITICAL LOG
                Log.e("CartRepo", "Step 3 FAILED! DB Rejected items. Cause: ${insertError.message}")

                // Optional: Delete the empty order so we don't have ghosts
                // postgrest["orders"].delete { filter { eq("id", newOrderId) } }
                false
            }
        } catch (e: Exception) {
            Log.e("CartRepo", "Failed to create order", e)
            false
        }
    }

    suspend fun clearCart() = withContext(Dispatchers.IO) {
        try {
            cartDataSource.deleteAll()
            fetchCartItems()
        } catch (e: Exception) {
            Log.e("CartRepo", "Failed to clear cart", e)
        }
    }
}


//// --- CRITICAL CHANGE: SPLIT ORDER LOGIC ---
//suspend fun createOrderBySplitLogic(
//    paymentId: String,
//    address: String,
//    cartItems: List<CartItem>
//): Boolean = withContext(Dispatchers.IO) {
//    val userId = auth.currentUserOrNull()?.id ?: return@withContext false
//
//    // 1. Group items by Store ID
//    // (e.g. { "store_cafe_123": [Coffee, Cake], "store_med_456": [Pills] })
//    val itemsByStore = cartItems.groupBy { it.product.storeId }
//
//    var allOrdersSuccess = true
//
//    try {
//        // 2. Loop through each store and create a separate order
//        itemsByStore.forEach { (storeId, items) ->
//
//            // Calculate total for THIS store only
//            val storeTotalAmount = items.sumOf { it.product.priceRetail * it.quantity }
//
//            Log.d("CartRepo", "Creating Order for Store: $storeId | Amount: $storeTotalAmount")
//
//            // A. Insert Parent Order (Now includes storeId!)
//            val orderRequest = OrderRequest(
//                userId = userId,
//                storeId = storeId, // <--- IMPORTANT: Link order to the store
//                paymentId = paymentId,
//                totalAmount = storeTotalAmount,
//                address = address
//            )
//
//            val orderResponse = postgrest["orders"]
//                .insert(orderRequest) { select() }
//                .decodeSingle<OrderResponse>()
//
//            val newOrderId = orderResponse.id
//
//            // B. Prepare Child Items
//            val orderItemsList = items.map { item ->
//                OrderItemRequest(
//                    orderId = newOrderId,
//                    productId = item.product.id,
//                    productName = item.product.name,
//                    quantity = item.quantity,
//                    price = item.product.priceRetail,
//                    storeId = storeId
//                )
//            }
//
//            // C. Insert Child Items
//            postgrest["order_items"].insert(orderItemsList)
//        }
//
//        // If loop finishes without crashing, we are good
//        true
//
//    } catch (e: Exception) {
//        Log.e("CartRepo", "Failed to create split orders", e)
//        // Ideally, you'd implement a rollback here, but for now returning false lets UI show error
//        false
//    }
//}