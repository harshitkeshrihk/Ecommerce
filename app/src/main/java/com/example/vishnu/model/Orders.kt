package com.example.vishnu.model
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// 1. Send this to create the Master Order
@Serializable
data class OrderRequest(
    @SerialName("user_id") val userId: String,
    @SerialName("payment_id") val paymentId: String,
    @SerialName("total_amount") val totalAmount: Double,
    @SerialName("shipping_address") val address: String,
    @SerialName("store_id") val storeId: String,
    val status: String = "PAID",
    val channel: String = "retail" // retail | wholesale — set by CartRepository.createOrder
)

// 2. Get this back to know the new Order ID (e.g., 105)
@Serializable
data class OrderResponse(
    val id: Long
)

// 3. Send this to add items to that Order
@Serializable
data class OrderItemRequest(
    @SerialName("order_id") val orderId: Long,
    @SerialName("product_id") val productId: String, // UUID
    @SerialName("product_name") val productName: String,
    val quantity: Int,
    @SerialName("price_at_purchase") val price: Double,
    @SerialName("store_id") val storeId: String,
)

@Serializable
data class OrderItemDetail(
    @SerialName("product_name") val productName: String,
    val quantity: Int,
    @SerialName("price_at_purchase") val price: Double
)

@Serializable
data class Order( // <--- This is the class for Active/Past lists
    val id: Long,
    @SerialName("created_at") val createdAt: String, // DB generated timestamp
    @SerialName("total_amount") val totalAmount: Double,
    val status: String,
    @SerialName("payment_id") val paymentId: String,
    @SerialName("shipping_address") val shippingAddress: String,
    val channel: String = "retail"
)