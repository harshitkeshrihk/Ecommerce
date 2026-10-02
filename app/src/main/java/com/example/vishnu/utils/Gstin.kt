package com.example.vishnu.utils

// Same pattern as the orders_gstin_format_check constraint in
// supabase/011_unified_wholesale.sql: state code, PAN, entity code, 'Z', check character.
private val GSTIN_PATTERN = Regex("^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z][1-9A-Z]Z[0-9A-Z]$")
private const val GSTIN_CHARSET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ"

/** Uppercases and strips spaces, so " 27aapfu0939f1zv " becomes "27AAPFU0939F1ZV". */
fun normalizeGstin(raw: String): String = raw.filterNot { it.isWhitespace() }.uppercase()

/**
 * Format plus the GSTN check character (mod-36 over the first 14 characters).
 * This catches typos only. It does not prove the GSTIN is registered.
 */
fun isValidGstin(gstin: String): Boolean {
    if (!GSTIN_PATTERN.matches(gstin)) return false
    val sum = gstin.take(14).withIndex().sumOf { (i, c) ->
        val product = GSTIN_CHARSET.indexOf(c) * (if (i % 2 == 0) 1 else 2)
        product / 36 + product % 36
    }
    return GSTIN_CHARSET[(36 - sum % 36) % 36] == gstin[14]
}
