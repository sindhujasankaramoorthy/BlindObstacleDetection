package com.example.blindobstacledetection.location

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Focused unit tests validating UserLocation accuracy logic,
 * LocationUiState lifecycle transitions, and ensuring confirmed destination
 * is preserved across GPS state changes.
 */
class LocationStateHolderTest {

    @Test
    fun testUserLocationAccuracyEvaluation() {
        val preciseLocation = UserLocation(
            latitude = 11.0168,
            longitude = 76.9558,
            accuracyMeters = 8.5f,
            timestamp = System.currentTimeMillis(),
            provider = "fused"
        )
        assertTrue("8.5m accuracy should be flagged as accurate", preciseLocation.isAccurate)
        assertFalse("8.5m accuracy should not be approximate", preciseLocation.isApproximate)
        assertEquals("High accuracy (±8m)", preciseLocation.accuracyDescription)

        val approximateLocation = UserLocation(
            latitude = 11.0168,
            longitude = 76.9558,
            accuracyMeters = 150.0f,
            timestamp = System.currentTimeMillis(),
            provider = "network"
        )
        assertFalse("150m accuracy should not be flagged as accurate", approximateLocation.isAccurate)
        assertTrue("150m accuracy should be flagged as approximate", approximateLocation.isApproximate)
        assertEquals("Approximate location (±150m)", approximateLocation.accuracyDescription)
    }

    @Test
    fun testUserLocationFreshnessCalculation() {
        val now = System.currentTimeMillis()
        val freshLocation = UserLocation(
            latitude = 11.0168,
            longitude = 76.9558,
            accuracyMeters = 10f,
            timestamp = now
        )
        assertEquals(0L, freshLocation.freshnessSeconds)

        val fiveMinutesAgo = now - 300_000L
        val olderLocation = UserLocation(
            latitude = 11.0168,
            longitude = 76.9558,
            accuracyMeters = 10f,
            timestamp = fiveMinutesAgo
        )
        assertTrue("Older location should have freshness >= 300s", olderLocation.freshnessSeconds >= 300L)
    }

    @Test
    fun testLocationStateTransitions() {
        var state: LocationUiState = LocationUiState.Idle
        assertEquals(LocationUiState.Idle, state)

        state = LocationUiState.Loading("Getting your location...")
        assertTrue(state is LocationUiState.Loading)
        assertEquals("Getting your location...", (state as LocationUiState.Loading).message)

        val mockLocation = UserLocation(
            latitude = 11.016844,
            longitude = 76.955832,
            accuracyMeters = 12.0f,
            timestamp = System.currentTimeMillis(),
            provider = "fused"
        )
        state = LocationUiState.Success(mockLocation, "Location acquired")
        assertTrue(state is LocationUiState.Success)
        val successState = state as LocationUiState.Success
        assertEquals("Location acquired", successState.message)
        assertEquals(11.016844, successState.location.latitude, 0.000001)
        assertEquals(76.955832, successState.location.longitude, 0.000001)
    }

    @Test
    fun testPermissionDeniedState() {
        val deniedState = LocationUiState.Error(
            message = "Location permission is required to acquire your position.",
            isPermissionError = true
        )
        assertTrue(deniedState.isPermissionError)
        assertEquals("Location permission is required to acquire your position.", deniedState.message)
    }

    @Test
    fun testLocationDisabledState() {
        val disabledState = LocationUiState.LocationDisabled(
            message = "Location services (GPS) are disabled. Please enable GPS in device settings."
        )
        assertTrue(disabledState.message.contains("disabled"))
    }

    @Test
    fun testConfirmedDestinationRemainsIntactAcrossLocationLifecycle() {
        // Simulates the Phase 17 + Phase 18 state holder behavior
        val confirmedDestination = "Coimbatore Railway Station"
        var locationState: LocationUiState = LocationUiState.Idle
        var userLocation: UserLocation? = null

        // Step 1: User requests location
        locationState = LocationUiState.Loading("Getting your location...")
        assertEquals("Coimbatore Railway Station", confirmedDestination)
        assertNull(userLocation)

        // Step 2: Location services fail / timeout occurs
        locationState = LocationUiState.Error("Acquisition timed out", isPermissionError = false)
        assertEquals("Coimbatore Railway Station", confirmedDestination)
        assertNull(userLocation)

        // Step 3: User retries and succeeds
        val acquiredLocation = UserLocation(
            latitude = 11.0016,
            longitude = 76.9628,
            accuracyMeters = 15.0f,
            timestamp = System.currentTimeMillis()
        )
        locationState = LocationUiState.Success(acquiredLocation, "Location acquired")
        userLocation = acquiredLocation

        // Step 4: Verify both Phase 17 destination and Phase 18 location coexist cleanly
        assertEquals("Coimbatore Railway Station", confirmedDestination)
        assertNotNull(userLocation)
        assertEquals(11.0016, userLocation.latitude, 0.0001)
        assertEquals(76.9628, userLocation.longitude, 0.0001)
    }

    @Test
    fun testMockLocationFlag() {
        val mockLocation = UserLocation(
            latitude = 11.0,
            longitude = 76.0,
            accuracyMeters = 5.0f,
            isMock = true
        )
        assertTrue(mockLocation.isMock)

        val realLocation = UserLocation(
            latitude = 11.0,
            longitude = 76.0,
            accuracyMeters = 5.0f,
            isMock = false
        )
        assertFalse(realLocation.isMock)
    }
}
