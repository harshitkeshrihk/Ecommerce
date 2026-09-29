package com.example.vishnu.repository

import android.util.Log
import com.example.vishnu.model.GiftPackDraft
import com.example.vishnu.model.GiftingOrderRequest
import com.example.vishnu.model.GiftingPaymentRequest
import com.example.vishnu.model.GiftingRules
import com.example.vishnu.model.OccasionType
import com.example.vishnu.model.OrderItemRequest
import com.example.vishnu.model.OrderRequest
import com.example.vishnu.model.PackContentLine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

sealed class GiftingOrderResult {
    data class Placed(val orderId: Long) : GiftingOrderResult()
    data class Failed(val message: String) : GiftingOrderResult()
}

/**
 * Creates a gifting order after Razorpay has taken the first payment (same
 * pay-then-record order as retail checkout). Writes four things:
 * the parent `orders` row (channel = gifting; ADVANCE_PAID or PAID), `order_items`
 * with the expanded quantities (qty per pack x pack count), the `gifting_orders`
 * row holding the per-pack composition, personalization, ship-by and balance
 * due dates, and the first `gifting_payments` row (advance or full).
 */
@Singleton
class GiftingOrderRepository @Inject constructor(
    private val orderDataSource: OrderDataSource,
    private val giftingOrderDataSource: GiftingOrderDataSource,
    private val currentUser: CurrentUserProvider
) {
    suspend fun placeGiftingOrder(
        draft: GiftPackDraft,
        occasion: OccasionType,
        packCount: Int,
        personalization: String,
        shipByDate: LocalDate,
        address: String,
        paymentId: String,
        amountPaid: Double,
        logoPath: String? = null,
        logoNotes: String? = null,
        budgetPerPerson: Double? = null
    ): GiftingOrderResult = withContext(Dispatchers.IO) {
        if (occasion == OccasionType.CORPORATE && logoPath == null) {
            return@withContext GiftingOrderResult.Failed("Corporate orders need a logo")
        }
        val userId = currentUser.userId()
            ?: return@withContext GiftingOrderResult.Failed("Not signed in")
        val storeId = draft.lines.firstOrNull()?.product?.storeId
            ?: return@withContext GiftingOrderResult.Failed("The pack is empty")

        val total = draft.totalFor(packCount)
        val paidInFull = GiftingRules.isFullyPaid(amountPaid, total)

        try {
            val orderId = orderDataSource.insertOrder(
                OrderRequest(
                    userId = userId,
                    paymentId = paymentId,
                    totalAmount = total,
                    address = address,
                    storeId = storeId,
                    status = if (paidInFull) "PAID" else "ADVANCE_PAID",
                    channel = "gifting"
                )
            )

            orderDataSource.insertOrderItems(
                draft.expandedQuantities(packCount).map { (product, qty) ->
                    OrderItemRequest(
                        orderId = orderId,
                        productId = product.id,
                        productName = product.name,
                        quantity = qty,
                        price = product.priceRetail,
                        storeId = storeId
                    )
                }
            )

            giftingOrderDataSource.insertGiftingOrder(
                GiftingOrderRequest(
                    orderId = orderId,
                    occasionType = occasion.dbValue,
                    sourcePackId = draft.sourcePackId,
                    packName = draft.name,
                    packCount = packCount,
                    packContents = draft.lines.map {
                        PackContentLine(it.product.id, it.product.name, it.qtyPerPack, it.product.priceRetail)
                    },
                    personalizationText = personalization.trim().ifBlank { null },
                    shipByDate = shipByDate.toString(),
                    balanceDueDate = GiftingRules.balanceDueDate(shipByDate).toString(),
                    logoPath = logoPath,
                    logoNotes = logoNotes?.trim()?.ifBlank { null },
                    budgetPerPerson = budgetPerPerson.takeIf { occasion == OccasionType.CORPORATE }
                )
            )

            giftingOrderDataSource.insertPayment(
                GiftingPaymentRequest(
                    orderId = orderId,
                    razorpayPaymentId = paymentId,
                    amount = amountPaid,
                    kind = if (paidInFull) "full" else "advance"
                )
            )
            GiftingOrderResult.Placed(orderId)
        } catch (e: Exception) {
            Log.e("GiftingOrderRepo", "Failed to record gifting order for payment $paymentId", e)
            GiftingOrderResult.Failed(
                "Payment received (ID $paymentId) but the order could not be saved. " +
                    "Please contact us with this payment ID."
            )
        }
    }

    /**
     * Records the remaining balance after Razorpay has taken it. The server
     * (pay_gifting_balance) checks the order is the caller's and that the
     * amount matches what's left, then marks the order PAID.
     */
    suspend fun payBalance(orderId: Long, paymentId: String, amount: Double): GiftingOrderResult =
        withContext(Dispatchers.IO) {
            try {
                giftingOrderDataSource.payBalance(orderId, paymentId, amount)
                GiftingOrderResult.Placed(orderId)
            } catch (e: Exception) {
                Log.e("GiftingOrderRepo", "Failed to record balance payment $paymentId for order $orderId", e)
                GiftingOrderResult.Failed(
                    "Payment received (ID $paymentId) but it could not be recorded against order #$orderId. " +
                        "Please contact us with this payment ID."
                )
            }
        }
}
