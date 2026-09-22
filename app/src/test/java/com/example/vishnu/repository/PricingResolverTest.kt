package com.example.vishnu.repository

import com.example.vishnu.model.MoqSlab
import com.example.vishnu.model.PriceSource
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Covers Doc 4 §02's acceptance criterion: "MOQ-slab pricing is correct at
 * every quantity breakpoint." This is the pure slab-selection half of
 * resolvePricing (see PricingRepository.kt) — the half that doesn't need a
 * live Supabase project to verify.
 */
class PricingResolverTest {

    private val slabs = listOf(
        MoqSlab(id = "1", productId = "p1", minQty = 10, pricePerUnit = 90.0),
        MoqSlab(id = "2", productId = "p1", minQty = 50, pricePerUnit = 80.0),
        MoqSlab(id = "3", productId = "p1", minQty = 200, pricePerUnit = 65.0)
    )

    @Test
    fun `below the lowest breakpoint falls back to the wholesale base price`() {
        val result = resolveWholesalePrice(wholesaleBasePrice = 100.0, slabs = slabs, qty = 5)
        assertEquals(100.0, result.unitPrice, 0.0)
        assertEquals(PriceSource.WHOLESALE_BASE, result.source)
    }

    @Test
    fun `exactly at a breakpoint uses that slab, not the one below it`() {
        val result = resolveWholesalePrice(wholesaleBasePrice = 100.0, slabs = slabs, qty = 10)
        assertEquals(90.0, result.unitPrice, 0.0)
        assertEquals(PriceSource.MOQ_SLAB, result.source)
    }

    @Test
    fun `between two breakpoints uses the lower (already-cleared) one`() {
        val result = resolveWholesalePrice(wholesaleBasePrice = 100.0, slabs = slabs, qty = 49)
        assertEquals(90.0, result.unitPrice, 0.0)
    }

    @Test
    fun `at the highest breakpoint uses the best price`() {
        val result = resolveWholesalePrice(wholesaleBasePrice = 100.0, slabs = slabs, qty = 500)
        assertEquals(65.0, result.unitPrice, 0.0)
        assertEquals(PriceSource.MOQ_SLAB, result.source)
    }

    @Test
    fun `no slabs configured for the product always falls back to base price`() {
        val result = resolveWholesalePrice(wholesaleBasePrice = 100.0, slabs = emptyList(), qty = 1000)
        assertEquals(100.0, result.unitPrice, 0.0)
        assertEquals(PriceSource.WHOLESALE_BASE, result.source)
    }
}
