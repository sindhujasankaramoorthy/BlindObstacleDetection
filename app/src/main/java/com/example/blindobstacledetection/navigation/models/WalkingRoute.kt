package com.example.blindobstacledetection.navigation.models

import kotlin.math.roundToInt

/**
 * Complete pedestrian walking route generated from the user's actual GPS location
 * to the confirmed destination.
 *
 * @param originLatitude Starting latitude in degrees.
 * @param originLongitude Starting longitude in degrees.
 * @param destinationLatitude Destination latitude in degrees.
 * @param destinationLongitude Destination longitude in degrees.
 * @param destinationName Human-readable name of the destination.
 * @param totalDistanceMeters Total walking distance in meters.
 * @param totalDurationSeconds Estimated walking duration in seconds.
 * @param steps Ordered list of turn-by-turn navigation steps.
 * @param geometry Ordered list of geographic coordinates forming the walking path.
 * @param timestamp Milliseconds epoch timestamp when this route was calculated.
 * @param provider Provider identifier (e.g. "OpenRouteService foot-walking").
 */
data class WalkingRoute(
    val originLatitude: Double,
    val originLongitude: Double,
    val destinationLatitude: Double,
    val destinationLongitude: Double,
    val destinationName: String,
    val totalDistanceMeters: Double,
    val totalDurationSeconds: Double,
    val steps: List<RouteStep>,
    val geometry: List<RouteCoordinates>,
    val timestamp: Long = System.currentTimeMillis(),
    val provider: String = "OpenRouteService foot-walking"
) {
    /**
     * Formatted total distance for display and speech.
     */
    val formattedDistance: String
        get() = when {
            totalDistanceMeters < 1000.0 -> "${totalDistanceMeters.roundToInt()} meters"
            else -> "%.1f km".format(totalDistanceMeters / 1000.0)
        }

    /**
     * Formatted estimated duration for display and speech.
     */
    val formattedDuration: String
        get() = when {
            totalDurationSeconds < 60.0 -> "under a minute"
            totalDurationSeconds < 3600.0 -> "${(totalDurationSeconds / 60.0).roundToInt()} minutes"
            else -> {
                val hours = (totalDurationSeconds / 3600.0).toInt()
                val mins = ((totalDurationSeconds % 3600.0) / 60.0).roundToInt()
                if (mins == 0) "$hours hours" else "$hours hours $mins minutes"
            }
        }

    /**
     * Concise summary text for screen reader announcements.
     */
    val speechSummary: String
        get() = "Walking route to $destinationName: $formattedDistance, approximately $formattedDuration, across ${steps.size} steps."

    /**
     * Summary text for UI chip display (e.g. "1.2 km • ~15 mins").
     */
    val uiSummary: String
        get() = "$formattedDistance • ~$formattedDuration"
}
