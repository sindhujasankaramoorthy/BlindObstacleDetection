package com.example.blindobstacledetection.navigation.models

/**
 * Resolved geographic location from a destination query.
 *
 * @param query Original search query / destination name.
 * @param displayName Formatted address or place name returned by the geocoder.
 * @param latitude Geographic latitude [-90.0, 90.0].
 * @param longitude Geographic longitude [-180.0, 180.0].
 * @param provider Geocoding provider used (e.g. "Nominatim", "OpenRouteService", "DirectCoordinates").
 */
data class GeocodedLocation(
    val query: String,
    val displayName: String,
    val latitude: Double,
    val longitude: Double,
    val provider: String = "Nominatim"
) {
    init {
        require(latitude in -90.0..90.0) { "Invalid latitude: $latitude" }
        require(longitude in -180.0..180.0) { "Invalid longitude: $longitude" }
    }

    /**
     * Short display name suitable for UI headers and screen reader announcements.
     */
    val shortName: String
        get() = displayName.split(",").firstOrNull()?.trim() ?: query
}
