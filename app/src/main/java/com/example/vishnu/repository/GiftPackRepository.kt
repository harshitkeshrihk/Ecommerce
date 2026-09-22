package com.example.vishnu.repository

import android.util.Log
import com.example.vishnu.model.GIFTING_TAG
import com.example.vishnu.model.GiftPack
import com.example.vishnu.model.GiftPackInsertResponse
import com.example.vishnu.model.GiftPackItemRequest
import com.example.vishnu.model.GiftPackLine
import com.example.vishnu.model.GiftPackRequest
import com.example.vishnu.model.GiftingOrder
import com.example.vishnu.model.Product
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order as SupabaseOrder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** Reads/writes for the gifting collection, admin-defined packs and the admin gifting-order list. */
@Singleton
class GiftPackRepository @Inject constructor(
    private val postgrest: Postgrest
) {
    private val packColumns = Columns.raw("*, gift_pack_items(id, qty, product:products(*))")

    /** Products tagged for gifting (the gifting collection). */
    suspend fun getGiftingProducts(): List<Product> = withContext(Dispatchers.IO) {
        try {
            postgrest["products"]
                .select {
                    filter {
                        eq("is_available", true)
                        contains("category_tags", listOf(GIFTING_TAG))
                    }
                }
                .decodeList<Product>()
        } catch (e: Exception) {
            Log.e("GiftPackRepo", "Error fetching gifting products", e)
            emptyList()
        }
    }

    /** Packs with their items. RLS hides inactive packs from non-admins. */
    suspend fun getPacks(activeOnly: Boolean): List<GiftPack> = withContext(Dispatchers.IO) {
        try {
            postgrest["gift_packs"]
                .select(columns = packColumns) {
                    if (activeOnly) filter { eq("is_active", true) }
                    order("created_at", order = SupabaseOrder.ASCENDING)
                }
                .decodeList<GiftPack>()
        } catch (e: Exception) {
            Log.e("GiftPackRepo", "Error fetching gift packs", e)
            emptyList()
        }
    }

    suspend fun getPack(packId: String): GiftPack? = withContext(Dispatchers.IO) {
        try {
            postgrest["gift_packs"]
                .select(columns = packColumns) { filter { eq("id", packId) } }
                .decodeSingleOrNull<GiftPack>()
        } catch (e: Exception) {
            Log.e("GiftPackRepo", "Error fetching gift pack $packId", e)
            null
        }
    }

    /**
     * Admin: creates or updates a pack and replaces its item list.
     * Returns the pack id, or null on failure.
     */
    suspend fun savePack(
        packId: String?,
        name: String,
        description: String?,
        isActive: Boolean,
        lines: List<GiftPackLine>
    ): String? = withContext(Dispatchers.IO) {
        try {
            val request = GiftPackRequest(name = name, description = description, isActive = isActive)
            val id = if (packId == null) {
                postgrest["gift_packs"]
                    .insert(request) { select() }
                    .decodeSingle<GiftPackInsertResponse>()
                    .id
            } else {
                postgrest["gift_packs"].update(request) { filter { eq("id", packId) } }
                packId
            }

            postgrest["gift_pack_items"].delete { filter { eq("pack_id", id) } }
            if (lines.isNotEmpty()) {
                postgrest["gift_pack_items"].insert(
                    lines.map { GiftPackItemRequest(packId = id, productId = it.product.id, qty = it.qtyPerPack) }
                )
            }
            id
        } catch (e: Exception) {
            Log.e("GiftPackRepo", "Error saving gift pack", e)
            null
        }
    }

    suspend fun setPackActive(packId: String, isActive: Boolean): Boolean = withContext(Dispatchers.IO) {
        try {
            postgrest["gift_packs"].update({ set("is_active", isActive) }) { filter { eq("id", packId) } }
            true
        } catch (e: Exception) {
            Log.e("GiftPackRepo", "Error toggling gift pack $packId", e)
            false
        }
    }

    /** Admin: all gifting orders, soonest ship-by date first. */
    suspend fun getGiftingOrders(): List<GiftingOrder> = withContext(Dispatchers.IO) {
        try {
            postgrest["gifting_orders"]
                .select(columns = Columns.raw("*, order:orders(*)")) {
                    order("ship_by_date", order = SupabaseOrder.ASCENDING)
                }
                .decodeList<GiftingOrder>()
        } catch (e: Exception) {
            Log.e("GiftPackRepo", "Error fetching gifting orders", e)
            emptyList()
        }
    }
}
