package com.example.vishnu.model

import com.example.vishnu.repository.testProduct
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** Pack pricing, budget tiers and checkout validation for bulk gifting (Doc 4 §03). */
class GiftingRulesTest {

    private val bowl = testProduct("bowl", priceRetail = 120.0)
    private val spoon = testProduct("spoon", priceRetail = 30.0)
    private val box = testProduct("box", priceRetail = 50.0)

    // 1 bowl + 2 spoons + 1 box = 120 + 60 + 50 = 230 per pack
    private val draft = GiftPackDraft(
        name = "Steel Return-Gift Set",
        sourcePackId = "pack-1",
        lines = listOf(GiftPackLine(bowl, 1), GiftPackLine(spoon, 2), GiftPackLine(box, 1))
    )

    private val today = LocalDate.of(2026, 9, 22)

    // --- Pricing ---

    @Test
    fun `price per pack is the sum of retail price x qty per pack`() {
        assertEquals(230.0, draft.pricePerPack, 0.0)
    }

    @Test
    fun `total scales linearly with pack count`() {
        assertEquals(230.0 * 150, draft.totalFor(150), 0.0)
    }

    @Test
    fun `pack price uses retail, never wholesale`() {
        val d = GiftPackDraft("x", null, listOf(GiftPackLine(testProduct("p", priceRetail = 100.0, priceWholesale = 10.0), 1)))
        assertEquals(100.0, d.pricePerPack, 0.0)
    }

    @Test
    fun `expanded quantities multiply each line by pack count`() {
        val expanded = draft.expandedQuantities(150).associate { (p, q) -> p.id to q }
        assertEquals(mapOf("bowl" to 150, "spoon" to 300, "box" to 150), expanded)
    }

    @Test
    fun `expanded lines at retail price sum to the order total`() {
        val sum = draft.expandedQuantities(37).sumOf { (p, q) -> p.priceRetail * q }
        assertEquals(draft.totalFor(37), sum, 0.0)
    }

    @Test
    fun `admin pack converts to a draft with the same contents`() {
        val pack = GiftPack(
            id = "pack-9",
            name = "Copper Set",
            items = listOf(GiftPackItem("i1", 2, bowl), GiftPackItem("i2", 1, box))
        )
        val d = pack.toDraft()
        assertEquals("pack-9", d.sourcePackId)
        assertEquals("Copper Set", d.name)
        assertEquals(listOf("bowl" to 2, "box" to 1), d.lines.map { it.product.id to it.qtyPerPack })
    }

    // --- Budget tiers ---

    @Test
    fun `budget tier boundaries are lower-inclusive, upper-exclusive`() {
        val (low, mid, high) = GiftingRules.BUDGET_TIERS
        assertTrue(low.matches(299.99))
        assertFalse(low.matches(300.0))
        assertTrue(mid.matches(300.0))
        assertFalse(mid.matches(700.0))
        assertTrue(high.matches(700.0))
        assertTrue(high.matches(50_000.0))
    }

    @Test
    fun `every price falls in exactly one tier`() {
        listOf(0.0, 150.0, 300.0, 450.0, 699.99, 700.0, 2500.0).forEach { price ->
            assertEquals("price $price", 1, GiftingRules.BUDGET_TIERS.count { it.matches(price) })
        }
    }

    // --- Validation ---

    private fun validate(
        d: GiftPackDraft = draft,
        packCount: Int? = 50,
        personalization: String = "",
        shipBy: LocalDate? = today.plusDays(30),
        address: String = "12 MG Road"
    ) = GiftingRules.validate(d, packCount, personalization, shipBy, address, today)

    @Test
    fun `a complete order passes`() {
        assertNull(validate())
    }

    @Test
    fun `empty pack is rejected`() {
        assertEquals("Add at least one item to the pack", validate(d = draft.copy(lines = emptyList())))
    }

    @Test
    fun `below the minimum pack count is rejected, at the minimum passes`() {
        assertEquals("Minimum order is 10 packs", validate(packCount = GiftingRules.MIN_PACK_COUNT - 1))
        assertEquals("Minimum order is 10 packs", validate(packCount = null))
        assertNull(validate(packCount = GiftingRules.MIN_PACK_COUNT))
    }

