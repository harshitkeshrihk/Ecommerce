package com.example.vishnu.repository

import android.util.Log
import com.example.vishnu.model.CapacityCalendar
import com.example.vishnu.model.CapacityOverride
import com.example.vishnu.model.CapacitySettings
import com.example.vishnu.model.OpenLoadRow
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.query.Order as SupabaseOrder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/** Workshop capacity (settings + date overrides) and the open production load. */
@Singleton
class ProductionRepository @Inject constructor(
    private val postgrest: Postgrest
) {
    suspend fun getSettings(): CapacitySettings? = withContext(Dispatchers.IO) {
        try {
            postgrest["production_capacity_settings"]
                .select { filter { eq("id", 1) } }
                .decodeSingleOrNull<CapacitySettings>()
        } catch (e: Exception) {
            Log.e("ProductionRepo", "Error fetching capacity settings", e)
            null
        }
    }

    /** Overrides from [from] onwards (past ones don't affect planning). */
    suspend fun getOverrides(from: LocalDate): List<CapacityOverride> = withContext(Dispatchers.IO) {
        try {
            postgrest["production_capacity_overrides"]
                .select {
                    filter { gte("day", from.toString()) }
                    order("day", order = SupabaseOrder.ASCENDING)
                }
                .decodeList<CapacityOverride>()
        } catch (e: Exception) {
            Log.e("ProductionRepo", "Error fetching capacity overrides", e)
            emptyList()
        }
    }

    /** Capacity calendar, or null if settings couldn't be loaded (caller must not assume capacity). */
    suspend fun getCalendar(today: LocalDate): CapacityCalendar? {
        val settings = getSettings() ?: return null
        return CapacityCalendar.from(settings, getOverrides(today))
    }

    /** Unshipped gifting packs per ship-by date, across all customers (aggregated server-side). */
    suspend fun getOpenLoad(): List<OpenLoadRow>? = withContext(Dispatchers.IO) {
        try {
            postgrest.rpc("gifting_open_load").decodeList<OpenLoadRow>()
        } catch (e: Exception) {
            Log.e("ProductionRepo", "Error fetching open production load", e)
            null
        }
    }

    // --- Admin ---

    suspend fun saveSettings(settings: CapacitySettings): Boolean = withContext(Dispatchers.IO) {
        try {
            postgrest["production_capacity_settings"].update(settings) { filter { eq("id", 1) } }
            true
        } catch (e: Exception) {
            Log.e("ProductionRepo", "Error saving capacity settings", e)
            false
        }
    }

    suspend fun upsertOverride(override: CapacityOverride): Boolean = withContext(Dispatchers.IO) {
        try {
            postgrest["production_capacity_overrides"].upsert(override) { onConflict = "day" }
            true
        } catch (e: Exception) {
            Log.e("ProductionRepo", "Error saving capacity override", e)
            false
        }
    }

    suspend fun deleteOverride(day: String): Boolean = withContext(Dispatchers.IO) {
        try {
            postgrest["production_capacity_overrides"].delete { filter { eq("day", day) } }
            true
        } catch (e: Exception) {
            Log.e("ProductionRepo", "Error deleting capacity override", e)
            false
        }
    }
}
