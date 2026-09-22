package com.example.vishnu.utils

import android.content.Context
import android.content.Intent
import android.net.Uri

data class OrderSummaryLine(val productName: String, val qty: Int, val unitPrice: Double)

// Same business WhatsApp number the existing retail checkout
// (CartViewModel.checkoutOnWhatsApp) sends to — REPLACE THIS with your real
// number later. Shared here so wholesale order summaries (Quick-Order Pad,
// RFQ) open the same chat instead of an unfilled contact picker.
const val BUSINESS_WHATSAPP_NUMBER = "919839633958"

/**
 * Plain, non-tax order summary text for print/WhatsApp — Phase 1 explicitly
 * defers GST invoicing (Doc 4 §00/§02), so this is never labeled or formatted
 * as a tax invoice.
 */
fun buildOrderSummaryText(orderLabel: String, lines: List<OrderSummaryLine>, total: Double): String {
    val itemLines = lines.joinToString("\n") { line ->
        "▪ ${line.productName} (x${line.qty}) - ₹${(line.unitPrice * line.qty).toInt()}"
    }
    return """
        📋 *Order Summary — $orderLabel*
        (Not a tax invoice)

        $itemLines

        ----------------
        💰 *Total: ₹${total.toInt()}*
    """.trimIndent()
}

fun shareOrderSummaryOnWhatsApp(context: Context, summaryText: String, phoneNumber: String? = null) {
    val base = if (phoneNumber != null) {
        "https://api.whatsapp.com/send?phone=$phoneNumber&text=${Uri.encode(summaryText)}"
    } else {
        "https://api.whatsapp.com/send?text=${Uri.encode(summaryText)}"
    }
    try {
        context.startActivity(
            Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse(base)
                setPackage("com.whatsapp")
            }
        )
    } catch (e: Exception) {
        context.startActivity(Intent(Intent.ACTION_VIEW).apply { data = Uri.parse(base) })
    }
}
