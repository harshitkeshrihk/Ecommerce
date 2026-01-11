package com.example.vishnu.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.vishnu.viewModels.TrackingViewModel
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.JointType
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.RoundCap
import com.google.maps.android.compose.*
import kotlin.math.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveTrackingScreen(
    orderId: Long,
    onBack: () -> Unit,
    viewModel: TrackingViewModel = hiltViewModel()
) {
    val deliveryAssignment by viewModel.deliveryAssignment.collectAsState()
    val deliveryPartner by viewModel.deliveryPartner.collectAsState()
    val currentLocation by viewModel.currentLocation.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val etaMinutes by viewModel.etaMinutes.collectAsState()
    val routePath by viewModel.routePath.collectAsState()

    // Initialize tracking when screen loads
    LaunchedEffect(orderId) {
        viewModel.initializeTracking(orderId)
    }

    // Default location (can be set to user's location or store location)
    val defaultLocation = LatLng(28.7041, 77.1025) // Default to Delhi
    val deliveryLocation = remember {
        mutableStateOf<LatLng?>(null)
    }
    val partnerLocation = remember {
        mutableStateOf<LatLng?>(null)
    }

    // Update locations when data changes
    LaunchedEffect(currentLocation) {
        currentLocation?.let {
            partnerLocation.value = LatLng(it.latitude, it.longitude)
        }
    }

    LaunchedEffect(deliveryAssignment) {
        deliveryAssignment?.let { assignment ->
            if (assignment.deliveryLatitude != null && assignment.deliveryLongitude != null) {
                deliveryLocation.value = LatLng(
                    assignment.deliveryLatitude,
                    assignment.deliveryLongitude
                )
            }
        }
    }

    // Camera position - center between partner and delivery location, or show partner
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(
            deliveryLocation.value ?: partnerLocation.value ?: defaultLocation,
            15f
        )
    }

    // Update camera when locations change
    LaunchedEffect(partnerLocation.value, deliveryLocation.value) {
        val partner = partnerLocation.value
        val delivery = deliveryLocation.value

        when {
            partner != null && delivery != null -> {
                // Center between both points
                val centerLat = (partner.latitude + delivery.latitude) / 2
                val centerLng = (partner.longitude + delivery.longitude) / 2
                val center = LatLng(centerLat, centerLng)
                
                // Calculate zoom to show both points
                val distance = calculateDistance(
                    partner.latitude, partner.longitude,
                    delivery.latitude, delivery.longitude
                )
                val zoom = when {
                    distance > 5000 -> 12f
                    distance > 2000 -> 13f
                    distance > 1000 -> 14f
                    else -> 15f
                }
                
                cameraPositionState.animate(
                    CameraUpdateFactory.newCameraPosition(
                        CameraPosition(center, zoom, 0f, 0f)
                    )
                )
            }
            partner != null -> {
                cameraPositionState.animate(
                    CameraUpdateFactory.newCameraPosition(
                        CameraPosition(partner, 15f, 0f, 0f)
                    )
                )
            }
            delivery != null -> {
                cameraPositionState.animate(
                    CameraUpdateFactory.newCameraPosition(
                        CameraPosition(delivery, 15f, 0f, 0f)
                    )
                )
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Live Tracking") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refreshLocation() }) {
                        Icon(Icons.Default.Refresh, "Refresh")
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                error != null -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Text(
                                text = "Tracking Unavailable",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = error ?: "Unknown error",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(onClick = { viewModel.initializeTracking(orderId) }) {
                                Text("Retry")
                            }
                        }
                    }
                }
                else -> {
                    // Map View
                    GoogleMap(
                        modifier = Modifier.fillMaxSize(),
                        cameraPositionState = cameraPositionState,
                        properties = MapProperties(
                            isMyLocationEnabled = false,
                            mapType = MapType.NORMAL
                        ),
                        uiSettings = MapUiSettings(
                            zoomControlsEnabled = true,
                            myLocationButtonEnabled = false,
                            compassEnabled = true
                        )
                    ) {
                        // Delivery location marker (destination)
                        deliveryLocation.value?.let { location ->
                            Marker(
                                state = MarkerState(position = location),
                                title = "Delivery Address",
                                snippet = "Your order will be delivered here"
                            )
                        }

                        // Delivery partner marker (current location)
                        partnerLocation.value?.let { location ->
                            Marker(
                                state = MarkerState(position = location),
                                title = deliveryPartner?.name ?: "Delivery Partner",
                                snippet = "On the way"
                            )
                        }

                        // Draw route line if both locations are available
                        if(routePath.isNotEmpty()){
                            Polyline(
                                points = routePath,
                                color = Color(0xFF2196F3),
                                width = 15f,
                                jointType = JointType.ROUND,
                                startCap = RoundCap(),
                                endCap = RoundCap()
                            )
                        }else{
                            partnerLocation.value?.let { partner ->
                                deliveryLocation.value?.let { delivery ->
                                    Polyline(
                                        points = listOf(partner, delivery),
                                        color = Color(0xFF2196F3),
                                        width = 8f
                                    )
                                }
                            }
                        }

                    }

                    // Bottom Info Card
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .padding(16.dp),
                        shape = RoundedCornerShape(16.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            // Delivery Partner Info
                            deliveryPartner?.let { partner ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Delivery Partner",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = Color.Gray
                                        )
                                        Text(
                                            text = partner.name,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = partner.phone,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color.Gray
                                        )
                                    }
                                    if (partner.vehicleType.isNotEmpty()) {
                                        Surface(
                                            color = MaterialTheme.colorScheme.primaryContainer,
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Text(
                                                text = partner.vehicleType,
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))
                            Divider()
                            Spacer(modifier = Modifier.height(16.dp))

                            // ETA and Status
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "Estimated Arrival",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = Color.Gray
                                    )
                                    etaMinutes?.let { eta ->
                                        Text(
                                            text = "$eta minutes",
                                            style = MaterialTheme.typography.titleLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    } ?: Text(
                                        text = "Calculating...",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color.Gray
                                    )
                                }

                                // Status Badge
                                deliveryAssignment?.let { assignment ->
                                    Surface(
                                        color = when (assignment.deliveryStatus) {
                                            "IN_TRANSIT" -> Color(0xFF2196F3)
                                            "PICKED_UP" -> Color(0xFFFF9800)
                                            "DELIVERED" -> Color(0xFF4CAF50)
                                            else -> Color(0xFF9E9E9E)
                                        },
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(
                                            text = assignment.deliveryStatus.replace("_", " "),
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    }
                                }
                            }

                            // Last update time
                            currentLocation?.let { location ->
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Last updated: ${formatTimestamp(location.timestamp)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.Gray
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// Helper function to calculate distance
private fun calculateDistance(
    lat1: Double, lon1: Double,
    lat2: Double, lon2: Double
): Double {
    val earthRadius = 6371000.0 // meters
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLon / 2) * sin(dLon / 2)
    val c = 2 * atan2(sqrt(a), sqrt(1 - a))
    return earthRadius * c
}

// Helper function to format timestamp
private fun formatTimestamp(timestamp: Long): String {
    val seconds = (System.currentTimeMillis() - timestamp) / 1000
    return when {
        seconds < 60 -> "Just now"
        seconds < 3600 -> "${seconds / 60} minutes ago"
        else -> "${seconds / 3600} hours ago"
    }
}

