package com.example.vishnu.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CartResponse(
    val id: Long,
    val quantity: Int,
    val product: Product
)

@Serializable
data class CartRequest(
    @SerialName("product_id")
    val productId: String, // <--- Change Long to String here
    val quantity: Int
)

fun CartResponse.toCartItem(): CartItem {
    return CartItem(
        product = this.product,
        quantity = this.quantity
    )
}