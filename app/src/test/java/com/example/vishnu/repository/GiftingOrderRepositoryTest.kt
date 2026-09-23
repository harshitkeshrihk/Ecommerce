package com.example.vishnu.repository

import com.example.vishnu.model.GiftPackDraft
import com.example.vishnu.model.GiftPackLine
import com.example.vishnu.model.OccasionType
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** Gifting order creation after payment (Doc 4 §03, event gifting end-to-end). */
class GiftingOrderRepositoryTest {

    private val bowl = testProduct("bowl", priceRetail = 120.0)
    private val spoon = testProduct("spoon", priceRetail = 30.0)
    private val draft = GiftPackDraft(
        name = "Steel Set",
        sourcePackId = "pack-1",
        lines = listOf(GiftPackLine(bowl, 1), GiftPackLine(spoon, 2))
    ) // 180 per pack

    private val user = FakeCurrentUser()
    private val orderDb = FakeOrderDataSource()
    private val giftingDb = FakeGiftingOrderDataSource()
    private val repo = GiftingOrderRepository(orderDb, giftingDb, user)

    private suspend fun place(
        personalization: String = "Sharma Family",
        packCount: Int = 150,
        amountPaid: Double = 180.0 * packCount // full by default
    ) = repo.placeGiftingOrder(
        draft = draft,
        occasion = OccasionType.EVENT,
        packCount = packCount,
        personalization = personalization,
        shipByDate = LocalDate.of(2026, 11, 1),
        address = "12 MG Road",
        paymentId = "pay_abc",
        amountPaid = amountPaid
    )

    @Test
    fun `writes a paid gifting order for the full pack total`() = runTest {
        val result = place()

        val (orderId, order) = orderDb.orders.single()
        assertEquals(GiftingOrderResult.Placed(orderId), result)
        assertEquals("gifting", order.channel)
        assertEquals("PAID", order.status)
        assertEquals("pay_abc", order.paymentId)
        assertEquals(180.0 * 150, order.totalAmount, 0.0)
        assertEquals("user-1", order.userId)
        assertEquals("12 MG Road", order.address)
    }

    @Test
    fun `order items hold the expanded quantities at retail price`() = runTest {
        place()

        val byProduct = orderDb.items.associateBy { it.productId }
        assertEquals(150, byProduct.getValue("bowl").quantity)
        assertEquals(300, byProduct.getValue("spoon").quantity)
        assertEquals(30.0, byProduct.getValue("spoon").price, 0.0)
        assertEquals(orderDb.orders.single().second.totalAmount, orderDb.items.sumOf { it.price * it.quantity }, 0.0)
    }

    @Test
    fun `gifting row records occasion, per-pack contents, personalization and ship-by date`() = runTest {
        place()

        val row = giftingDb.rows.single()
        assertEquals(orderDb.orders.single().first, row.orderId)
        assertEquals("event", row.occasionType)
        assertEquals("pack-1", row.sourcePackId)
        assertEquals("Steel Set", row.packName)
        assertEquals(150, row.packCount)
        assertEquals(listOf("bowl" to 1, "spoon" to 2), row.packContents.map { it.productId to it.qtyPerPack })
        assertEquals("Sharma Family", row.personalizationText)
        assertEquals("2026-11-01", row.shipByDate)
    }

    @Test
    fun `blank personalization is stored as null`() = runTest {
        place(personalization = "   ")
        assertNull(giftingDb.rows.single().personalizationText)
    }

    @Test
    fun `fails without a signed-in user and writes nothing`() = runTest {
        user.id = null
        assertTrue(place() is GiftingOrderResult.Failed)
        assertTrue(orderDb.orders.isEmpty())
    }

    @Test
    fun `fails for an empty pack`() = runTest {
        val result = repo.placeGiftingOrder(
            draft.copy(lines = emptyList()), OccasionType.EVENT, 10, "", LocalDate.of(2026, 11, 1), "addr", "pay_1", 100.0
        )
        assertTrue(result is GiftingOrderResult.Failed)
        assertTrue(orderDb.orders.isEmpty())
    }

    @Test
    fun `a failed write after payment reports the payment id so support can reconcile`() = runTest {
        giftingDb.failInsert = true

        val result = place()

        assertTrue(result is GiftingOrderResult.Failed)
        assertTrue((result as GiftingOrderResult.Failed).message.contains("pay_abc"))
    }

    // --- Advance + balance ---

    @Test
    fun `paying in full marks the order PAID and records a full payment`() = runTest {
        place(amountPaid = 180.0 * 150)

        assertEquals("PAID", orderDb.orders.single().second.status)
        val payment = giftingDb.payments.single()
        assertEquals("full", payment.kind)
        assertEquals(27_000.0, payment.amount, 0.0)
        assertEquals("pay_abc", payment.razorpayPaymentId)
        assertEquals(orderDb.orders.single().first, payment.orderId)
    }

    @Test
    fun `paying an advance marks the order ADVANCE_PAID and records an advance payment`() = runTest {
        place(amountPaid = 10_800.0) // 40% of 27,000

        assertEquals("ADVANCE_PAID", orderDb.orders.single().second.status)
        // The order total is still the full amount - only the payment is partial.
        assertEquals(27_000.0, orderDb.orders.single().second.totalAmount, 0.0)
        assertEquals("advance", giftingDb.payments.single().kind)
        assertEquals(10_800.0, giftingDb.payments.single().amount, 0.0)
    }

    @Test
    fun `balance is due 3 days before the ship-by date`() = runTest {
        place()
        assertEquals("2026-10-29", giftingDb.rows.single().balanceDueDate)
    }

    @Test
    fun `balance payment is recorded against the order`() = runTest {
        val result = repo.payBalance(orderId = 101, paymentId = "pay_bal", amount = 16_200.0)

        assertEquals(GiftingOrderResult.Placed(101), result)
        assertEquals(Triple(101L, "pay_bal", 16_200.0), giftingDb.balancePayments.single())
    }

    @Test
    fun `a rejected balance payment reports the payment id`() = runTest {
        giftingDb.failBalance = true
        val result = repo.payBalance(orderId = 101, paymentId = "pay_bal", amount = 1.0)
        assertTrue((result as GiftingOrderResult.Failed).message.contains("pay_bal"))
    }

    // --- Corporate logo (slice 4a) ---

    @Test
    fun `a corporate order without a logo is refused and nothing is written`() = runTest {
        val result = repo.placeGiftingOrder(
            draft, OccasionType.CORPORATE, 150, "", LocalDate.of(2026, 11, 1), "addr", "pay_c", 27_000.0,
            logoPath = null
        )
        assertTrue(result is GiftingOrderResult.Failed)
        assertTrue(orderDb.orders.isEmpty())
    }

    @Test
    fun `corporate logo path and placement notes are saved on the gifting row`() = runTest {
        repo.placeGiftingOrder(
            draft, OccasionType.CORPORATE, 150, "", LocalDate.of(2026, 11, 1), "addr", "pay_c", 27_000.0,
            logoPath = "user-1/logos/abc.png", logoNotes = "  Gold foil on the lid  "
        )
        val row = giftingDb.rows.single()
        assertEquals("corporate", row.occasionType)
        assertEquals("user-1/logos/abc.png", row.logoPath)
        assertEquals("Gold foil on the lid", row.logoNotes)
    }

    @Test
    fun `non-corporate orders carry no logo`() = runTest {
        place()
        assertNull(giftingDb.rows.single().logoPath)
    }
}
