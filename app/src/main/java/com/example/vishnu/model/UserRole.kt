package com.example.vishnu.model

import com.example.vishnu.utils.isValidGstin
import com.example.vishnu.utils.normalizeGstin

enum class UserRole {
    RETAIL,
    WHOLESALE,
    DISTRIBUTOR,
    ADMIN;

    companion object {
        fun fromString(value: String?): UserRole =
            entries.find { it.name.equals(value, ignoreCase = true) } ?: RETAIL
    }
}

fun UserProfile?.roleEnum(): UserRole = UserRole.fromString(this?.role)

/**
 * May order at MOQ slab prices with offline billing. Mirrors
 * public.is_business_buyer() in supabase/011_unified_wholesale.sql — the
 * database rejects wholesale orders from anyone else.
 */
fun UserProfile?.isBusinessBuyer(): Boolean =
    roleEnum() in setOf(UserRole.WHOLESALE, UserRole.DISTRIBUTOR, UserRole.ADMIN)

/** GSTIN to pre-fill at wholesale checkout. */
data class GstinPrefill(
    val value: String,
    // The verified KYC GSTIN of an approved business account: shown but not editable.
    val locked: Boolean
)

fun UserProfile?.gstinPrefill(): GstinPrefill {
    val gstin = this?.gstin?.let(::normalizeGstin).orEmpty()
    // A KYC GSTIN saved before format validation existed may be malformed;
    // leave it editable rather than lock in a value the order insert would reject.
    val locked = isBusinessBuyer() && this?.kycStatus == "verified" && isValidGstin(gstin)
    return GstinPrefill(gstin, locked)
}
