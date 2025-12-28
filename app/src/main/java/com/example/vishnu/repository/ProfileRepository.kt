package com.example.vishnu.repository

import android.util.Log
import com.example.vishnu.model.Order
import com.example.vishnu.model.OrderItemDetail
import com.example.vishnu.model.ProfileUpdateRequest
import com.example.vishnu.model.UserProfile
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import io.github.jan.supabase.postgrest.query.Order as SupabaseOrder

@Singleton
class ProfileRepository @Inject constructor(
    private val auth: Auth,
    private val postgrest: Postgrest
) {
    // Fetch Profile
    suspend fun getUserProfile(): UserProfile? = withContext(Dispatchers.IO) {
        val userId = auth.currentUserOrNull()?.id ?: return@withContext null
        try {
            postgrest["profiles"]
                .select {
                    filter { eq("id", userId) }
                }
                .decodeSingleOrNull<UserProfile>()
        } catch (e: Exception) {
            Log.e("ProfileRepo", "Error fetching profile", e)
            null
        }
    }

    // Update Profile
    suspend fun updateProfile(name: String, phone: String, address: String): Boolean = withContext(Dispatchers.IO) {
        val userId = auth.currentUserOrNull()?.id ?: return@withContext false
        try {
            val updateData = ProfileUpdateRequest(
                fullName = name,
                phoneNumber = phone,
                address = address,
                updatedAt = Instant.now().toString()
            )

            postgrest["profiles"].update(updateData) {
                filter { eq("id", userId) }
            }
            true // Success
        } catch (e: Exception) {
            Log.e("ProfileRepo", "Error updating profile", e)
            false
        }
    }

    // 1. Fetch Active Orders (Processing, Shipped, etc.)
    suspend fun getActiveOrders(): List<Order> = withContext(Dispatchers.IO) {
        try {
            val userId = auth.currentUserOrNull()?.id ?: return@withContext emptyList()
            postgrest["orders"]
                .select {
                    filter { eq("user_id", userId) }
                    // Filter for statuses that are "Active"
                    filter { isIn("status", listOf("PAID", "PROCESSING", "SHIPPED")) }
                    order("created_at", order = SupabaseOrder.DESCENDING)
                }
                .decodeList<Order>()
        } catch (e: Exception) {
            emptyList()
        }
    }

    // 2. Fetch Past Orders (Delivered, Cancelled)
    suspend fun getPastOrders(): List<Order> = withContext(Dispatchers.IO) {
        try {
            val userId = auth.currentUserOrNull()?.id ?: return@withContext emptyList()
            postgrest["orders"]
                .select {
                    filter { eq("user_id", userId) }
                    // Filter for statuses that are "Completed"
                    filter { isIn("status", listOf("DELIVERED", "CANCELLED", "REFUNDED")) }
                    order("created_at", order = SupabaseOrder.DESCENDING)
                }
                .decodeList<Order>()
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun getOrderItems(orderId: Long): List<OrderItemDetail> = withContext(Dispatchers.IO) {
        try {
            // Query: SELECT product_name, quantity, price_at_purchase FROM order_items WHERE order_id = X
            postgrest["order_items"]
                .select(columns = Columns.list("product_name", "quantity", "price_at_purchase")) {
                    filter { eq("order_id", orderId) }
                }
                .decodeList<OrderItemDetail>()
        } catch (e: Exception) {
            Log.e("ProfileRepo", "Error fetching items for order #$orderId", e)
            emptyList()
        }
    }

//    suspend fun getAllOrdersForAdmin(): List<Order> = withContext(Dispatchers.IO) {
//        try {
//            postgrest["orders"]
//                .select {
//                    order("created_at", order = SupabaseOrder.DESCENDING)
//                }
//                .decodeList<Order>()
//        } catch (e: Exception) {
//            emptyList()
//        }
//    }
//
//    suspend fun updateOrderStatus(orderId: Long, newStatus: String): Boolean = withContext(Dispatchers.IO) {
//        try {
//            postgrest["orders"]
//                .update({
//                    set("status", newStatus)
//                }) {
//                    filter { eq("id", orderId) }
//                }
//            true
//        } catch (e: Exception) {
//            false
//        }
//    }


}