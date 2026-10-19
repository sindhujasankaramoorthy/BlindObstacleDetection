package com.example.blindobstacledetection.navigation.geocoding

import com.example.blindobstacledetection.navigation.models.GeocodedLocation

/**
 * Interface for converting destination queries (place names, addresses, landmarks, or coordinates)
 * into validated geographic coordinates.
 */
interface GeocodingRepository {

    /**
     * Resolves a destination query into geographic coordinates.
     *
     * @param query Destination place name (e.g. "Gandhipuram Bus Stand", "Central Railway Station", "11.0168, 76.9558").
     * @param proximityLat Optional user current latitude to bias search results.
     * @param proximityLon Optional user current longitude to bias search results.
     * @return Result containing [GeocodedLocation] on success, or a descriptive exception on failure.
     */
    suspend fun geocode(
        query: String,
        proximityLat: Double? = null,
        proximityLon: Double? = null
    ): Result<GeocodedLocation>
}
