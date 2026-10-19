package com.example.blindobstacledetection.navigation.geocoding

import com.example.blindobstacledetection.navigation.models.GeocodedLocation
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

/**
 * Geocoding repository using OpenStreetMap Nominatim.
 *
 * Honors OpenStreetMap Nominatim Usage Policy:
 * - Includes a custom User-Agent identifying the application
 * - Requests only what is needed (limit=1)
 * - Free, open data with attribution
 */
class NominatimGeocodingRepository(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build(),
    private val gson: Gson = Gson()
) : GeocodingRepository {

    private val coordinateRegex = Pattern.compile(
        "^\\s*([+-]?\\d+(?:\\.\\d+)?)[,\\s]+([+-]?\\d+(?:\\.\\d+)?)\\s*$"
    )

    override suspend fun geocode(
        query: String,
        proximityLat: Double?,
        proximityLon: Double?
    ): Result<GeocodedLocation> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Destination query cannot be blank"))
        }

        // 1. Direct coordinate check (e.g. "11.0168, 76.9558")
        val coordMatcher = coordinateRegex.matcher(trimmed)
        if (coordMatcher.matches()) {
            val latStr = coordMatcher.group(1)
            val lonStr = coordMatcher.group(2)
            val lat = latStr?.toDoubleOrNull()
            val lon = lonStr?.toDoubleOrNull()
            if (lat != null && lon != null && lat in -90.0..90.0 && lon in -180.0..180.0) {
                return@withContext Result.success(
                    GeocodedLocation(
                        query = trimmed,
                        displayName = "Coordinates ($lat, $lon)",
                        latitude = lat,
                        longitude = lon,
                        provider = "DirectCoordinates"
                    )
                )
            }
        }

        // 2. Normalize query with South Indian / Tamil phonetic aliases
        val normalizedQuery = RegionalPlaceNormalizer.normalize(trimmed)

        // 3. OpenStreetMap Nominatim API request
        try {
            var items = executeSearch(normalizedQuery, proximityLat, proximityLon)

            // If no results and query was changed or didn't specify state, try with Tamil Nadu context
            if (items.isNullOrEmpty() && !normalizedQuery.contains("Tamil Nadu", ignoreCase = true)) {
                items = executeSearch("$normalizedQuery, Tamil Nadu", proximityLat, proximityLon)
            }

            if (items.isNullOrEmpty()) {
                return@withContext Result.failure(
                    NoSuchElementException("Could not locate \"$trimmed\". Please check the spelling or add a city name.")
                )
            }

            var chosenItem = items[0]
            var chosenLat = chosenItem.lat?.toDoubleOrNull()
            var chosenLon = chosenItem.lon?.toDoubleOrNull()

            // 4. Proximity Distance Validation:
            // For a walking app, if the matched location is > 60km away from user's GPS,
            // check if a regional qualifier (e.g. "$normalizedQuery, Tamil Nadu") yields a closer match.
            if (proximityLat != null && proximityLon != null && chosenLat != null && chosenLon != null) {
                val distance = calculateDistanceKm(proximityLat, proximityLon, chosenLat, chosenLon)
                if (distance > 60.0 && !normalizedQuery.contains("Tamil Nadu", ignoreCase = true)) {
                    val regionalQuery = if (isNearCoimbatore(proximityLat, proximityLon)) {
                        "$normalizedQuery, Coimbatore, Tamil Nadu"
                    } else {
                        "$normalizedQuery, Tamil Nadu"
                    }
                    val localItems = executeSearch(regionalQuery, proximityLat, proximityLon)
                    if (!localItems.isNullOrEmpty()) {
                        val localFirst = localItems[0]
                        val localLat = localFirst.lat?.toDoubleOrNull()
                        val localLon = localFirst.lon?.toDoubleOrNull()
                        if (localLat != null && localLon != null) {
                            val localDist = calculateDistanceKm(proximityLat, proximityLon, localLat, localLon)
                            if (localDist < distance) {
                                chosenItem = localFirst
                                chosenLat = localLat
                                chosenLon = localLon
                            }
                        }
                    }
                }
            }

            if (chosenLat == null || chosenLon == null || chosenLat !in -90.0..90.0 || chosenLon !in -180.0..180.0) {
                return@withContext Result.failure(
                    IllegalStateException("Geocoding service returned invalid coordinates for \"$trimmed\"")
                )
            }

            Result.success(
                GeocodedLocation(
                    query = trimmed,
                    displayName = chosenItem.displayName ?: trimmed,
                    latitude = chosenLat,
                    longitude = chosenLon,
                    provider = "OpenStreetMap Nominatim"
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun executeSearch(
        searchQuery: String,
        proximityLat: Double?,
        proximityLon: Double?
    ): List<NominatimItem>? {
        val encodedQuery = URLEncoder.encode(searchQuery, StandardCharsets.UTF_8.name())
        val urlBuilder = StringBuilder(
            "https://nominatim.openstreetmap.org/search?q=$encodedQuery&format=json&limit=1&addressdetails=1"
        )

        // Bias search towards user's current city/area if coordinates are known
        if (proximityLat != null && proximityLon != null) {
            val delta = 0.5 // ~50km bounding box
            val left = proximityLon - delta
            val top = proximityLat + delta
            val right = proximityLon + delta
            val bottom = proximityLat - delta
            urlBuilder.append("&viewbox=$left,$top,$right,$bottom&bounded=0")
        }

        val request = Request.Builder()
            .url(urlBuilder.toString())
            .header("User-Agent", "BlindObstacleDetection-AndroidApp/1.0 (assistive-navigation)")
            .header("Accept", "application/json")
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) return null

        val body = response.body?.string()
        if (body.isNullOrBlank()) return null

        val items = gson.fromJson(body, Array<NominatimItem>::class.java)
        return items?.toList()
    }

    private fun calculateDistanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        return r * c
    }

    private fun isNearCoimbatore(lat: Double, lon: Double): Boolean {
        // Coimbatore roughly 11.0° N, 76.96° E (+/- 0.6°)
        return lat in 10.4..11.6 && lon in 76.4..77.6
    }

    private data class NominatimItem(
        @SerializedName("lat") val lat: String?,
        @SerializedName("lon") val lon: String?,
        @SerializedName("display_name") val displayName: String?,
        @SerializedName("type") val type: String?
    )
}
