package com.example.vishnu.repository

import android.util.Log
import com.example.vishnu.model.OrderItemRequest
import com.example.vishnu.model.OrderRequest
import com.example.vishnu.model.OrderResponse
import com.example.vishnu.model.RfqDraftLine
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.postgrest.Postgrest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Places a wholesale order straight from the Quick-Order Pad. Like RFQ
 * acceptance, this confirms the order immediately with no payment gateway
 * step — Phase 1 keeps wholesale billing offline (Doc 4 §00), so "confirmed"
 * here means "logged and ready for offline settlement against the order
 * summary," not "paid in-app."
 */
@Singleton
class QuickOrderRepository @Inject constructor(
    private val postgrest: Postgrest,
    private val auth: Auth
) {
    suspend fun placeOrder(
        lines: List<RfqDraftLine>,
        resolvedUnitPrices: Map<String, Double>, // productId -> unit price
        shippingAddress: String
    ): Boolean = withContext(Dispatchers.IO) {
        val userId = auth.currentUserOrNull()?.id ?: return@withContext false
        if (lines.isEmpty()) return@withContext false

        try {
            val storeId = lines.first().product.storeId
            val totalAmount = lines.sumOf { line ->
                (resolvedUnitPrices[line.product.id] ?: line.product.priceWholesale) * line.qty
            }

            val orderId = postgrest["orders"]
                .insert(
                    OrderRequest(
                        userId = userId,
                        paymentId = "QUICK-ORDER-${System.currentTimeMillis()}",
                        totalAmount = totalAmount,
                        address = shippingAddress,
                        storeId = storeId,
                        status = "CONFIRMED",
                        channel = "wholesale"
                    )
                ) { select() }
                .decodeSingle<OrderResponse>()
                .id

            val orderItems = lines.map { line ->
                OrderItemRequest(
                    orderId = orderId,
                    productId = line.product.id,
                    productName = line.product.name,
                    quantity = line.qty,
                    price = resolvedUnitPrices[line.product.id] ?: line.product.priceWholesale,
                    storeId = storeId
                )
            }
            postgrest["order_items"].insert(orderItems)
            true
        } catch (e: Exception) {
            Log.e("QuickOrderRepo", "Error placing quick order", e)
            false
        }
    }
}
