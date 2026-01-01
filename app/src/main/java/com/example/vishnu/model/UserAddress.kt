package com.example.vishnu.model
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class UserAddress(
    val id: String = "",
    @SerialName("user_id") val userId: String = "",
    val label: String,        // "Home", "Office"
    @SerialName("address_text") val addressText: String,
    @SerialName("phone_number") val phoneNumber: String,
    @SerialName("is_default") val isDefault: Boolean = false
)