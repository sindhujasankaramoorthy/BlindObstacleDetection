package com.example.blindobstacledetection.navigation.models

/**
 * Geographic coordinate pair representing a point along a walking route.
 *
 * @param latitude Latitude in degrees [-90.0, 90.0]
 * @param longitude Longitude in degrees [-180.0, 180.0]
 */
data class RouteCoordinates(
    val latitude: Double,
    val longitude: Double
) {
    init {
        require(latitude in -90.0..90.0) { "Latitude must be between -90 and 90 degrees: $latitude" }
        require(longitude in -180.0..180.0) { "Longitude must be between -180 and 180 degrees: $longitude" }
    }
}
