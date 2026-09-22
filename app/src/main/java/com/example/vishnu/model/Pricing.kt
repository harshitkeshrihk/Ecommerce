package com.example.vishnu.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PricingTier(
    val id: String,
    val name: String,
    val description: String? = null
)

@Serializable
data class MoqSlab(
    val id: String = "",
    @SerialName("product_id") val productId: String,
    @SerialName("min_qty") val minQty: Int,
    @SerialName("price_per_unit") val pricePerUnit: Double
)

// Insert-only shape (no id) for creating a new slab.
@Serializable
data class MoqSlabRequest(
    @SerialName("product_id") val productId: String,
    @SerialName("min_qty") val minQty: Int,
    @SerialName("price_per_unit") val pricePerUnit: Double
)

enum class PriceSource { MOQ_SLAB, WHOLESALE_BASE, QUOTE, RETAIL }

data class ResolvedPrice(
    val unitPrice: Double,
    val source: PriceSource
)
