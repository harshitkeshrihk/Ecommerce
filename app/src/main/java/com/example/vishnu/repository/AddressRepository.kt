package com.example.vishnu.repository

import android.util.Log
import com.example.vishnu.model.UserAddress
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.postgrest.Postgrest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import io.github.jan.supabase.postgrest.query.Order as SupabaseOrder

@Singleton
class AddressRepository @Inject constructor(
    private val postgrest: Postgrest,
    private val auth: Auth
){

    suspend fun getUserAddresses(): List<UserAddress> = withContext(Dispatchers.IO) {
        try {
            val userId = auth.currentUserOrNull()?.id ?: return@withContext emptyList()
            postgrest["user_addresses"]
                .select {
                    filter { eq("user_id", userId) }
                    order("is_default", order = SupabaseOrder.DESCENDING) // Default on top
                }
                .decodeList<UserAddress>()
        } catch (e: Exception) {
            Log.e("AddressRepo", "Fetch failed", e)
            emptyList()
        }
    }

    suspend fun addAddress(address: UserAddress): Boolean = withContext(Dispatchers.IO){
        try {
            val userId = auth.currentUserOrNull()?.id ?: return@withContext false
            val newAdd = address.copy(userId = userId)

            val existing = getUserAddresses()
            val finalAdd = if(existing.isEmpty()) newAdd.copy(isDefault = true) else newAdd

            postgrest["user_addresses"].insert(finalAdd)
            true
        }catch (e: Exception){
            Log.e("AddressRepo", "Add failed", e)
            false
        }
    }

    suspend fun deleteAddress(addressId: String) = withContext(Dispatchers.IO){
        try {
            postgrest["user_addresses"].delete {
                filter { eq("id",addressId) }
            }
        }catch (e: Exception){
            Log.e("AddressRepo", "Delete failed", e)
        }
    }

    suspend fun setDefaultAddress(addressId: String) = withContext(Dispatchers.IO){
        try {
            val userId = auth.currentUserOrNull()?.id ?: return@withContext false

            //Unset All
            postgrest["user_addresses"].update({
                set("is_default", false)
            }){
                filter { eq("user_id", userId) }
            }

            postgrest["user_addresses"].update({
                set("is_default",true)
            }){
                filter { eq("id", addressId) }
            }

        }catch (e: Exception){
            Log.e("AddressRepo", "Set Default failed", e)
        }
    }

}