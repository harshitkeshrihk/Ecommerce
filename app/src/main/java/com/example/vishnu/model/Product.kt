package com.example.vishnu.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Place this at the bottom of the file or in a separate Data file
@Serializable
data class Product(
    val id: String = "",
    val name: String,         // e.g., "Laser Etched Steel Thali Set"
    val material: String,     // e.g., "Stainless Steel 304 Grade"
    val gauge: String,        // e.g., "22 Gauge"
    val weight: String,       // e.g., "450g" - Added to UI!
    val category: String,       // e.g., "450g" - Added to UI!
    val subcategory: String,       // e.g., "450g" - Added to UI!
    @SerialName("price_retail") // Maps DB 'price_retail' -> Kotlin 'priceRetail'
    val priceRetail: Double,

    @SerialName("price_wholesale")
    val priceWholesale: Double,

    @SerialName("video_url")
    val videoUrl: String? = null,

    @SerialName("image_url")
    val imageUrl: String,

    @SerialName("stock_count")
    val stockCount: Int,

    @SerialName("is_available")
    val isAvailable: Boolean,

    @SerialName("is_bestseller")
    val isBestseller: Boolean = false,

    @SerialName("created_at")
    val createdAt: String? = null

)

