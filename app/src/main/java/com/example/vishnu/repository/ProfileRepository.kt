package com.example.vishnu.repository

import android.util.Log
import com.example.vishnu.model.ProfileUpdateRequest
import com.example.vishnu.model.UserProfile
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.postgrest.Postgrest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

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
}