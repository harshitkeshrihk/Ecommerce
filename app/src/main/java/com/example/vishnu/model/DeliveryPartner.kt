package com.example.vishnu.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Delivery Partner Model
 * Represents a delivery partner who can deliver orders
 */
@Serializable
data class DeliveryPartner(
    val id: String,
    @SerialName("user_id")
    val userId: String? = null, // Links to auth.users if delivery partner has account
    val name: String,
    val phone: String,
    @SerialName("vehicle_type")
    val vehicleType: String = "BIKE", // BIKE, SCOOTER, CAR
    @SerialName("is_available")
    val isAvailable: Boolean = true,
    @SerialName("current_latitude")
    val currentLatitude: Double? = null,
    @SerialName("current_longitude")
    val currentLongitude: Double? = null,
    @SerialName("last_updated")
    val lastUpdated: String? = null,
    @SerialName("created_at")
    val createdAt: String? = null
)

/**
 * Real-time Location Update
 * Used for live tracking updates via WebSocket/Realtime
 */
@Serializable
data class DeliveryLocationUpdate(
    @SerialName("delivery_partner_id")
    val deliveryPartnerId: String,
    val latitude: Double,
    val longitude: Double,
    val timestamp: Long = System.currentTimeMillis(),
    val bearing: Float? = null, // Direction in degrees (0-360)
    val speed: Float? = null // Speed in m/s
)

/**
 * Order Delivery Assignment
 * Links an order to a delivery partner
 */
@Serializable
data class DeliveryAssignment(
    val id: String?=null,
    @SerialName("order_id")
    val orderId: Long,
    @SerialName("delivery_partner_id")
    val deliveryPartnerId: String,
    @SerialName("assigned_at")
    val assignedAt: String,
    @SerialName("estimated_arrival")
    val estimatedArrival: String? = null, // ISO timestamp
    @SerialName("delivery_status")
    val deliveryStatus: String = "ASSIGNED", // ASSIGNED, PICKED_UP, IN_TRANSIT, DELIVERED
    @SerialName("pickup_latitude")
    val pickupLatitude: Double? = null,
    @SerialName("pickup_longitude")
    val pickupLongitude: Double? = null,
    @SerialName("delivery_latitude")
    val deliveryLatitude: Double? = null,
    @SerialName("delivery_longitude")
    val deliveryLongitude: Double? = null
)

