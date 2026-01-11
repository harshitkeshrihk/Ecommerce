package com.example.vishnu.model

import com.google.gson.annotations.SerializedName

data class DirectionsResponse(
    @SerializedName("routes") val routes: List<Route>,
    @SerializedName("status")
    val status: String,

    // Optional: Error message if status is not OK
    @SerializedName("error_message")
    val errorMessage: String? = null
)


data class Route(
    @SerializedName("overview_polyline") val overviewPolyline: OverviewPolyline
)

data class OverviewPolyline(
    @SerializedName("points") val points: String
)