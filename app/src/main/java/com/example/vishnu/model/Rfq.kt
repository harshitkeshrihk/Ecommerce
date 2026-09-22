package com.example.vishnu.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// --- Submission (client -> server) ---

@Serializable
data class RfqRequest(
    @SerialName("user_id") val userId: String,
    @SerialName("needed_by_date") val neededByDate: String? = null,
    val notes: String? = null,
    val status: String = "open"
)

@Serializable
data class RfqResponse(val id: String)

@Serializable
data class RfqItemRequest(
    @SerialName("rfq_id") val rfqId: String,
    @SerialName("product_id") val productId: String,
    val qty: Int
)

// A line the customer builds up in the RFQ submission form before it's sent.
data class RfqDraftLine(
    val product: Product,
    val qty: Int
)

// --- Reading back (server -> client) ---

@Serializable
data class RfqItemDetail(
    val id: String,
    val qty: Int,
    val product: Product
)

@Serializable
data class Rfq(
    val id: String,
    @SerialName("user_id") val userId: String,
    val status: String, // open | quoted | negotiating | won | lost
    @SerialName("needed_by_date") val neededByDate: String? = null,
    val notes: String? = null,
    @SerialName("created_at") val createdAt: String,
    @SerialName("rfq_items") val items: List<RfqItemDetail> = emptyList(),
    val quotes: List<Quote> = emptyList()
) {
    val latestQuote: Quote? get() = quotes.maxByOrNull { it.version }
}

// A quote is a "round" (version + terms) — price is per rfq_item, not one
// value for the whole RFQ, since an RFQ can span multiple distinct products
// at different quantities that obviously don't share a single unit price.
@Serializable
data class Quote(
    val id: String,
    @SerialName("rfq_id") val rfqId: String,
    val version: Int,
    val terms: String? = null,
    @SerialName("created_at") val createdAt: String,
    @SerialName("quote_items") val items: List<QuoteItemDetail> = emptyList()
) {
    fun priceFor(rfqItemId: String): Double? = items.find { it.rfqItemId == rfqItemId }?.pricePerUnit
}

@Serializable
data class QuoteItemDetail(
    val id: String,
    @SerialName("rfq_item_id") val rfqItemId: String,
    @SerialName("price_per_unit") val pricePerUnit: Double
)

@Serializable
data class QuoteRequest(
    @SerialName("rfq_id") val rfqId: String,
    val version: Int,
    val terms: String? = null,
    @SerialName("created_by") val createdBy: String
)

@Serializable
data class QuoteResponse(val id: String)

@Serializable
data class QuoteItemRequest(
    @SerialName("quote_id") val quoteId: String,
    @SerialName("rfq_item_id") val rfqItemId: String,
    @SerialName("price_per_unit") val pricePerUnit: Double
)
