package com.example.blindobstacledetection.navigation

import com.example.blindobstacledetection.navigation.models.RouteCoordinates
import com.example.blindobstacledetection.navigation.models.RouteStep
import com.example.blindobstacledetection.navigation.models.WalkingRoute
import com.example.blindobstacledetection.navigation.routing.MissingApiKeyException
import com.example.blindobstacledetection.navigation.routing.OpenRouteServiceWalkingRepository
import com.example.blindobstacledetection.navigation.routing.RouteNotFoundException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for OpenRouteService pedestrian walking route parsing,
 * metrics extraction, and error handling.
 */
class WalkingRouteParsingTest {

    private val sampleOrsGeoJson = """
    {
      "type": "FeatureCollection",
      "features": [
        {
          "type": "Feature",
          "geometry": {
            "coordinates": [
              [76.9558, 11.0168],
              [76.9560, 11.0170],
              [76.9565, 11.0175],
              [76.9570, 11.0180]
            ],
            "type": "LineString"
          },
          "properties": {
            "summary": {
              "distance": 450.5,
              "duration": 360.0
            },
            "segments": [
              {
                "distance": 450.5,
                "duration": 360.0,
                "steps": [
                  {
                    "distance": 150.0,
                    "duration": 120.0,
                    "type": 11,
                    "instruction": "Head northeast on Cross Cut Road",
                    "name": "Cross Cut Road",
                    "way_points": [0, 1]
                  },
                  {
                    "distance": 200.5,
                    "duration": 160.0,
                    "type": 1,
                    "instruction": "Turn right onto 7th Street",
                    "name": "7th Street",
                    "way_points": [1, 2]
                  },
                  {
                    "distance": 100.0,
                    "duration": 80.0,
                    "type": 10,
                    "instruction": "Arrive at Gandhipuram Bus Stand",
                    "name": "-",
                    "way_points": [2, 3]
                  }
                ]
              }
            ]
          }
        }
      ]
    }
    """.trimIndent()

    @Test
    fun testSuccessfulGeoJsonParsing() {
        val repo = OpenRouteServiceWalkingRepository(apiKeyProvider = { "test_dummy_key" })
        val result = repo.parseGeoJson(
            json = sampleOrsGeoJson,
            originLat = 11.0168,
            originLon = 76.9558,
            destLat = 11.0180,
            destLon = 76.9570,
            destinationName = "Gandhipuram Bus Stand"
        )

        assertTrue("Route parsing should succeed", result.isSuccess)
        val route = result.getOrNull()
        assertNotNull(route)

        // Validate summary metrics
        assertEquals(450.5, route!!.totalDistanceMeters, 0.01)
        assertEquals(360.0, route.totalDurationSeconds, 0.01)
        assertEquals("451 meters", route.formattedDistance)
        assertEquals("6 minutes", route.formattedDuration)
        assertEquals("Gandhipuram Bus Stand", route.destinationName)
        assertEquals("OpenRouteService foot-walking", route.provider)

        // Validate geometry coordinates
        assertEquals(4, route.geometry.size)
        assertEquals(11.0168, route.geometry[0].latitude, 0.0001)
        assertEquals(76.9558, route.geometry[0].longitude, 0.0001)
        assertEquals(11.0180, route.geometry[3].latitude, 0.0001)
        assertEquals(76.9570, route.geometry[3].longitude, 0.0001)

        // Validate ordered steps
        assertEquals(3, route.steps.size)

        val step1 = route.steps[0]
        assertEquals(1, step1.stepIndex)
        assertEquals("Head northeast on Cross Cut Road", step1.instruction)
        assertEquals(150.0, step1.distanceMeters, 0.01)
        assertEquals(120.0, step1.durationSeconds, 0.01)
        assertEquals(11, step1.maneuverType)
        assertEquals("Depart / Head", step1.maneuverName)
        assertEquals("Cross Cut Road", step1.streetName)
        assertEquals("150 m", step1.formattedDistance)
        assertEquals("2 min", step1.formattedDuration)

        val step2 = route.steps[1]
        assertEquals(2, step2.stepIndex)
        assertEquals("Turn right onto 7th Street", step2.instruction)
        assertEquals(1, step2.maneuverType)
        assertEquals("Turn Right", step2.maneuverName)
        assertEquals("7th Street", step2.streetName)

        val step3 = route.steps[2]
        assertEquals(3, step3.stepIndex)
        assertEquals("Arrive at Gandhipuram Bus Stand", step3.instruction)
        assertEquals(10, step3.maneuverType)
        assertEquals("Arrive at Destination", step3.maneuverName)
    }

    @Test
    fun testKilometerFormatting() {
        val longRoute = WalkingRoute(
            originLatitude = 11.0,
            originLongitude = 76.0,
            destinationLatitude = 11.02,
            destinationLongitude = 76.02,
            destinationName = "Town Hall",
            totalDistanceMeters = 2450.0,
            totalDurationSeconds = 1850.0,
            steps = emptyList(),
            geometry = emptyList()
        )
        assertEquals("2.5 km", longRoute.formattedDistance)
        assertEquals("31 minutes", longRoute.formattedDuration)
    }

    @Test
    fun testStepAccessibleTextFormatting() {
        val step = RouteStep(
            stepIndex = 1,
            instruction = "Turn left onto Anna Salai",
            distanceMeters = 85.0,
            durationSeconds = 65.0,
            maneuverType = 0,
            streetName = "Anna Salai"
        )
        val accessible = step.accessibleText
        assertTrue(accessible.contains("Step 1: Turn left onto Anna Salai"))
        assertTrue(accessible.contains("85 m"))
        assertTrue(accessible.contains("1 min"))
    }

    @Test
    fun testMissingApiKeyThrowsException() = runBlocking {
        val repo = OpenRouteServiceWalkingRepository(apiKeyProvider = { "" })
        val result = repo.calculateWalkingRoute(
            originLat = 11.0168,
            originLon = 76.9558,
            destLat = 11.0180,
            destLon = 76.9570,
            destinationName = "Station"
        )

        assertTrue(result.isFailure)
        val exception = result.exceptionOrNull()
        assertTrue(exception is MissingApiKeyException)
        assertTrue(exception?.message?.contains("API key not configured") == true)
    }

    @Test
    fun testInvalidCoordinateRangeRejection() = runBlocking {
        val repo = OpenRouteServiceWalkingRepository(apiKeyProvider = { "valid_key" })

        // Origin latitude out of bounds (> 90.0)
        val result = repo.calculateWalkingRoute(
            originLat = 120.0,
            originLon = 76.9558,
            destLat = 11.0180,
            destLon = 76.9570,
            destinationName = "Station"
        )
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun testMalformedJsonHandling() {
        val repo = OpenRouteServiceWalkingRepository(apiKeyProvider = { "dummy_key" })
        val result = repo.parseGeoJson(
            json = "{ malformed json ::: invalid }",
            originLat = 11.0,
            originLon = 76.0,
            destLat = 11.1,
            destLon = 76.1,
            destinationName = "Test"
        )
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalStateException)
    }

    @Test
    fun testEmptyFeatureListReturnsNoRoute() {
        val emptyGeoJson = """
        {
          "type": "FeatureCollection",
          "features": []
        }
        """.trimIndent()

        val repo = OpenRouteServiceWalkingRepository(apiKeyProvider = { "dummy_key" })
        val result = repo.parseGeoJson(
            json = emptyGeoJson,
            originLat = 11.0,
            originLon = 76.0,
            destLat = 11.1,
            destLon = 76.1,
            destinationName = "Test"
        )
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is RouteNotFoundException)
    }
}
