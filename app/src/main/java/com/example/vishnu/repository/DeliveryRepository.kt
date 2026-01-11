package com.example.vishnu.repository

import android.util.Log
import com.example.vishnu.api.DirectionsApiService
import com.example.vishnu.model.DeliveryAssignment
import com.example.vishnu.model.DeliveryLocationUpdate
import com.example.vishnu.model.DeliveryPartner
import com.example.vishnu.utils.PolylineUtils
import com.google.android.gms.maps.model.LatLng
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject
import javax.inject.Singleton
import io.github.jan.supabase.realtime.postgresChangeFlow
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.coroutines.launch

@Singleton
class DeliveryRepository @Inject constructor(
    private val postgrest: Postgrest,
    private val realtime: Realtime,
    private val directionsApi: DirectionsApiService
) {
    private val TAG = "DeliveryRepository"

    /**
     * Get delivery assignment for an order
     */
    suspend fun getDeliveryAssignment(orderId: Long): DeliveryAssignment? = withContext(Dispatchers.IO) {
        try {
            postgrest.from("delivery_assignments")
                .select {
                    filter {
                        eq("order_id", orderId)
                    }
                }
                .decodeSingleOrNull<DeliveryAssignment>()
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching delivery assignment", e)
            null
        }
    }

    /**
     * Get delivery partner details
     */
    suspend fun getDeliveryPartner(partnerId: String): DeliveryPartner? = withContext(Dispatchers.IO) {
        try {
            postgrest.from("delivery_partners")
                .select {
                    filter {
                        eq("id", partnerId)
                    }
                }
                .decodeSingleOrNull<DeliveryPartner>()
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching delivery partner", e)
            null
        }
    }

    suspend fun getAvailablePartners(): List<DeliveryPartner> = withContext(Dispatchers.IO) {
        try {
            postgrest.from("delivery_partners")
                .select {
                    filter {
                        eq("is_available", true)
                    }
                }
                .decodeList<DeliveryPartner>()
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching partners", e)
            emptyList()
        }
    }

    suspend fun assignOrderToPartner(
        orderId: Long,
        partnerId: String,
        pickupLat: Double,
        pickupLng: Double,
        destLat: Double,
        destLng: Double
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            // 1. Create the assignment entry
            val assignment = DeliveryAssignment(
                orderId = orderId,
                deliveryPartnerId = partnerId,
                deliveryStatus = "ASSIGNED",
                pickupLatitude = pickupLat,
                pickupLongitude = pickupLng,
                deliveryLatitude = destLat,
                deliveryLongitude = destLng,
                assignedAt = kotlinx.datetime.Clock.System.now().toString()
            )

            postgrest.from("delivery_assignments").insert(assignment)

            // 2. Update the main Order status to indicate it's processed
            postgrest.from("orders").update(
                {
                    set("status", "SHIPPED") // Or "ASSIGNED"
                }
            ) {
                filter {
                    eq("id", orderId)
                }
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error assigning order", e)
            false
        }
    }

    /**
     * Subscribe to real-time location updates for a delivery partner
     * This uses Supabase Realtime to get live location updates
     * Falls back to polling if realtime is not available
     */
    fun subscribeToLocationUpdates(deliveryPartnerId: String): Flow<DeliveryLocationUpdate> = callbackFlow {
        val channelName = "delivery_location:$deliveryPartnerId"
        // 1. Create the channel
        val channel = realtime.channel(channelName)

        // 2. Launch Realtime Listener (in a separate coroutine so it doesn't block polling)
        val realtimeJob = launch {
            try {
                // Use postgresChangeFlow for cleaner Flow integration
                channel.postgresChangeFlow<PostgresAction.Update>(schema = "public") {
                    table = "delivery_partners"
                   "id=eq.$deliveryPartnerId"
                }.collect { change ->
                    // 'change' is strictly PostgresAction.Update here
                    val record = change.record // Access the new data

                    val lat = record["current_latitude"]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull()
                    val lng = record["current_longitude"]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull()

                    if (lat != null && lng != null) {
                        trySend(
                            DeliveryLocationUpdate(
                                deliveryPartnerId = deliveryPartnerId,
                                latitude = lat,
                                longitude = lng,
                                timestamp = System.currentTimeMillis()
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Realtime flow error", e)
            }
        }

        // 3. Connect the channel
        launch {
            try {
                channel.subscribe()
                Log.d(TAG, "Subscribed to realtime channel: $channelName")
            } catch (e: Exception) {
                Log.w(TAG, "Realtime subscription failed", e)
            }
        }

        // 4. Fallback Polling (Runs in parallel)
        val pollingJob = launch {
            while (isActive) { // Check if scope is active
                try {
                    val partner = getDeliveryPartner(deliveryPartnerId)
                    partner?.let {
                        if (it.currentLatitude != null && it.currentLongitude != null) {
                            trySend(
                                DeliveryLocationUpdate(
                                    deliveryPartnerId = it.id,
                                    latitude = it.currentLatitude,
                                    longitude = it.currentLongitude,
                                    timestamp = System.currentTimeMillis()
                                )
                            )
                        }
                    }
                    delay(5000)
                } catch (e: Exception) {
                    Log.e(TAG, "Error in polling", e)
                    delay(5000) // Wait before retrying on error
                }
            }
        }

        // 5. Cleanup when the flow collector stops listening
        awaitClose {
            Log.d(TAG, "Cleaning up subscription")
            launch(Dispatchers.IO) {
                try {
                    channel.unsubscribe()
                } catch (e: Exception) {
                    Log.e(TAG, "Error unsubscribing", e)
                }
            }
        }
    }

    /**
     * Get current location of delivery partner (one-time fetch)
     */
    suspend fun getCurrentLocation(deliveryPartnerId: String): DeliveryLocationUpdate? = withContext(Dispatchers.IO) {
        try {
            val partner = getDeliveryPartner(deliveryPartnerId)
            partner?.let {
                if (it.currentLatitude != null && it.currentLongitude != null) {
                    DeliveryLocationUpdate(
                        deliveryPartnerId = it.id,
                        latitude = it.currentLatitude,
                        longitude = it.currentLongitude,
                        timestamp = System.currentTimeMillis()
                    )
                } else null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting current location", e)
            null
        }
    }

    /**
     * Calculate estimated time of arrival (ETA) in minutes
     * Simple distance-based calculation (can be enhanced with routing API)
     */
    suspend fun calculateETA(
        partnerLat: Double,
        partnerLng: Double,
        destinationLat: Double,
        destinationLng: Double
    ): Int = withContext(Dispatchers.IO) {
        // Haversine formula to calculate distance
        val distance = calculateDistance(partnerLat, partnerLng, destinationLat, destinationLng)
        
        // Assume average speed of 30 km/h for delivery (adjust based on vehicle type)
        val averageSpeedKmh = 30.0
        val distanceKm = distance / 1000.0 // Convert meters to km
        val timeHours = distanceKm / averageSpeedKmh
        val timeMinutes = (timeHours * 60).toInt()
        
        // Add buffer time (5 minutes)
        maxOf(timeMinutes + 5, 5) // Minimum 5 minutes
    }

    /**
     * Calculate distance between two points using Haversine formula
     */
    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val earthRadius = 6371000.0 // meters
        
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2)
        
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        
        return earthRadius * c
    }

    suspend fun getRoutePoints(
        originLat: Double,
        originLng: Double,
        destLat: Double,
        destLng: Double,
        apiKey: String
    ): List<LatLng> = withContext(Dispatchers.IO) {
        // 1. Log the Input
        val origin = "$originLat,$originLng"
        val destination = "$destLat,$destLng"
        Log.d(TAG, "Fetching route: Origin[$origin] -> Dest[$destination]")

        try {
            val response = directionsApi.getDirections(origin, destination, apiKey)

            // 2. Log the API Status (Crucial)
            Log.d(TAG, "API Response Status: ${response.status}")
            Log.d(TAG, "Routes found: ${response.routes.size}")

            if (response.status == "OK" && response.routes.isNotEmpty()) {
                val encodedString = response.routes[0].overviewPolyline.points
                Log.d(TAG, "Route found! Decoding polyline...")
                return@withContext PolylineUtils.decodePolyline(encodedString)
            } else {
                // 3. Log specific failure reason
                Log.e(TAG, "Directions API Failed. Status: ${response.status}")
                if (response.routes.isEmpty()) {
                    Log.e(TAG, "Reason: Routes list is empty.")
                }
                // Common Status Codes to look for in Logcat:
                // REQUEST_DENIED -> Check API Key / Billing
                // OVER_QUERY_LIMIT -> Check Quota / Billing
                // ZERO_RESULTS -> No road exists between these points

                return@withContext emptyList()
            }
        } catch (e: Exception) {
            // 4. Log Network/Parsing Exceptions
            Log.e(TAG, "CRITICAL ERROR fetching directions", e)
            return@withContext emptyList()
        }
    }
}

