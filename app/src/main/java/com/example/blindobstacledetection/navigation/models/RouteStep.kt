package com.example.blindobstacledetection.navigation.models

import kotlin.math.roundToInt

/**
 * An individual turn, transition, or leg of a walking route.
 * Preserves structured maneuver data for future Phase 20 navigation announcements.
 *
 * @param stepIndex 1-based sequential index of the step in the route.
 * @param instruction Human-readable walking direction (e.g. "Turn left onto Church Street").
 * @param distanceMeters Walking distance of this segment in meters.
 * @param durationSeconds Estimated walking duration of this segment in seconds.
 * @param maneuverType Action code representing the maneuver (aligned with ORS action types).
 * @param streetName Name of the street, path, or way if known.
 * @param wayPointStart Index into the route geometry coordinate array where this step begins.
 * @param wayPointEnd Index into the route geometry coordinate array where this step ends.
 */
data class RouteStep(
    val stepIndex: Int,
    val instruction: String,
    val distanceMeters: Double,
    val durationSeconds: Double,
    val maneuverType: Int = 11, // Default 11 = Depart / Continue
    val streetName: String = "",
    val wayPointStart: Int = 0,
    val wayPointEnd: Int = 0
) {
    /**
     * Human-friendly formatted distance for display.
     */
    val formattedDistance: String
        get() = when {
            distanceMeters < 5.0 -> "Immediately"
            distanceMeters < 1000.0 -> "${distanceMeters.roundToInt()} m"
            else -> "%.1f km".format(distanceMeters / 1000.0)
        }

    /**
     * Human-friendly formatted duration for display.
     */
    val formattedDuration: String
        get() = when {
            durationSeconds < 60.0 -> "< 1 min"
            durationSeconds < 3600.0 -> "${(durationSeconds / 60.0).roundToInt()} min"
            else -> {
                val hours = (durationSeconds / 3600.0).toInt()
                val mins = ((durationSeconds % 3600.0) / 60.0).roundToInt()
                "${hours}h ${mins}m"
            }
        }

    /**
     * Accessible text formatted specifically for Android TalkBack / Screen Readers.
     */
    val accessibleText: String
        get() = "Step $stepIndex: $instruction, for $formattedDistance, approximately $formattedDuration."

    /**
     * Human-readable maneuver classification name.
     */
    val maneuverName: String
        get() = when (maneuverType) {
            0 -> "Turn Left"
            1 -> "Turn Right"
            2 -> "Sharp Left"
            3 -> "Sharp Right"
            4 -> "Slight Left"
            5 -> "Slight Right"
            6 -> "Continue Straight"
            7 -> "Enter Roundabout"
            8 -> "Exit Roundabout"
            9 -> "Make U-Turn"
            10 -> "Arrive at Destination"
            11 -> "Depart / Head"
            12 -> "Keep Left"
            13 -> "Keep Right"
            else -> "Walk"
        }
}
