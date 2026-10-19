package com.example.blindobstacledetection.navigation.routing

import com.example.blindobstacledetection.navigation.models.WalkingRoute

/**
 * Custom exceptions for navigation error handling and user messaging.
 */
class MissingApiKeyException(message: String) : Exception(message)
class InvalidApiKeyException(message: String) : Exception(message)
class RateLimitException(message: String) : Exception(message)
class RouteNotFoundException(message: String) : Exception(message)

/**
 * Interface for pedestrian walking route calculations.
 * Enables switching between routing providers (OpenRouteService, OSRM, GraphHopper, etc.)
 * without modifying UI or ViewModel layers.
 */
interface WalkingRouteRepository {

    /**
     * Calculates a pedestrian walking route from origin coordinates to destination coordinates.
     *
     * @param originLat Latitude of origin (user's current GPS location).
     * @param originLon Longitude of origin (user's current GPS location).
     * @param destLat Latitude of destination.
     * @param destLon Longitude of destination.
     * @param destinationName Human-readable name of destination.
     * @return Result containing [WalkingRoute] on success, or a descriptive exception on failure.
     */
    suspend fun calculateWalkingRoute(
        originLat: Double,
        originLon: Double,
        destLat: Double,
        destLon: Double,
        destinationName: String
    ): Result<WalkingRoute>
}
