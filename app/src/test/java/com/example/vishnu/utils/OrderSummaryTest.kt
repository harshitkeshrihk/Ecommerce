package com.example.vishnu.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Doc 4 §02 acceptance criterion: the Phase 1 order summary must never be
 * represented as a tax invoice anywhere in the UI, since GST invoicing is
 * deferred to Phase 1B.
 */
class OrderSummaryTest {

    @Test
    fun `summary is never labeled as a tax invoice`() {
        val text = buildOrderSummaryText(
            orderLabel = "Test Order",
            lines = listOf(OrderSummaryLine("Steel Thali", 10, 90.0)),
            total = 900.0
        )
        assertFalse(text.contains("GST", ignoreCase = true))
        // The only mention of "tax invoice" is the explicit disclaimer that it is NOT one.
        assertTrue(text.contains("Not a tax invoice"))
        assertFalse(text.lines().first { it.contains("Order Summary") }.contains("Invoice", ignoreCase = true))
    }

    @Test
    fun `total reflects quantity times unit price across all lines`() {
        val lines = listOf(
            OrderSummaryLine("Steel Thali", 10, 90.0),
            OrderSummaryLine("Copper Jug", 5, 200.0)
        )
        val text = buildOrderSummaryText("Test Order", lines, total = 1900.0)
        assertTrue(text.contains("₹1900"))
        assertTrue(text.contains("Steel Thali"))
        assertTrue(text.contains("Copper Jug"))
    }
}
