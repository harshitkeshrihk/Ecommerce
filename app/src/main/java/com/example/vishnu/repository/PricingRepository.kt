package com.example.vishnu.repository

import android.util.Log
import com.example.vishnu.model.MoqSlab
import com.example.vishnu.model.MoqSlabRequest
import com.example.vishnu.model.PriceSource
import com.example.vishnu.model.Product
import com.example.vishnu.model.ResolvedPrice
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.query.Order as SupabaseOrder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * resolvePricing, per Doc 2 §1.3 / Doc 4 §02 — implemented client-side against
 * Postgrest (this app has no Supabase Edge Functions deployed), but kept as a
 * single entry point so no screen prices an item off a raw field directly.
 * The contract-pricing/quote branches from the original pseudocode are
 * Phase 2/3 scope and are intentionally not stubbed here.
 */
@Singleton
class PricingRepository @Inject constructor(
    private val postgrest: Postgrest
) {
    suspend fun getSlabsForProduct(productId: String): List<MoqSlab> = withContext(Dispatchers.IO) {
        try {
            postgrest["moq_slabs"]
                .select {
                    filter { eq("product_id", productId) }
                    order("min_qty", order = SupabaseOrder.DESCENDING)
                }
                .decodeList<MoqSlab>()
        } catch (e: Exception) {
            Log.e("PricingRepo", "Error fetching MOQ slabs for $productId", e)
            emptyList()
        }
    }

    suspend fun addSlab(productId: String, minQty: Int, pricePerUnit: Double): Boolean =
        withContext(Dispatchers.IO) {
            try {
                postgrest["moq_slabs"].insert(
                    MoqSlabRequest(productId = productId, minQty = minQty, pricePerUnit = pricePerUnit)
                )
                true
            } catch (e: Exception) {
                Log.e("PricingRepo", "Error adding MOQ slab", e)
                false
            }
        }

    suspend fun deleteSlab(slabId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            postgrest["moq_slabs"].delete { filter { eq("id", slabId) } }
            true
        } catch (e: Exception) {
            Log.e("PricingRepo", "Error deleting MOQ slab", e)
            false
        }
    }

    /**
     * The single source of truth for unit price. Retail buyers always pay
     * price_retail. Wholesale/distributor buyers get the best (highest
     * min_qty they clear) MOQ slab, falling back to price_wholesale if no
     * slab applies — see resolveWholesalePrice for the pure logic.
     */
    suspend fun resolvePricing(product: Product, qty: Int, isWholesaleBuyer: Boolean): ResolvedPrice {
        if (!isWholesaleBuyer) {
            return ResolvedPrice(product.priceRetail, PriceSource.RETAIL)
        }
        val slabs = getSlabsForProduct(product.id)
        return resolveWholesalePrice(product.priceWholesale, slabs, qty)
    }
}

/** Pure, testable slab-selection logic — see Doc 2 §1.3's resolvePricing pseudocode. */
fun resolveWholesalePrice(wholesaleBasePrice: Double, slabs: List<MoqSlab>, qty: Int): ResolvedPrice {
    val slab = slabs
        .filter { it.minQty <= qty }
        .maxByOrNull { it.minQty }
    return if (slab != null) {
        ResolvedPrice(slab.pricePerUnit, PriceSource.MOQ_SLAB)
    } else {
        ResolvedPrice(wholesaleBasePrice, PriceSource.WHOLESALE_BASE)
    }
}
