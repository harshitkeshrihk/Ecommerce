package com.example.vishnu.viewModels

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.vishnu.BuildConfig
import com.example.vishnu.model.DeliveryAssignment
import com.example.vishnu.model.DeliveryLocationUpdate
import com.example.vishnu.model.DeliveryPartner
import com.example.vishnu.repository.DeliveryRepository
import com.google.android.gms.maps.model.LatLng
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TrackingViewModel @Inject constructor(
    private val deliveryRepository: DeliveryRepository
) : ViewModel() {

    private val TAG = "TrackingViewModel"

    private val _deliveryAssignment = MutableStateFlow<DeliveryAssignment?>(null)
    val deliveryAssignment: StateFlow<DeliveryAssignment?> = _deliveryAssignment.asStateFlow()

    private val _deliveryPartner = MutableStateFlow<DeliveryPartner?>(null)
    val deliveryPartner: StateFlow<DeliveryPartner?> = _deliveryPartner.asStateFlow()

    private val _currentLocation = MutableStateFlow<DeliveryLocationUpdate?>(null)
    val currentLocation: StateFlow<DeliveryLocationUpdate?> = _currentLocation.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _etaMinutes = MutableStateFlow<Int?>(null)
    val etaMinutes: StateFlow<Int?> = _etaMinutes.asStateFlow()

    private val _routePath = MutableStateFlow<List<LatLng>>(emptyList())
    val routePath: StateFlow<List<LatLng>> = _routePath.asStateFlow()

    private var isRouteFetched = false


    private var locationSubscriptionJob: Job? = null

    /**
     * Initialize tracking for an order
     */
    fun initializeTracking(orderId: Long) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            try {
                // 1. Get delivery assignment
                val assignment = deliveryRepository.getDeliveryAssignment(orderId)
                if (assignment == null) {
                    _error.value = "No delivery partner assigned yet"
                    _isLoading.value = false
                    return@launch
                }

                _deliveryAssignment.value = assignment

                // 2. Get delivery partner details
                val partner = deliveryRepository.getDeliveryPartner(assignment.deliveryPartnerId)
                if (partner == null) {
                    _error.value = "Delivery partner not found"
                    _isLoading.value = false
                    return@launch
                }

                _deliveryPartner.value = partner

                // 3. Get initial location
                val initialLocation = deliveryRepository.getCurrentLocation(partner.id)
                _currentLocation.value = initialLocation

                // 4. Subscribe to real-time updates
                subscribeToLocationUpdates(partner.id)

                // 5. Calculate ETA if we have destination coordinates
                if (assignment.deliveryLatitude != null && assignment.deliveryLongitude != null &&
                    initialLocation != null
                ) {
                    if(!isRouteFetched){
                        fetchRoute(
                            initialLocation.latitude,
                            initialLocation.longitude,
                            assignment.deliveryLatitude,
                            assignment.deliveryLongitude
                        )
                    }else{
                        Log.d(TAG, "Route already fetched, skipping API call to save cost.")
                    }
                    calculateETA(
                        initialLocation.latitude,
                        initialLocation.longitude,
                        assignment.deliveryLatitude,
                        assignment.deliveryLongitude
                    )
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error initializing tracking", e)
                _error.value = "Failed to initialize tracking: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Subscribe to real-time location updates
     */
    private fun subscribeToLocationUpdates(deliveryPartnerId: String) {
        // Cancel previous subscription if any
        locationSubscriptionJob?.cancel()

        locationSubscriptionJob = deliveryRepository.subscribeToLocationUpdates(deliveryPartnerId)
            .onEach { locationUpdate ->
                Log.d(TAG, "Location update received: ${locationUpdate.latitude}, ${locationUpdate.longitude}")
                _currentLocation.value = locationUpdate

                // Recalculate ETA when location updates
                _deliveryAssignment.value?.let { assignment ->
                    if (assignment.deliveryLatitude != null && assignment.deliveryLongitude != null) {
                        calculateETA(
                            locationUpdate.latitude,
                            locationUpdate.longitude,
                            assignment.deliveryLatitude,
                            assignment.deliveryLongitude
                        )
                    }
                }
            }
            .catch { e ->
                Log.e(TAG, "Error in location subscription", e)
                _error.value = "Connection error: ${e.message}"
            }
            .launchIn(viewModelScope)
    }

    /**
     * Calculate ETA
     */
    private fun calculateETA(
        partnerLat: Double,
        partnerLng: Double,
        destinationLat: Double,
        destinationLng: Double
    ) {
        viewModelScope.launch {
            try {
                val eta = deliveryRepository.calculateETA(
                    partnerLat,
                    partnerLng,
                    destinationLat,
                    destinationLng
                )
                _etaMinutes.value = eta
            } catch (e: Exception) {
                Log.e(TAG, "Error calculating ETA", e)
            }
        }
    }

    /**
     * Refresh location manually
     */
    fun refreshLocation() {
        _deliveryPartner.value?.let { partner ->
            viewModelScope.launch {
                try {
                    val location = deliveryRepository.getCurrentLocation(partner.id)
                    _currentLocation.value = location
                } catch (e: Exception) {
                    Log.e(TAG, "Error refreshing location", e)
                    _error.value = "Failed to refresh location"
                }
            }
        }
    }

    private fun fetchRoute(
        startLat: Double,
        startLng: Double,
        endLat: Double,
        endLng: Double
    ) {
        viewModelScope.launch {
            try {
                val points = deliveryRepository.getRoutePoints(
                    startLat, startLng, endLat, endLng, BuildConfig.MAPS_API_KEY
                )
                if (points.isNotEmpty()) {
                    isRouteFetched = true
                    _routePath.value = points
                    Log.d(TAG, "Directions API called successfully.")
                }
            }catch (e : Exception){
                Log.e(TAG, "Error fetching route", e)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        locationSubscriptionJob?.cancel()
    }
}

