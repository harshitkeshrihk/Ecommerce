package com.example.vishnu.repository

import android.util.Log
import com.example.vishnu.model.OrderItemRequest
import com.example.vishnu.model.OrderRequest
import com.example.vishnu.model.OrderResponse
import com.example.vishnu.model.Quote
import com.example.vishnu.model.Rfq
import com.example.vishnu.model.RfqDraftLine
import com.example.vishnu.model.RfqItemRequest
import com.example.vishnu.model.RfqRequest
import com.example.vishnu.model.RfqResponse
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order as SupabaseOrder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

sealed class RfqSubmitResult {
    object Success : RfqSubmitResult()
    data class Error(val message: String) : RfqSubmitResult()
}

@Singleton
class RfqRepository @Inject constructor(
    private val postgrest: Postgrest,
    private val auth: Auth
) {
    suspend fun submitRfq(
        lines: List<RfqDraftLine>,
        neededByDate: String?,
        notes: String?
    ): RfqSubmitResult = withContext(Dispatchers.IO) {
        val userId = auth.currentUserOrNull()?.id
            ?: return@withContext RfqSubmitResult.Error("Not signed in")
        if (lines.isEmpty()) return@withContext RfqSubmitResult.Error("Add at least one item")

        try {
            val rfqId = postgrest["rfqs"]
                .insert(RfqRequest(userId = userId, neededByDate = neededByDate, notes = notes)) { select() }
                .decodeSingle<RfqResponse>()
                .id

            val itemRequests = lines.map { RfqItemRequest(rfqId = rfqId, productId = it.product.id, qty = it.qty) }
            postgrest["rfq_items"].insert(itemRequests)

            RfqSubmitResult.Success
        } catch (e: Exception) {
            Log.e("RfqRepo", "Error submitting RFQ", e)
            RfqSubmitResult.Error(e.message ?: "Failed to submit request")
        }
    }

    suspend fun getMyRfqs(): List<Rfq> = withContext(Dispatchers.IO) {
        val userId = auth.currentUserOrNull()?.id ?: return@withContext emptyList()
        try {
            postgrest["rfqs"]
                .select(
                    columns = Columns.raw("*, rfq_items(id, qty, product:products(*)), quotes(*, quote_items(*))")
                ) {
                    filter { eq("user_id", userId) }
                    order("created_at", order = SupabaseOrder.DESCENDING)
                }
                .decodeList<Rfq>()
        } catch (e: Exception) {
            Log.e("RfqRepo", "Error fetching my RFQs", e)
            emptyList()
        }
    }

    /**
     * Accepts a quote and converts the RFQ directly into a confirmed order —
     * no cart, no payment step. Wholesale billing stays offline per Doc 4
     * §00/§02: settlement against the printed/WhatsApp order summary happens
     * outside the app, on the terms recorded on the quote.
     */
    suspend fun acceptQuote(rfq: Rfq, quote: Quote, shippingAddress: String): Boolean =
        withContext(Dispatchers.IO) {
            val userId = auth.currentUserOrNull()?.id ?: return@withContext false
            try {
                // Every RFQ line must have a quoted price — respondToQuote
                // always prices every line, but guard anyway rather than
                // silently treating a missing one as free.
                if (rfq.items.any { quote.priceFor(it.id) == null }) return@withContext false

                val totalAmount = rfq.items.sumOf { it.qty * quote.priceFor(it.id)!! }
                val storeId = rfq.items.firstOrNull()?.product?.storeId ?: return@withContext false

                val orderId = postgrest["orders"]
                    .insert(
                        OrderRequest(
                            userId = userId,
                            paymentId = "RFQ-${quote.id}",
                            totalAmount = totalAmount,
                            address = shippingAddress,
                            storeId = storeId,
                            status = "CONFIRMED",
                            channel = "wholesale"
                        )
                    ) { select() }
                    .decodeSingle<OrderResponse>()
                    .id

                val orderItems = rfq.items.map { item ->
                    OrderItemRequest(
                        orderId = orderId,
                        productId = item.product.id,
                        productName = item.product.name,
                        quantity = item.qty,
                        price = quote.priceFor(item.id)!!,
                        storeId = storeId
                    )
                }
                postgrest["order_items"].insert(orderItems)
                postgrest["rfqs"].update({ set("status", "won") }) { filter { eq("id", rfq.id) } }
                true
            } catch (e: Exception) {
                Log.e("RfqRepo", "Error accepting quote for RFQ ${rfq.id}", e)
                false
            }
        }
}
