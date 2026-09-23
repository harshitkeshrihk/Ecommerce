package com.example.vishnu.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Doc 4 §03 acceptance criterion: "The production calendar surfaces a capacity
 * conflict before, not after, a ship-by date is missed."
 */
class ProductionSchedulerTest {

    // Monday 21 Sep 2026. Workshop: 100 packs/day, closed Sundays.
    private val mon = LocalDate.of(2026, 9, 21)
    private val tue = mon.plusDays(1)
    private val wed = mon.plusDays(2)
    private val thu = mon.plusDays(3)
    private val nextMon = mon.plusDays(7)

    private val settings = CapacitySettings(defaultPacksPerDay = 100, closedWeekdays = listOf(7))
    private val calendar = CapacityCalendar(settings, emptyMap())

    private fun job(id: String, shipBy: LocalDate, packs: Int) = ProductionJob(id, shipBy, packs)

    // --- Capacity calendar ---

    @Test
    fun `default capacity applies on open days, zero on weekly off days`() {
        assertEquals(100, calendar.capacityOn(mon))
        assertEquals(0, calendar.capacityOn(mon.plusDays(6))) // Sunday
    }

    @Test
    fun `a date override beats both the default and the weekly off day`() {
        val sunday = mon.plusDays(6)
        val cal = CapacityCalendar.from(
            settings,
            listOf(CapacityOverride(sunday.toString(), 250, "extra shift"), CapacityOverride(tue.toString(), 0, "Holiday"))
        )
        assertEquals(250, cal.capacityOn(sunday))
        assertEquals(0, cal.capacityOn(tue))
        assertTrue(cal.isOverridden(tue))
        assertFalse(cal.isOverridden(wed))
    }

    // --- Planning ---

    @Test
    fun `a job spills across consecutive days`() {
        val plan = ProductionScheduler.plan(listOf(job("a", thu, 250)), calendar, mon)

        assertEquals(100, plan.plannedOn(mon))
        assertEquals(100, plan.plannedOn(tue))
        assertEquals(50, plan.plannedOn(wed))
        assertEquals(wed, plan.finishDay["a"])
        assertTrue(plan.late.isEmpty())
    }

    @Test
    fun `finishing ON the ship-by date is late - it must be done the day before`() {
        val plan = ProductionScheduler.plan(listOf(job("a", wed, 250)), calendar, mon)
        assertEquals(setOf("a"), plan.late)
    }

    @Test
    fun `closed days are skipped`() {
        val plan = ProductionScheduler.plan(listOf(job("a", nextMon.plusDays(1), 700)), calendar, mon)
        assertEquals(0, plan.plannedOn(mon.plusDays(6))) // Sunday
        assertEquals(nextMon, plan.finishDay["a"])
    }

    @Test
    fun `earliest ship-by date is worked first regardless of input order`() {
        val plan = ProductionScheduler.plan(
            listOf(job("later", LocalDate.of(2026, 10, 10), 150), job("sooner", wed, 150)),
            calendar,
            mon
        )
        assertEquals(listOf("sooner" to 100), plan.allocations[mon])
        assertEquals(tue, plan.finishDay["sooner"])
        assertEquals(wed, plan.finishDay["later"])
        assertTrue(plan.late.isEmpty())
    }

    @Test
    fun `with no capacity at all a job never finishes and is late`() {
        val closed = CapacityCalendar(CapacitySettings(0, emptyList()), emptyMap())
        val plan = ProductionScheduler.plan(listOf(job("a", thu, 10)), closed, mon)
        assertNull(plan.finishDay["a"])
        assertEquals(setOf("a"), plan.late)
    }

    // --- checkProductionCapacity ---

    @Test
    fun `an order that fits is available`() {
        val result = ProductionScheduler.check(emptyList(), job("new", thu, 250), calendar, mon)
        assertEquals(CapacityCheck.Available, result)
    }

    @Test
    fun `an order that can't be finished in time conflicts and suggests the earliest date that works`() {
        val result = ProductionScheduler.check(emptyList(), job("new", wed, 250), calendar, mon)
        assertEquals(CapacityCheck.Conflict(earliestAvailable = thu), result)
    }

    @Test
    fun `an order that would push an existing order past its ship-by date conflicts`() {
        // "existing" alone finishes Tue, before its Wed ship-by. A new, more urgent
        // order jumps ahead and would push it to finish ON Wed — too late.
        val existing = listOf(job("x-existing", wed, 200))
        val result = ProductionScheduler.check(existing, job("new", tue, 100), calendar, mon)
        assertEquals(CapacityCheck.Conflict(earliestAvailable = thu), result)
    }

    @Test
    fun `existing bookings reduce what's left for new orders`() {
        // y takes all of Mon + Tue, so the new order only starts Wed and finishes Thu.
        val existing = listOf(job("y", wed, 200))
        val result = ProductionScheduler.check(existing, job("new", thu, 150), calendar, mon)
        assertEquals(CapacityCheck.Conflict(earliestAvailable = mon.plusDays(4)), result) // Fri
    }

    @Test
    fun `a later-due booking yields to a sooner order when it can still finish on time`() {
        // x was booked first but isn't due until 30 Oct, so a Wed order can go ahead of it.
        val existing = listOf(job("x", LocalDate.of(2026, 10, 30), 500))
        val result = ProductionScheduler.check(existing, job("new", wed, 150), calendar, mon)
        assertEquals(CapacityCheck.Available, result)
    }

    @Test
    fun `an overdue unshipped order doesn't block an unrelated new order`() {
        val overdue = listOf(job("overdue", mon.minusDays(1), 100))
        val result = ProductionScheduler.check(overdue, job("new", thu, 100), calendar, mon)
        assertEquals(CapacityCheck.Available, result)
    }

    @Test
    fun `with zero capacity there is no earliest date to suggest`() {
        val closed = CapacityCalendar(CapacitySettings(0, emptyList()), emptyMap())
        val result = ProductionScheduler.check(emptyList(), job("new", thu, 10), closed, mon)
        assertEquals(CapacityCheck.Conflict(earliestAvailable = null), result)
    }

    // --- Mapping ---

    @Test
    fun `only paid and in-production orders count as open work`() {
        fun order(id: Long, status: String) = GiftingOrder(
            orderId = id, occasionType = "event", packName = "Set", packCount = 10,
            packContents = emptyList(), shipByDate = "2026-10-01",
            order = Order(id, "2026-09-21T10:00:00", 1000.0, status, "pay", "addr", "gifting")
        )
        val jobs = ProductionScheduler.jobsFrom(
            listOf(order(1, "PAID"), order(2, "PROCESSING"), order(3, "SHIPPED"), order(4, "DELIVERED"), order(5, "CANCELLED"))
        )
        assertEquals(listOf("1", "2"), jobs.map { it.id })
    }

    @Test
    fun `aggregated load rows become one job per ship-by date`() {
        val jobs = ProductionScheduler.jobsFromLoad(listOf(OpenLoadRow("2026-10-01", 300), OpenLoadRow("bad-date", 5)))
        assertEquals(listOf(ProductionJob("load-2026-10-01", LocalDate.of(2026, 10, 1), 300)), jobs)
    }
}
