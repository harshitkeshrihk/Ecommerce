package com.example.vishnu.utils

import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

// Helper to format ISO dates nicely
fun formatIsoDate(isoString: String): String {
    return try {
        // Input format (from Supabase)
        val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
        parser.timeZone = TimeZone.getTimeZone("UTC") // Supabase sends UTC
        val date = parser.parse(isoString) ?: return isoString

        // Output format (nice looking)
        val formatter = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
        formatter.timeZone = TimeZone.getDefault() // Convert to user's local time
        formatter.format(date)
    } catch (e: Exception) {
        isoString // Fallback if parsing fails
    }
}

fun last6Digits(value: Long): Long {
    return kotlin.math.abs(value) % 1_000_000
}

/** ₹ with Indian digit grouping (₹1,23,456), paise shown only when non-zero. */
fun formatRupees(amount: Double): String {
    val format = java.text.NumberFormat.getCurrencyInstance(Locale("en", "IN"))
    format.maximumFractionDigits = if (amount % 1.0 == 0.0) 0 else 2
    format.minimumFractionDigits = format.maximumFractionDigits
    return format.format(amount)
}
