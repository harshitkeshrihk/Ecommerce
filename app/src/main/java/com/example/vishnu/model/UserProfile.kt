package com.example.vishnu.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class UserProfile(
    val id: String, // UUID
    @SerialName("full_name") val fullName: String? = "",
    @SerialName("phone_number") val phoneNumber: String? = "",
    val address: String? = ""
)

// Request body for updates (We don't send ID, just data)
@Serializable
data class ProfileUpdateRequest(
    @SerialName("full_name") val fullName: String?,
    @SerialName("phone_number") val phoneNumber: String?,
    val address: String?,
    @SerialName("updated_at") val updatedAt: String // ISO Timestamp
)