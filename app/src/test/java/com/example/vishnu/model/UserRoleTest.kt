package com.example.vishnu.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Who orders at slab prices, and when the checkout GSTIN is pre-filled and locked. */
class UserRoleTest {

    private val validGstin = "27AAPFU0939F1ZV"

    private fun profile(role: String, gstin: String? = null, kycStatus: String? = null) =
        UserProfile(id = "u1", role = role, gstin = gstin, kycStatus = kycStatus)

    @Test
    fun `only wholesale, distributor and admin are business buyers`() {
        assertTrue(profile("wholesale").isBusinessBuyer())
        assertTrue(profile("distributor").isBusinessBuyer())
        assertTrue(profile("admin").isBusinessBuyer())
        assertFalse(profile("retail").isBusinessBuyer())
        assertFalse((null as UserProfile?).isBusinessBuyer())
    }

    @Test
    fun `verified business account gets its GSTIN pre-filled and locked`() {
        val prefill = profile("wholesale", validGstin, "verified").gstinPrefill()
        assertEquals(GstinPrefill(validGstin, locked = true), prefill)
    }

    @Test
    fun `pending application pre-fills but leaves the GSTIN editable`() {
        val prefill = profile("retail", validGstin, "pending").gstinPrefill()
        assertEquals(GstinPrefill(validGstin, locked = false), prefill)
    }

    @Test
    fun `malformed KYC GSTIN is never locked`() {
        val prefill = profile("wholesale", "27aapfu0939f1zx", "verified").gstinPrefill()
        assertEquals(GstinPrefill("27AAPFU0939F1ZX", locked = false), prefill)
    }

    @Test
    fun `retail account without KYC starts with an empty field`() {
        assertEquals(GstinPrefill("", locked = false), profile("retail").gstinPrefill())
    }
}
