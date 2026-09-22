package com.example.vishnu.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class UserProfile(
    val id: String, // UUID
    @SerialName("full_name") val fullName: String? = "",
    @SerialName("phone_number") val phoneNumber: String? = "",
    val address: String? = "",
    val role: String? = "retail", // "retail" | "wholesale" | "distributor" | "admin" — server-assigned, not user-editable

    // Phase 1 — wholesale/distributor KYC. requestedRole is what the user asked
    // for at signup; `role` only becomes that value once an admin approves it
    // (see AdminRepository.approveKyc). All null for a plain retail signup.
    @SerialName("requested_role") val requestedRole: String? = null, // "wholesale" | "distributor"
    val gstin: String? = null,
    @SerialName("business_name") val businessName: String? = null,
    @SerialName("kyc_status") val kycStatus: String? = null, // pending | verified | rejected
    @SerialName("kyc_rejection_reason") val kycRejectionReason: String? = null,
    @SerialName("pricing_tier_id") val pricingTierId: String? = null
)

// Request body for updates (We don't send ID, just data)
@Serializable
data class ProfileUpdateRequest(
    @SerialName("full_name") val fullName: String?,
    @SerialName("phone_number") val phoneNumber: String?,
    val address: String?,
    @SerialName("updated_at") val updatedAt: String // ISO Timestamp
)

// Sent once, right after a wholesale/distributor signup completes, to attach
// the business details and put the account in the KYC queue.
//
// kycStatus has NO default value on purpose: kotlinx.serialization's Json
// (configured in SupabaseModule.kt without encodeDefaults = true) silently
// drops any field that equals its declared default from the outgoing JSON —
// so a defaulted "pending" here would never actually reach the PATCH body,
// and the column would silently stay whatever it was before. Same reasoning
// applies to KycApprovalRequest/KycRejectionRequest below — do not add a
// default back to any of these kycStatus fields.
@Serializable
data class KycSubmissionRequest(
    @SerialName("requested_role") val requestedRole: String,
    val gstin: String,
    @SerialName("business_name") val businessName: String,
    @SerialName("kyc_status") val kycStatus: String
)

// Admin-only decision on a pending KYC submission.
@Serializable
data class KycApprovalRequest(
    val role: String,
    @SerialName("kyc_status") val kycStatus: String,
    @SerialName("pricing_tier_id") val pricingTierId: String
)

@Serializable
data class KycRejectionRequest(
    @SerialName("kyc_status") val kycStatus: String,
    @SerialName("kyc_rejection_reason") val reason: String
)