package com.example.vishnu.model

data class CartItem(
    val product: Product,
    val quantity: Int
)

/** Retail checkout total — the amount charged via Razorpay and stored on the order. */
fun List<CartItem>.retailTotal(): Double = sumOf { it.product.priceRetail * it.quantity }
