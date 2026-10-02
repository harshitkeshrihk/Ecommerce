package com.example.vishnu.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GstinTest {

    @Test
    fun `accepts GSTINs with a correct check character`() {
        assertTrue(isValidGstin("27AAPFU0939F1ZV"))
        assertTrue(isValidGstin("29AAGCB7383J1Z4"))
    }

    @Test
    fun `rejects a single-character typo via the check character`() {
        assertFalse(isValidGstin("27AAPFU0939F1ZW"))
        assertFalse(isValidGstin("27AAPFU0938F1ZV"))
    }

    @Test
    fun `rejects malformed input`() {
        assertFalse(isValidGstin(""))
        assertFalse(isValidGstin("27AAPFU0939F1Z"))      // too short
        assertFalse(isValidGstin("27aapfu0939f1zv"))     // not normalized
        assertFalse(isValidGstin("27AAPFU0939F1AV"))     // 14th char must be Z
        assertFalse(isValidGstin("2AAAPFU0939F1ZV"))     // state code not numeric
    }

    @Test
    fun `normalize strips spaces and uppercases`() {
        assertEquals("27AAPFU0939F1ZV", normalizeGstin(" 27aapfu 0939f1zv "))
    }
}
