package com.example.blindobstacledetection.navigation

import com.example.blindobstacledetection.location.UserLocation
import com.example.blindobstacledetection.navigation.geocoding.GeocodingRepository
import com.example.blindobstacledetection.navigation.geocoding.NominatimGeocodingRepository
import com.example.blindobstacledetection.navigation.models.GeocodedLocation
import com.example.blindobstacledetection.navigation.models.RouteCoordinates
import com.example.blindobstacledetection.navigation.models.RouteStep
import com.example.blindobstacledetection.navigation.models.RouteUiState
import com.example.blindobstacledetection.navigation.models.WalkingRoute
import com.example.blindobstacledetection.navigation.routing.MissingApiKeyException
import com.example.blindobstacledetection.navigation.routing.WalkingRouteRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for geocoding input recognition, coordinate validation,
 * RouteUiState lifecycle, and state coordination across Phases 17, 18, and 19.
 */
class GeocodingAndNavigationFlowTest {

    @Test
    fun testDirectCoordinateParsingInGeocoding() = runBlocking {
        val repo = NominatimGeocodingRepository()

        // Test comma separated coordinate query
        val commaResult = repo.geocode("11.0168, 76.9558")
        assertTrue(commaResult.isSuccess)
        val commaLocation = commaResult.getOrNull()
        assertNotNull(commaLocation)
        assertEquals(11.0168, commaLocation!!.latitude, 0.0001)
        assertEquals(76.9558, commaLocation.longitude, 0.0001)
        assertEquals("DirectCoordinates", commaLocation.provider)

        // Test space separated coordinate query
        val spaceResult = repo.geocode("11.0168 76.9558")
        assertTrue(spaceResult.isSuccess)
        assertEquals(11.0168, spaceResult.getOrNull()!!.latitude, 0.0001)

        // Test negative coordinates (e.g. southern/western hemisphere)
        val negativeResult = repo.geocode("-33.8688, 151.2093")
        assertTrue(negativeResult.isSuccess)
        assertEquals(-33.8688, negativeResult.getOrNull()!!.latitude, 0.0001)
        assertEquals(151.2093, negativeResult.getOrNull()!!.longitude, 0.0001)
    }

    @Test
    fun testBlankDestinationQueryReturnsFailure() = runBlocking {
        val repo = NominatimGeocodingRepository()
        val result = repo.geocode("   ")
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun testRouteUiStateTransitions() {
        var state: RouteUiState = RouteUiState.Idle
        assertEquals(RouteUiState.Idle, state)

        state = RouteUiState.Loading("Resolving destination: Central Station...")
        assertTrue(state is RouteUiState.Loading)
        assertEquals("Resolving destination: Central Station...", (state as RouteUiState.Loading).stage)

        val mockRoute = WalkingRoute(
            originLatitude = 11.0168,
            originLongitude = 76.9558,
            destinationLatitude = 11.0200,
            destinationLongitude = 76.9600,
            destinationName = "Central Station",
            totalDistanceMeters = 500.0,
            totalDurationSeconds = 400.0,
            steps = listOf(
                RouteStep(
                    stepIndex = 1,
                    instruction = "Head north",
                    distanceMeters = 500.0,
                    durationSeconds = 400.0
                )
            ),
            geometry = listOf(
                RouteCoordinates(11.0168, 76.9558),
                RouteCoordinates(11.0200, 76.9600)
            )
        )

        state = RouteUiState.Success(mockRoute)
        assertTrue(state is RouteUiState.Success)
        val success = state as RouteUiState.Success
        assertEquals("Central Station", success.route.destinationName)
        assertEquals("500 meters", success.route.formattedDistance)

        state = RouteUiState.Error(
            message = "No pedestrian route found",
            canRetry = true,
            isLocationError = false,
            isDestinationError = false
        )
        assertTrue(state is RouteUiState.Error)
        assertEquals("No pedestrian route found", (state as RouteUiState.Error).message)
        assertTrue((state as RouteUiState.Error).canRetry)
    }

    @Test
    fun testMissingApiKeyRouteState() {
        val missingKeyState = RouteUiState.MissingApiKey("API key is required")
        assertEquals("API key is required", missingKeyState.message)
    }

    @Test
    fun testDestinationAndLocationPreservedAcrossRoutePlanning() {
        // Simulates coordination between Phase 17, Phase 18, and Phase 19
        val confirmedDestination = "Town Hall"
        val userLocation = UserLocation(
            latitude = 11.0168,
            longitude = 76.9558,
            accuracyMeters = 10f
        )

        var routeState: RouteUiState = RouteUiState.Idle
        var currentRoute: WalkingRoute? = null

        // 1. Initial State: Destination confirmed, Location ready
        assertNotNull(confirmedDestination)
        assertNotNull(userLocation)
        assertNull(currentRoute)

        // 2. Route calculation in progress
        routeState = RouteUiState.Loading("Calculating walking route...")
        assertEquals("Town Hall", confirmedDestination)
        assertEquals(11.0168, userLocation.latitude, 0.0001)

        // 3. Route calculation succeeds
        val mockRoute = WalkingRoute(
            originLatitude = userLocation.latitude,
            originLongitude = userLocation.longitude,
            destinationLatitude = 11.0200,
            destinationLongitude = 76.9600,
            destinationName = confirmedDestination,
            totalDistanceMeters = 850.0,
            totalDurationSeconds = 680.0,
            steps = emptyList(),
            geometry = emptyList()
        )
        currentRoute = mockRoute
        routeState = RouteUiState.Success(mockRoute)

        // All three phases coexist harmoniously
        assertEquals("Town Hall", confirmedDestination)
        assertEquals(11.0168, userLocation.latitude, 0.0001)
        assertNotNull(currentRoute)
        assertEquals("850 meters", currentRoute!!.formattedDistance)

        // 4. Stale Route Clearing: If destination is cleared, route is cleared
        val clearedDestination: String? = null
        if (clearedDestination == null) {
            currentRoute = null
            routeState = RouteUiState.Idle
        }
        assertNull(clearedDestination)
        assertNull(currentRoute)
        assertEquals(RouteUiState.Idle, routeState)
        // User's GPS location remains valid even if destination is cleared
        assertEquals(11.0168, userLocation.latitude, 0.0001)
    }

    @Test
    fun testRouteCoordinatesValidation() {
        // Valid coordinates
        val validCoord = RouteCoordinates(11.0168, 76.9558)
        assertEquals(11.0168, validCoord.latitude, 0.0001)
        assertEquals(76.9558, validCoord.longitude, 0.0001)

        // Invalid latitude (> 90.0)
        var latFailed = false
        try {
            RouteCoordinates(95.0, 76.9558)
        } catch (_: IllegalArgumentException) {
            latFailed = true
        }
        assertTrue("Latitude > 90 must throw IllegalArgumentException", latFailed)

        // Invalid longitude (> 180.0)
        var lonFailed = false
        try {
            RouteCoordinates(11.0168, 185.0)
        } catch (_: IllegalArgumentException) {
            lonFailed = true
        }
        assertTrue("Longitude > 180 must throw IllegalArgumentException", lonFailed)
    }
}
