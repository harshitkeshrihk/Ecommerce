package com.example.vishnu.repository

import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.providers.builtin.Email
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import javax.inject.Inject

class AuthRepository @Inject constructor(
    private val auth: Auth
) {
    // Check if user is already logged in
    val sessionStatus = auth.sessionStatus

    val currentUser
        get() = auth.currentUserOrNull()

    suspend fun signUp(email: String, pass: String, name: String) {
        // We pass 'data' which goes into 'raw_user_meta_data'
        // Our SQL trigger will read this 'full_name' and put it in the profiles table.
        val metadata = JsonObject(mapOf("full_name" to JsonPrimitive(name)))

        auth.signUpWith(Email) {
            this.email = email
            this.password = pass
            this.data = metadata
        }
    }

    suspend fun signIn(email: String, pass: String) {
        auth.signInWith(Email) {
            this.email = email
            this.password = pass
        }
    }

    suspend fun signOut() {
        auth.signOut()
    }
}