package com.example.vishnu.utils


import android.annotation.SuppressLint
import android.content.Context
import android.location.Geocoder
import android.location.Location
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.tasks.await
import java.util.Locale

class LocationManager(private val context: Context) {

    private val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)

    @SuppressLint("MissingPermission") // We will check this in UI before calling
    suspend fun getCurrentAddress(): String? {
        return try {
            // 1. Get Coordinates
            val location: Location? = fusedLocationClient.getCurrentLocation(
                Priority.PRIORITY_HIGH_ACCURACY,
                null
            ).await()

            if (location != null) {
                // 2. Geocoding (Lat/Lng -> Address String)
                val geocoder = Geocoder(context, Locale.getDefault())
                // Basic geocoding for older APIs (For newer Android 13+, there's a listener approach, but this works)
                val addresses = geocoder.getFromLocation(location.latitude, location.longitude, 1)

                if (!addresses.isNullOrEmpty()) {
                    val address = addresses[0]
                    // Create a string like: "Sector 21, Gurugram"
                    val locality = address.subLocality ?: address.locality
                    val city = address.adminArea ?: ""
                    "$locality, $city"
                } else {
                    "Unknown Location"
                }
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}