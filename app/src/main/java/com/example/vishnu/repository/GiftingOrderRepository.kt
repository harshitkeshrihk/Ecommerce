package com.example.vishnu.repository

import android.util.Log
import com.example.vishnu.model.GiftPackDraft
import com.example.vishnu.model.GiftingOrderRequest
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
 * Creates a gifting order after Razorpay has taken payment (same
 * pay-then-record order as retail checkout). Writes three things:
 * the parent `orders` row (channel = gifting), `order_items` with the
 * expanded quantities (qty per pack x pack count), and the `gifting_orders`
 * row holding the per-pack composition, personalization and ship-by date.
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
        paymentId: String
    ): GiftingOrderResult = withContext(Dispatchers.IO) {
        val userId = currentUser.userId()
            ?: return@withContext GiftingOrderResult.Failed("Not signed in")
        val storeId = draft.lines.firstOrNull()?.product?.storeId
            ?: return@withContext GiftingOrderResult.Failed("The pack is empty")

        try {
            val orderId = orderDataSource.insertOrder(
                OrderRequest(
                    userId = userId,
                    paymentId = paymentId,
                    totalAmount = draft.totalFor(packCount),
                    address = address,
                    storeId = storeId,
                    status = "PAID",
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
                    shipByDate = shipByDate.toString()
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
}
