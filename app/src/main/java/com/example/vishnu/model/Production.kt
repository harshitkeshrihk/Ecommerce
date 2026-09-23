package com.example.vishnu.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.LocalDate

// --- Supabase rows ---

// No defaults on purpose: SupabaseModule's Json drops default-valued fields,
// so a defaulted value would silently never reach an update.
@Serializable
data class CapacitySettings(
    @SerialName("default_packs_per_day") val defaultPacksPerDay: Int,
    @SerialName("closed_weekdays") val closedWeekdays: List<Int> // ISO 1 = Mon ... 7 = Sun
)

@Serializable
data class CapacityOverride(
    val day: String, // yyyy-MM-dd
    val packs: Int,
    val note: String?
)

@Serializable
data class OpenLoadRow(
    @SerialName("ship_by_date") val shipByDate: String,
    val packs: Long
)

/** Order statuses that still need workshop time. */
val OPEN_PRODUCTION_STATUSES = setOf("PAID", "PROCESSING")

// --- Capacity ---

class CapacityCalendar(
    private val settings: CapacitySettings,
    private val overrides: Map<LocalDate, Int>
) {
    /** Packs the workshop can make on [date]. An override beats the weekly off-day rule. */
    fun capacityOn(date: LocalDate): Int =
        overrides[date]
            ?: if (date.dayOfWeek.value in settings.closedWeekdays) 0 else settings.defaultPacksPerDay

    fun isOverridden(date: LocalDate): Boolean = date in overrides

    companion object {
        fun from(settings: CapacitySettings, overrides: List<CapacityOverride>) = CapacityCalendar(
            settings,
            overrides.mapNotNull { o -> runCatching { LocalDate.parse(o.day) }.getOrNull()?.let { it to o.packs } }.toMap()
        )
    }
}

// --- Scheduling ---

/** Work that needs [packs] made on days strictly before [shipBy]. */
data class ProductionJob(val id: String, val shipBy: LocalDate, val packs: Int)

data class ProductionPlan(
    /** Day -> (job id, packs made that day), in the order they're worked. */
    val allocations: Map<LocalDate, List<Pair<String, Int>>>,
    /** Last production day per job; null if it can't be finished within the horizon. */
    val finishDay: Map<String, LocalDate?>,
    /** Jobs that won't be finished before their ship-by date. */
    val late: Set<String>
) {
    fun plannedOn(date: LocalDate): Int = allocations[date]?.sumOf { it.second } ?: 0
}

object ProductionScheduler {

    /** How far ahead the planner will look before declaring a job unfinishable. */
    const val HORIZON_DAYS = 730L

    /**
     * Earliest-ship-by-first: jobs are worked in ship-by order, each filling the
     * remaining capacity of consecutive days starting at [from]. With all jobs
     * available now and one shared capacity, this ordering finishes every job
     * on time whenever any ordering can — so "late" here means genuinely
     * over-committed, not a planning artefact.
     */
    fun plan(jobs: List<ProductionJob>, calendar: CapacityCalendar, from: LocalDate): ProductionPlan {
        val allocations = linkedMapOf<LocalDate, MutableList<Pair<String, Int>>>()
        val finishDay = linkedMapOf<String, LocalDate?>()
        val late = linkedSetOf<String>()
        val lastDay = from.plusDays(HORIZON_DAYS)

        var day = from
        var remainingToday = calendar.capacityOn(day)

        for (job in jobs.sortedWith(compareBy({ it.shipBy }, { it.id }))) {
            var needed = job.packs
            var finished: LocalDate? = if (needed <= 0) from else null
            while (needed > 0 && !day.isAfter(lastDay)) {
                if (remainingToday <= 0) {
                    day = day.plusDays(1)
                    remainingToday = calendar.capacityOn(day)
                    continue
                }
                val made = minOf(needed, remainingToday)
                allocations.getOrPut(day) { mutableListOf() } += job.id to made
                needed -= made
                remainingToday -= made
                if (needed == 0) finished = day
            }
            finishDay[job.id] = finished
            if (finished == null || !finished.isBefore(job.shipBy)) late += job.id
        }
        return ProductionPlan(allocations, finishDay, late)
    }

    /**
     * checkProductionCapacity(ship_by_date, qty): can [newJob] be accepted
     * without it, or any order already booked, missing its ship-by date?
     * Orders that were already going to be late don't count against the new one.
     */
    fun check(
        existing: List<ProductionJob>,
        newJob: ProductionJob,
        calendar: CapacityCalendar,
        today: LocalDate
    ): CapacityCheck {
        val lateBefore = plan(existing, calendar, today).late
        val after = plan(existing + newJob, calendar, today)
        if (newJob.id !in after.late && after.late == lateBefore) return CapacityCheck.Available

        val earliest = generateSequence(newJob.shipBy.plusDays(1)) { it.plusDays(1) }
            .take(365)
            .firstOrNull { candidate ->
                val p = plan(existing + newJob.copy(shipBy = candidate), calendar, today)
                newJob.id !in p.late && p.late == lateBefore
            }
        return CapacityCheck.Conflict(earliestAvailable = earliest)
    }

    /** Open gifting orders as jobs (admin view: one job per order). */
    fun jobsFrom(orders: List<GiftingOrder>): List<ProductionJob> =
        orders
            .filter { it.order.status in OPEN_PRODUCTION_STATUSES }
            .mapNotNull { o ->
                runCatching { LocalDate.parse(o.shipByDate) }.getOrNull()
                    ?.let { ProductionJob(o.orderId.toString(), it, o.packCount) }
            }

    /** Aggregated open load as jobs (customer view: one job per ship-by date). */
    fun jobsFromLoad(rows: List<OpenLoadRow>): List<ProductionJob> =
        rows.mapNotNull { r ->
            runCatching { LocalDate.parse(r.shipByDate) }.getOrNull()
                ?.let { ProductionJob("load-${r.shipByDate}", it, r.packs.toInt()) }
        }
}

sealed class CapacityCheck {
    object Available : CapacityCheck()
    /** [earliestAvailable] is null when nothing within a year works (e.g. capacity set to 0). */
    data class Conflict(val earliestAvailable: LocalDate?) : CapacityCheck()
}
