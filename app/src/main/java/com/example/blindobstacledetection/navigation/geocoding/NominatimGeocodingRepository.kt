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

        // 2. OpenStreetMap Nominatim API request
        try {
            val encodedQuery = URLEncoder.encode(trimmed, StandardCharsets.UTF_8.name())
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
            if (!response.isSuccessful) {
                return@withContext Result.failure(
                    IOException("Geocoding service returned HTTP ${response.code}: ${response.message}")
                )
            }

            val body = response.body?.string()
            if (body.isNullOrBlank()) {
                return@withContext Result.failure(
                    IOException("Empty response from geocoding service")
                )
            }

            val items = gson.fromJson(body, Array<NominatimItem>::class.java)
            if (items.isNullOrEmpty()) {
                return@withContext Result.failure(
                    NoSuchElementException("Could not locate \"$trimmed\". Please check the spelling or add a city name.")
                )
            }

            val first = items[0]
            val lat = first.lat?.toDoubleOrNull()
            val lon = first.lon?.toDoubleOrNull()

            if (lat == null || lon == null || lat !in -90.0..90.0 || lon !in -180.0..180.0) {
                return@withContext Result.failure(
                    IllegalStateException("Geocoding service returned invalid coordinates for \"$trimmed\"")
                )
            }

            Result.success(
                GeocodedLocation(
                    query = trimmed,
                    displayName = first.displayName ?: trimmed,
                    latitude = lat,
                    longitude = lon,
                    provider = "OpenStreetMap Nominatim"
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private data class NominatimItem(
        @SerializedName("lat") val lat: String?,
        @SerializedName("lon") val lon: String?,
        @SerializedName("display_name") val displayName: String?,
        @SerializedName("type") val type: String?
    )
}