    @Test
    fun `personalization over the limit is rejected, at the limit passes`() {
        val max = GiftingRules.MAX_PERSONALIZATION_LENGTH
        assertNull(validate(personalization = "a".repeat(max)))
        assertTrue(validate(personalization = "a".repeat(max + 1))!!.startsWith("Personalization"))
    }

    @Test
    fun `ship-by date must be set and strictly in the future`() {
        assertEquals("Choose a ship-by date", validate(shipBy = null))
        assertEquals("Ship-by date must be in the future", validate(shipBy = today))
        assertEquals("Ship-by date must be in the future", validate(shipBy = today.minusDays(1)))
        assertNull(validate(shipBy = today.plusDays(1)))
    }

    @Test
    fun `blank address is rejected`() {
        assertEquals("Enter a delivery address", validate(address = "   "))
    }

    // --- Advance + balance ---

    @Test
    fun `minimum advance is 40 percent rounded up to the rupee, never above the total`() {
        assertEquals(10_800.0, GiftingRules.minAdvance(27_000.0), 0.0)
        assertEquals(401.0, GiftingRules.minAdvance(1_000.5), 0.0) // 400.2 -> 401
        assertEquals(0.5, GiftingRules.minAdvance(0.5), 0.0)
    }

    @Test
    fun `balance is due 3 days before ship-by`() {
        assertEquals(LocalDate.of(2026, 10, 29), GiftingRules.balanceDueDate(LocalDate.of(2026, 11, 1)))
    }

    @Test
    fun `full payment is required once the balance due date is today or past`() {
        assertTrue(GiftingRules.requiresFullPayment(today.plusDays(3), today))  // due today
        assertTrue(GiftingRules.requiresFullPayment(today.plusDays(1), today))
        assertFalse(GiftingRules.requiresFullPayment(today.plusDays(4), today)) // due tomorrow
    }

    private val shipByLater = today.plusDays(30)

    @Test
    fun `pay-now amount between 40 percent and the total passes`() {
        assertNull(GiftingRules.validatePayNow(400.0, 1_000.0, shipByLater, today))
        assertNull(GiftingRules.validatePayNow(750.0, 1_000.0, shipByLater, today))
        assertNull(GiftingRules.validatePayNow(1_000.0, 1_000.0, shipByLater, today))
    }

    @Test
    fun `pay-now below the minimum advance is rejected`() {
        assertEquals("Minimum advance is 40% of the total", GiftingRules.validatePayNow(399.0, 1_000.0, shipByLater, today))
    }

    @Test
    fun `pay-now above the total, zero or missing is rejected`() {
        assertEquals("You can't pay more than the order total", GiftingRules.validatePayNow(1_000.5, 1_000.0, shipByLater, today))
        assertEquals("Enter the amount to pay now", GiftingRules.validatePayNow(0.0, 1_000.0, shipByLater, today))
        assertEquals("Enter the amount to pay now", GiftingRules.validatePayNow(null, 1_000.0, shipByLater, today))
    }

    @Test
    fun `when the ship-by date is close only the full amount is accepted`() {
        val soon = today.plusDays(2)
        assertTrue(GiftingRules.validatePayNow(900.0, 1_000.0, soon, today)!!.contains("full amount is due now"))
        assertNull(GiftingRules.validatePayNow(1_000.0, 1_000.0, soon, today))
    }

    @Test
    fun `order payment totals and balance`() {
        val order = GiftingOrder(
            orderId = 1, occasionType = "wedding", packName = "Set", packCount = 100,
            packContents = emptyList(), shipByDate = "2026-11-01", balanceDueDate = "2026-10-29",
            order = Order(1, "2026-09-23T10:00:00", 27_000.0, "ADVANCE_PAID", "pay", "addr", "gifting"),
            payments = listOf(GiftingPayment("pay_1", 10_800.0, "advance"))
        )
        assertEquals(10_800.0, order.amountPaid, 0.0)
        assertEquals(16_200.0, order.balance, 0.0)
        assertFalse(order.isFullyPaid)
        assertTrue(order.copy(payments = order.payments + GiftingPayment("pay_2", 16_200.0, "balance")).isFullyPaid)
    }
}
