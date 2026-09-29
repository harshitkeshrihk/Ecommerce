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

    /** Largest logo / proof image accepted (matches the brand-assets bucket limit). */
    const val MAX_BRAND_IMAGE_BYTES = 5 * 1024 * 1024

    /** Smallest share of the order total the customer can pay at checkout. */
    const val MIN_ADVANCE_PERCENT = 40

    /** The remaining balance is due this many days before the ship-by date. */
    const val BALANCE_DUE_DAYS_BEFORE_SHIP = 3L

    /** 40% of the total, rounded UP to the whole rupee (never more than the total). */
    fun minAdvance(total: Double): Double =
        minOf(kotlin.math.ceil(total * MIN_ADVANCE_PERCENT / 100.0), total)

    fun balanceDueDate(shipBy: LocalDate): LocalDate = shipBy.minusDays(BALANCE_DUE_DAYS_BEFORE_SHIP)

    /** When the balance would already be due, there's no time for a second payment. */
    fun requiresFullPayment(shipBy: LocalDate, today: LocalDate): Boolean =
        !balanceDueDate(shipBy).isAfter(today)

    fun isFullyPaid(paid: Double, total: Double): Boolean = paid >= total - 0.005

    /** Returns the problem with the amount the customer wants to pay now, or null if it's acceptable. */
    fun validatePayNow(amount: Double?, total: Double, shipBy: LocalDate, today: LocalDate): String? = when {
        amount == null || amount <= 0.0 -> "Enter the amount to pay now"
        amount > total + 0.005 -> "You can't pay more than the order total"
        requiresFullPayment(shipBy, today) && !isFullyPaid(amount, total) ->
            "The ship-by date is less than ${BALANCE_DUE_DAYS_BEFORE_SHIP + 1} days away, so the full amount is due now"
        amount < minAdvance(total) - 0.005 ->
            "Minimum advance is $MIN_ADVANCE_PERCENT% of the total"
        else -> null
    }

    /**
     * Automatic corporate proposal: sellable packs whose price per pack fits the
     * budget per person, closest to the budget first (best use of the budget).
     */
    fun proposePacks(packs: List<GiftPack>, budgetPerPerson: Double): List<GiftPack> =
        packs
            .filter { it.items.isNotEmpty() && it.toDraft().pricePerPack <= budgetPerPerson + 0.005 }
            .sortedByDescending { it.toDraft().pricePerPack }

    /** Validates the budget + headcount form; returns an error message or null. */
    fun validateCorporateBrief(budgetPerPerson: Double?, headcount: Int?): String? = when {
        budgetPerPerson == null || budgetPerPerson <= 0.0 -> "Enter a budget per person"
        headcount == null || headcount < MIN_PACK_COUNT -> "Minimum order is $MIN_PACK_COUNT people"
        else -> null
    }

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
    @SerialName("ship_by_date") val shipByDate: String, // ISO yyyy-MM-dd
    @SerialName("balance_due_date") val balanceDueDate: String, // ISO yyyy-MM-dd
    @SerialName("logo_path") val logoPath: String?, // corporate only
    @SerialName("logo_notes") val logoNotes: String?,
    @SerialName("budget_per_person") val budgetPerPerson: Double? // corporate, when ordered from a budget
)

@Serializable
data class GiftingPaymentRequest(
    @SerialName("order_id") val orderId: Long,
    @SerialName("razorpay_payment_id") val razorpayPaymentId: String,
    val amount: Double,
    val kind: String // advance | full (balance goes through pay_gifting_balance)
)

@Serializable
data class GiftingPayment(
    @SerialName("razorpay_payment_id") val razorpayPaymentId: String,
    val amount: Double,
    val kind: String,
    @SerialName("created_at") val createdAt: String? = null
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
    @SerialName("balance_due_date") val balanceDueDate: String? = null,
    val order: Order,
    @SerialName("gifting_payments") val payments: List<GiftingPayment> = emptyList(),
    @SerialName("logo_path") val logoPath: String? = null,
    @SerialName("logo_notes") val logoNotes: String? = null,
    @SerialName("brand_assets") val proofs: List<BrandAsset> = emptyList()
) {
    val isCorporate: Boolean get() = occasionType == OccasionType.CORPORATE.dbValue

    /** Newest proof version, if the team has uploaded any. */
    val latestProof: BrandAsset? get() = proofs.maxByOrNull { it.version }

    val proofStatus: ProofStatus
        get() = when {
            !isCorporate -> ProofStatus.NOT_REQUIRED
            proofs.any { it.status == BrandAsset.APPROVED } -> ProofStatus.APPROVED
            latestProof == null -> ProofStatus.AWAITING_PROOF
            latestProof!!.status == BrandAsset.REVISION_REQUESTED -> ProofStatus.REVISION_REQUESTED
            else -> ProofStatus.IN_REVIEW
        }

    /** Corporate orders can't enter production until a proof is approved (enforced in the DB too). */
    val canEnterProduction: Boolean get() = proofStatus == ProofStatus.NOT_REQUIRED || proofStatus == ProofStatus.APPROVED

    val amountPaid: Double get() = payments.sumOf { it.amount }
    val balance: Double get() = (order.totalAmount - amountPaid).coerceAtLeast(0.0)
    val isFullyPaid: Boolean get() = GiftingRules.isFullyPaid(amountPaid, order.totalAmount)
}

// --- Corporate branding (brand_assets = proof versions) ---

enum class ProofStatus {
    NOT_REQUIRED,        // wedding / event
    AWAITING_PROOF,      // logo uploaded, team hasn't sent a proof yet
    IN_REVIEW,           // latest proof waiting for the customer
    REVISION_REQUESTED,  // customer asked for changes, team to send a new version
    APPROVED
}

@Serializable
data class BrandAsset(
    val id: String,
    @SerialName("order_id") val orderId: Long,
    val version: Int,
    @SerialName("proof_path") val proofPath: String,
    val status: String,
    @SerialName("admin_note") val adminNote: String? = null,
    @SerialName("customer_comment") val customerComment: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("decided_at") val decidedAt: String? = null
) {
    companion object {
        const val IN_REVIEW = "in_review"
        const val APPROVED = "approved"
        const val REVISION_REQUESTED = "revision_requested"
    }
}

@Serializable
data class BrandAssetRequest(
    @SerialName("order_id") val orderId: Long,
    val version: Int,
    @SerialName("proof_path") val proofPath: String,
    @SerialName("admin_note") val adminNote: String?
)

// --- Corporate custom proposal requests ---

@Serializable
data class CorporateProposalRequest(
    val id: String,
    @SerialName("budget_per_person") val budgetPerPerson: Double,
    val headcount: Int,
    val notes: String,
    val status: String, // open | proposed | closed
    @SerialName("proposed_pack_id") val proposedPackId: String? = null,
    @SerialName("admin_note") val adminNote: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("responded_at") val respondedAt: String? = null
) {
    companion object {
        const val OPEN = "open"
        const val PROPOSED = "proposed"
        const val CLOSED = "closed"
    }
}

@Serializable
data class CorporateProposalRequestInsert(
    @SerialName("budget_per_person") val budgetPerPerson: Double,
    val headcount: Int,
    val notes: String
)

// status has no default on purpose (SupabaseModule's Json drops default-valued fields).
@Serializable
data class CorporateProposalResponse(
    val status: String,
    @SerialName("proposed_pack_id") val proposedPackId: String?,
    @SerialName("admin_note") val adminNote: String?,
    @SerialName("responded_at") val respondedAt: String
)
