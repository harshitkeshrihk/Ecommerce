package com.example.vishnu.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Store(
    val id: String,
    val name: String,
    val type: String, // Now guaranteed to be one of your fixed types
    @SerialName("image_url") val imageUrl: String?,
    @SerialName("is_active") val isActive: Boolean,
    @SerialName("owner_email") val ownerEmail: String // Now guaranteed non-null
)