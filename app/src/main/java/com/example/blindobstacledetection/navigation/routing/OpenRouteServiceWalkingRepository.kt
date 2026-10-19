package com.example.blindobstacledetection.navigation.routing

import com.example.blindobstacledetection.navigation.NavigationConfig
import com.example.blindobstacledetection.navigation.models.RouteCoordinates
import com.example.blindobstacledetection.navigation.models.RouteStep
import com.example.blindobstacledetection.navigation.models.WalkingRoute
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Concrete implementation of [WalkingRouteRepository] using OpenRouteService's dedicated
 * pedestrian profile ("foot-walking").
 *
 * Guarantees real pedestrian routing (sidewalks, footpaths, pedestrian crossings).
 * Does NOT substitute or label driving routes as walking routes.
 */
class OpenRouteServiceWalkingRepository(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build(),
    private val gson: Gson = Gson(),
    private val apiKeyProvider: () -> String = { NavigationConfig.apiKey }
) : WalkingRouteRepository {

    override suspend fun calculateWalkingRoute(
        originLat: Double,
        originLon: Double,
        destLat: Double,
        destLon: Double,
        destinationName: String
    ): Result<WalkingRoute> = withContext(Dispatchers.IO) {
        // 1. Validate coordinates
        if (originLat !in -90.0..90.0 || originLon !in -180.0..180.0) {
            return@withContext Result.failure(
                IllegalArgumentException("Invalid origin coordinates ($originLat, $originLon)")
            )
        }
        if (destLat !in -90.0..90.0 || destLon !in -180.0..180.0) {
            return@withContext Result.failure(
                IllegalArgumentException("Invalid destination coordinates ($destLat, $destLon)")
            )
        }

        // 2. Validate API key
        val apiKey = apiKeyProvider().trim()
        if (apiKey.isBlank()) {
            return@withContext Result.failure(
                MissingApiKeyException(
                    "OpenRouteService API key not configured. Add ORS_API_KEY in local.properties or enter key in app to calculate walking routes."
                )
            )
        }

        try {
            // ORS expects GeoJSON [longitude, latitude] coordinates
            val requestPayload = OrsDirectionRequest(
                coordinates = listOf(
                    listOf(originLon, originLat),
                    listOf(destLon, destLat)
                ),
                instructions = true,
                preference = "recommended"
            )

            val jsonBody = gson.toJson(requestPayload)
            val requestBody = jsonBody.toRequestBody("application/json; charset=utf-8".toMediaType())

            val request = Request.Builder()
                .url("https://api.openrouteservice.org/v2/directions/foot-walking/geojson")
                .header("Authorization", apiKey)
                .header("Accept", "application/json, application/geo+json")
                .header("Content-Type", "application/json; charset=utf-8")
                .post(requestBody)
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string().orEmpty()

            if (!response.isSuccessful) {
                return@withContext when (response.code) {
                    401, 403 -> Result.failure(
                        InvalidApiKeyException("Invalid OpenRouteService API key. Please check your API key.")
                    )
                    429 -> Result.failure(
                        RateLimitException("OpenRouteService rate limit reached. Please wait a minute before retrying.")
                    )
                    400, 404 -> {
                        val errorMsg = parseErrorMessage(responseBody)
                            ?: "No pedestrian walking route found between these locations."
                        Result.failure(RouteNotFoundException(errorMsg))
                    }
                    else -> Result.failure(
                        IOException("Routing service returned HTTP ${response.code}: ${response.message}")
                    )
                }
            }

            if (responseBody.isBlank()) {
                return@withContext Result.failure(
                    IOException("Empty response received from routing service")
                )
            }

            parseGeoJson(
                json = responseBody,
                originLat = originLat,
                originLon = originLon,
                destLat = destLat,
                destLon = destLon,
                destinationName = destinationName
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseErrorMessage(json: String): String? {
        return try {
            val errorResponse = gson.fromJson(json, OrsErrorResponse::class.java)
            errorResponse?.error?.message ?: errorResponse?.message
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Parses the ORS GeoJSON response into a clean, immutable [WalkingRoute].
     */
    fun parseGeoJson(
        json: String,
        originLat: Double,
        originLon: Double,
        destLat: Double,
        destLon: Double,
        destinationName: String
    ): Result<WalkingRoute> {
        val featureCollection = try {
            gson.fromJson(json, OrsFeatureCollection::class.java)
        } catch (e: Exception) {
            return Result.failure(IllegalStateException("Malformed route response: ${e.localizedMessage}"))
        }

        val feature = featureCollection?.features?.firstOrNull()
            ?: return Result.failure(RouteNotFoundException("No route features found in response"))

        // Extract geometry coordinates: ORS gives [lon, lat]
        val geometryPoints = feature.geometry?.coordinates?.mapNotNull { point ->
            if (point.size >= 2) {
                val lon = point[0]
                val lat = point[1]
                if (lat in -90.0..90.0 && lon in -180.0..180.0) {
                    RouteCoordinates(latitude = lat, longitude = lon)
                } else null
            } else null
        } ?: emptyList()

        val summary = feature.properties?.summary
        val totalDistance = summary?.distance ?: 0.0
        val totalDuration = summary?.duration ?: 0.0

        val segment = feature.properties?.segments?.firstOrNull()
        val rawSteps = segment?.steps ?: emptyList()

        val routeSteps = rawSteps.mapIndexed { index, step ->
            RouteStep(
                stepIndex = index + 1,
                instruction = step.instruction?.takeIf { it.isNotBlank() } ?: "Proceed along walking path",
                distanceMeters = step.distance ?: 0.0,
                durationSeconds = step.duration ?: 0.0,
                maneuverType = step.type ?: 11,
                streetName = step.name?.takeIf { it != "-" }.orEmpty(),
                wayPointStart = step.wayPoints?.getOrNull(0) ?: 0,
                wayPointEnd = step.wayPoints?.getOrNull(1) ?: 0
            )
        }

        return Result.success(
            WalkingRoute(
                originLatitude = originLat,
                originLongitude = originLon,
                destinationLatitude = destLat,
                destinationLongitude = destLon,
                destinationName = destinationName,
                totalDistanceMeters = totalDistance,
                totalDurationSeconds = totalDuration,
                steps = routeSteps,
                geometry = geometryPoints,
                timestamp = System.currentTimeMillis(),
                provider = "OpenRouteService foot-walking"
            )
        )
    }

    // --- JSON Transfer Models ---

    private data class OrsDirectionRequest(
        @SerializedName("coordinates") val coordinates: List<List<Double>>,
        @SerializedName("instructions") val instructions: Boolean = true,
        @SerializedName("preference") val preference: String = "recommended"
    )

    private data class OrsFeatureCollection(
        @SerializedName("features") val features: List<OrsFeature>?
    )

    private data class OrsFeature(
        @SerializedName("geometry") val geometry: OrsGeometry?,
        @SerializedName("properties") val properties: OrsProperties?
    )

    private data class OrsGeometry(
        @SerializedName("coordinates") val coordinates: List<List<Double>>?
    )

    private data class OrsProperties(
        @SerializedName("summary") val summary: OrsSummary?,
        @SerializedName("segments") val segments: List<OrsSegment>?
    )

    private data class OrsSummary(
        @SerializedName("distance") val distance: Double?,
        @SerializedName("duration") val duration: Double?
    )

    private data class OrsSegment(
        @SerializedName("distance") val distance: Double?,
        @SerializedName("duration") val duration: Double?,
        @SerializedName("steps") val steps: List<OrsStep>?
    )

    private data class OrsStep(
        @SerializedName("distance") val distance: Double?,
        @SerializedName("duration") val duration: Double?,
        @SerializedName("type") val type: Int?,
        @SerializedName("instruction") val instruction: String?,
        @SerializedName("name") val name: String?,
        @SerializedName("way_points") val wayPoints: List<Int>?
    )

    private data class OrsErrorResponse(
        @SerializedName("error") val error: OrsErrorDetail?,
        @SerializedName("message") val message: String?
    )

    private data class OrsErrorDetail(
        @SerializedName("code") val code: Int?,
        @SerializedName("message") val message: String?
    )
}
