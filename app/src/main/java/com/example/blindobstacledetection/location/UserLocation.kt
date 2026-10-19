package com.example.blindobstacledetection.location

/**
 * In-memory representation of the user's acquired geographic location.
 * Kept in memory only without persistent database storage for privacy and efficiency.
 *
 * @param latitude Geographic latitude in degrees (-90.0 to 90.0).
 * @param longitude Geographic longitude in degrees (-180.0 to 180.0).
 * @param accuracyMeters Radial horizontal accuracy in meters (68% confidence level).
 * @param timestamp Time of location fix in milliseconds since Unix epoch.
 * @param provider Name of the provider that generated the fix (e.g. "fused", "gps", "network").
 * @param isMock True if the location was provided by a mock provider.
 */
data class UserLocation(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val provider: String? = "fused",
    val isMock: Boolean = false
) {
    /**
     * Location is considered accurate enough for walking navigation if accuracy is within 30 meters.
     */
    val isAccurate: Boolean
        get() = (accuracyMeters ?: Float.MAX_VALUE) <= 30f

    /**
     * Location is considered approximate if only coarse location or accuracy > 100 meters was provided.
     */
    val isApproximate: Boolean
        get() = (accuracyMeters ?: Float.MAX_VALUE) > 100f

    /**
     * Elapsed time in seconds since the location fix was generated.
     */
    val freshnessSeconds: Long
        get() = (System.currentTimeMillis() - timestamp).coerceAtLeast(0L) / 1000L

    /**
     * Formats accuracy for assistive UI display without reading out coordinate numbers.
     */
    val accuracyDescription: String
        get() = when {
            accuracyMeters == null -> "Accuracy unknown"
            accuracyMeters <= 10f -> "High accuracy (±${accuracyMeters.toInt()}m)"
            accuracyMeters <= 30f -> "Good accuracy (±${accuracyMeters.toInt()}m)"
            else -> "Approximate location (±${accuracyMeters.toInt()}m)"
        }
}
