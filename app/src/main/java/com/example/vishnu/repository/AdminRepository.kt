package com.example.vishnu.repository

import android.util.Log
import com.example.vishnu.model.KycApprovalRequest
import com.example.vishnu.model.KycRejectionRequest
import com.example.vishnu.model.Order
import com.example.vishnu.model.PricingTier
import com.example.vishnu.model.UserProfile
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.query.Order as SupabaseOrder
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Helper class to map the Store response
@Serializable
data class StoreProfile(
    val id: String,
    val name: String,
    @SerialName("owner_email") val ownerEmail: String
)

@Singleton
class AdminRepository @Inject constructor(
    private val auth: Auth,
    private val postgrest: Postgrest
) {

    // 1. Find out which store the logged-in user owns
    suspend fun getMyStore(): StoreProfile? = withContext(Dispatchers.IO) {
        val email = auth.currentUserOrNull()?.email ?: return@withContext null
        try {
            postgrest["stores"]
                .select {
                    filter { eq("owner_email", email) }
                    limit(1)
                }
                .decodeSingleOrNull<StoreProfile>()
        } catch (e: Exception) {
            Log.e("AdminRepo", "Error fetching store profile", e)
            null
        }
    }

    // 2. Fetch Orders specifically for this store
    suspend fun getOrdersForStore(storeId: String): List<Order> = withContext(Dispatchers.IO) {
        try {
            postgrest["orders"]
                .select {
                    filter { eq("store_id", storeId) }
                    order("created_at", order = SupabaseOrder.DESCENDING)
                }
                .decodeList<Order>()
        } catch (e: Exception) {
            Log.e("AdminRepo", "Error fetching orders", e)
            emptyList()
        }
    }

    // 3. Update Order Status
    suspend fun updateOrderStatus(orderId: Long, newStatus: String): Boolean = withContext(Dispatchers.IO) {
        try {
            postgrest["orders"]
                .update({
                    set("status", newStatus)
                }) {
                    filter { eq("id", orderId) }
                }
            true
        } catch (e: Exception) {
            Log.e("AdminRepo", "Error updating status", e)
            false
        }
    }

    // --- Phase 1: KYC verification queue (wholesale/distributor signups) ---

    suspend fun getPendingKycRequests(): List<UserProfile> = withContext(Dispatchers.IO) {
        try {
            postgrest["profiles"]
                .select {
                    filter { eq("kyc_status", "pending") }
                }
                .decodeList<UserProfile>()
        } catch (e: Exception) {
            Log.e("AdminRepo", "Error fetching pending KYC requests", e)
            emptyList()
        }
    }

    suspend fun getPricingTiers(): List<PricingTier> = withContext(Dispatchers.IO) {
        try {
            postgrest["pricing_tiers"].select().decodeList<PricingTier>()
        } catch (e: Exception) {
            Log.e("AdminRepo", "Error fetching pricing tiers", e)
            emptyList()
        }
    }

    // Approves a pending KYC request: promotes the user to their requested
    // role and assigns a pricing tier. Requires the admin-role exception in
    // prevent_role_self_update (see supabase/003_phase1_wholesale_core.sql
    // §C) — without it, the DB trigger silently reverts the role change.
    suspend fun approveKyc(userId: String, requestedRole: String, tierId: String): Boolean =
        withContext(Dispatchers.IO) {
            try {
                postgrest["profiles"].update(
                    KycApprovalRequest(role = requestedRole, kycStatus = "verified", pricingTierId = tierId)
                ) {
                    filter { eq("id", userId) }
                }
                true
            } catch (e: Exception) {
                Log.e("AdminRepo", "Error approving KYC for $userId", e)
                false
            }
        }

    suspend fun rejectKyc(userId: String, reason: String): Boolean = withContext(Dispatchers.IO) {
        try {
            postgrest["profiles"].update(
                KycRejectionRequest(kycStatus = "rejected", reason = reason)
            ) {
                filter { eq("id", userId) }
            }
            true
        } catch (e: Exception) {
            Log.e("AdminRepo", "Error rejecting KYC for $userId", e)
            false
        }
    }

    // --- Phase 1: admin quote pipeline (RFQ -> quote) ---

    suspend fun getOpenRfqs(): List<com.example.vishnu.model.Rfq> = withContext(Dispatchers.IO) {
        try {
            postgrest["rfqs"]
                .select(
                    columns = io.github.jan.supabase.postgrest.query.Columns.raw(
                        "*, rfq_items(id, qty, product:products(*)), quotes(*, quote_items(*))"
                    )
                ) {
                    filter { isIn("status", listOf("open", "quoted", "negotiating")) }
                    order("created_at", order = SupabaseOrder.DESCENDING)
                }
                .decodeList<com.example.vishnu.model.Rfq>()
        } catch (e: Exception) {
            Log.e("AdminRepo", "Error fetching open RFQs", e)
            emptyList()
        }
    }

    // pricesByRfqItemId: one price per RFQ line (rfq_item.id -> price/unit) —
    // a single price for the whole RFQ doesn't make sense once it spans more
    // than one distinct product (see Doc 4 session notes / AdminQuotePipelineScreen).
    suspend fun respondToQuote(
        rfqId: String,
        pricesByRfqItemId: Map<String, Double>,
        terms: String?,
        adminUserId: String,
        nextVersion: Int
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val quoteId = postgrest["quotes"]
                .insert(
                    com.example.vishnu.model.QuoteRequest(
                        rfqId = rfqId,
                        version = nextVersion,
                        terms = terms,
                        createdBy = adminUserId
                    )
                ) { select() }
                .decodeSingle<com.example.vishnu.model.QuoteResponse>()
                .id

            val quoteItems = pricesByRfqItemId.map { (rfqItemId, price) ->
                com.example.vishnu.model.QuoteItemRequest(
                    quoteId = quoteId,
                    rfqItemId = rfqItemId,
                    pricePerUnit = price
                )
            }
            postgrest["quote_items"].insert(quoteItems)

            postgrest["rfqs"].update({ set("status", "quoted") }) {
                filter { eq("id", rfqId) }
            }
            true
        } catch (e: Exception) {
            Log.e("AdminRepo", "Error responding to RFQ $rfqId", e)
            false
        }
    }
}