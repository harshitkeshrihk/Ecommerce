package com.example.vishnu.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.LocalDate

/** Tag on products.category_tags that puts a SKU in the gifting collection. */
const val GIFTING_TAG = "gifting"

enum class OccasionType(val dbValue: String, val label: String) {
    WEDDING("wedding", "Wedding"),
    CORPORATE("corporate", "Corporate"),
    EVENT("event", "Event")
}

// --- Admin-defined packs (gift_packs + gift_pack_items) ---

@Serializable
data class GiftPack(
    val id: String,
    val name: String,
    val description: String? = null,
    @SerialName("image_url") val imageUrl: String? = null,
    @SerialName("is_active") val isActive: Boolean = true,
    @SerialName("gift_pack_items") val items: List<GiftPackItem> = emptyList()
) {
    fun toDraft(): GiftPackDraft = GiftPackDraft(
        name = name,
        sourcePackId = id,
        lines = items.map { GiftPackLine(it.product, it.qty) }
    )
}

@Serializable
data class GiftPackItem(
    val id: String,
    val qty: Int,
    val product: Product
)

// isActive has no default on purpose — SupabaseModule's Json drops fields equal
// to their default, so a defaulted `true` would never reach an update and a
// pack could not be re-activated. Same reasoning as KycSubmissionRequest.
@Serializable
data class GiftPackRequest(
    val name: String,
    val description: String?,
    @SerialName("is_active") val isActive: Boolean
)

@Serializable
data class GiftPackInsertResponse(val id: String)

@Serializable
data class GiftPackItemRequest(
    @SerialName("pack_id") val packId: String,
    @SerialName("product_id") val productId: String,
    val qty: Int
)

// --- The pack a customer is ordering (admin pack as-is, edited, or built from scratch) ---

data class GiftPackLine(
    val product: Product,
    val qtyPerPack: Int
)

data class GiftPackDraft(
    val name: String,
    val sourcePackId: String?, // null = built from scratch
    val lines: List<GiftPackLine>
) {
    /** Price of ONE pack: every item at its retail price. Packaging is itself a product line. */
    val pricePerPack: Double get() = lines.sumOf { it.product.priceRetail * it.qtyPerPack }

    fun totalFor(packCount: Int): Double = pricePerPack * packCount

    /** Total units of each product needed to assemble [packCount] packs — what goes into order_items. */
    fun expandedQuantities(packCount: Int): List<Pair<Product, Int>> =
        lines.map { it.product to it.qtyPerPack * packCount }
}

// --- Budget tiers (price per pack) ---

data class BudgetTier(val label: String, val minPerPack: Double, val maxPerPack: Double?) {
    fun matches(pricePerPack: Double): Boolean =
        pricePerPack >= minPerPack && (maxPerPack == null || pricePerPack < maxPerPack)
}

object GiftingRules {
    /** Smallest order the bulk-gifting flow accepts. */
    const val MIN_PACK_COUNT = 10

    /** Engraving/printing length limit for personalization text. */
    const val MAX_PERSONALIZATION_LENGTH = 40

    val BUDGET_TIERS = listOf(
        BudgetTier("Under ₹300", 0.0, 300.0),
        BudgetTier("₹300 – ₹700", 300.0, 700.0),
        BudgetTier("₹700 +", 700.0, null)
    )

    /** Returns the first problem that blocks checkout, or null if the order can be paid for. */
    fun validate(
        draft: GiftPackDraft,
        packCount: Int?,
        personalization: String,
        shipByDate: LocalDate?,
        address: String,
        today: LocalDate
    ): String? = when {
        draft.lines.isEmpty() -> "Add at least one item to the pack"
        draft.lines.any { it.qtyPerPack <= 0 } -> "Every item needs a quantity of at least 1"
        packCount == null || packCount < MIN_PACK_COUNT -> "Minimum order is $MIN_PACK_COUNT packs"
        personalization.length > MAX_PERSONALIZATION_LENGTH ->
            "Personalization can be at most $MAX_PERSONALIZATION_LENGTH characters"
        shipByDate == null -> "Choose a ship-by date"
        !shipByDate.isAfter(today) -> "Ship-by date must be in the future"
        address.isBlank() -> "Enter a delivery address"
        else -> null
    }
}

// --- gifting_orders ---

@Serializable
data class PackContentLine(
    @SerialName("product_id") val productId: String,
    @SerialName("product_name") val productName: String,
    @SerialName("qty_per_pack") val qtyPerPack: Int,
    @SerialName("unit_price") val unitPrice: Double
)

@Serializable
data class GiftingOrderRequest(
    @SerialName("order_id") val orderId: Long,
    @SerialName("occasion_type") val occasionType: String,
    @SerialName("source_pack_id") val sourcePackId: String?,
    @SerialName("pack_name") val packName: String,
    @SerialName("pack_count") val packCount: Int,
    @SerialName("pack_contents") val packContents: List<PackContentLine>,
    @SerialName("personalization_text") val personalizationText: String?,
    @SerialName("ship_by_date") val shipByDate: String // ISO yyyy-MM-dd
)

/** Admin view: a gifting order joined with its parent order row. */
@Serializable
data class GiftingOrder(
    @SerialName("order_id") val orderId: Long,
    @SerialName("occasion_type") val occasionType: String,
    @SerialName("pack_name") val packName: String,
    @SerialName("pack_count") val packCount: Int,
    @SerialName("pack_contents") val packContents: List<PackContentLine>,
    @SerialName("personalization_text") val personalizationText: String? = null,
    @SerialName("ship_by_date") val shipByDate: String,
    val order: Order
)
