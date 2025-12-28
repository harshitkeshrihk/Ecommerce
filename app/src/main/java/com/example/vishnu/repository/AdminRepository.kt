package com.example.vishnu.repository

import android.util.Log
import com.example.vishnu.model.Order
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
}