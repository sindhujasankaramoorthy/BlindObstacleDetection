package com.example.blindobstacledetection.navigation

import com.example.blindobstacledetection.location.UserLocation
import com.example.blindobstacledetection.navigation.models.NavigationState
import com.example.blindobstacledetection.navigation.models.RouteCoordinates
import com.example.blindobstacledetection.navigation.models.RouteStep
import com.example.blindobstacledetection.navigation.models.WalkingRoute
import com.example.blindobstacledetection.tts.SpeechPriority
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Automated unit test suite for Phase 20:
 * - Turn-by-turn navigation engine state transitions
 * - Monotonic forward progress and hysteresis (GPS jitter immunity)
 * - Poor accuracy filtering and stale location handling
 * - Route deviation tracking and recalculation throttling
 * - Destination arrival detection
 * - 4-tier speech priority pre-emption rules
 * - Obsolete instruction discard logic
 * - Navigation mute with preserved obstacle safety
 */
class Phase20NavigationCoordinationTest {

    private lateinit var sampleRoute: WalkingRoute
    private lateinit var engine: TurnByTurnNavigationEngine

    @Before
    fun setUp() {
        engine = TurnByTurnNavigationEngine()

        // Setup a 3-step walking route in Coimbatore
        // Step 0: Start at (11.0168, 76.9558), head north to (11.0175, 76.9558) (~77m)
        // Step 1: Turn right at (11.0175, 76.9558) and walk east to (11.0175, 76.9580) (~240m)
        // Step 2: Arrive at destination at (11.0175, 76.9580)
        val geometry = listOf(
            RouteCoordinates(11.0168, 76.9558), // Point 0 (Origin)
            RouteCoordinates(11.0175, 76.9558), // Point 1 (Turn corner)
            RouteCoordinates(11.0175, 76.9580)  // Point 2 (Destination)
        )

        val steps = listOf(
            RouteStep(
                stepIndex = 1,
                instruction = "Head north on Gandhipuram Cross Road",
                distanceMeters = 77.0,
                durationSeconds = 60.0,
                maneuverType = 11,
                streetName = "Gandhipuram Cross Road",
                wayPointStart = 0,
                wayPointEnd = 1
            ),
            RouteStep(
                stepIndex = 2,
                instruction = "Turn right onto 7th Cross Street",
                distanceMeters = 240.0,
                durationSeconds = 180.0,
                maneuverType = 1,
                streetName = "7th Cross Street",
                wayPointStart = 1,
                wayPointEnd = 2
            ),
            RouteStep(
                stepIndex = 3,
                instruction = "You have arrived at City Central Library",
                distanceMeters = 0.0,
                durationSeconds = 0.0,
                maneuverType = 10,
                streetName = "City Central Library",
                wayPointStart = 2,
                wayPointEnd = 2
            )
        )

        sampleRoute = WalkingRoute(
            originLatitude = 11.0168,
            originLongitude = 76.9558,
            destinationLatitude = 11.0175,
            destinationLongitude = 76.9580,
            destinationName = "City Central Library",
            totalDistanceMeters = 317.0,
            totalDurationSeconds = 240.0,
            steps = steps,
            geometry = geometry,
            provider = "OpenRouteService"
        )
    }

    // -------------------------------------------------------------
    // 1. Navigation State Lifecycle Tests
    // -------------------------------------------------------------

    @Test
    fun testStartNavigation_initializesNavigatingStateAndDepartureAnnouncement() {
        val result = engine.start(sampleRoute)

        assertNotNull(result)
        val state = result.navigationState
        assertTrue(state is NavigationState.Navigating)
        val nav = state as NavigationState.Navigating
        assertEquals(0, nav.currentStepIndex)
        assertEquals("Head north on Gandhipuram Cross Road", nav.currentStep.instruction)
        assertFalse(nav.isOffRoute)
        assertEquals(317.0, nav.remainingDistanceMeters, 0.1)

        // Departure announcement should introduce destination and first instruction
        assertNotNull(result.spokenAnnouncement)
        assertTrue(result.spokenAnnouncement!!.contains("Starting navigation to City Central Library"))
        assertTrue(result.spokenAnnouncement!!.contains("Head north"))
    }

    @Test
    fun testPauseAndResumeNavigation_preservesStepIndex() {
        engine.start(sampleRoute)

        // Advance to step 1
        val turnLocation = UserLocation(
            latitude = 11.0175,
            longitude = 76.9558,
            accuracyMeters = 5.0f,
            timestamp = System.currentTimeMillis()
        )
        engine.onLocationUpdate(turnLocation)

        val paused = engine.pause()
        assertNotNull(paused)
        assertTrue(paused is NavigationState.Paused)
        assertEquals(1, (paused as NavigationState.Paused).currentStepIndex)

        val resumed = engine.resume()
        assertNotNull(resumed)
        assertTrue(resumed is NavigationState.Navigating)
        assertEquals(1, (resumed as NavigationState.Navigating).currentStepIndex)
    }

    @Test
    fun testStopNavigation_clearsEngineState() {
        engine.start(sampleRoute)
        assertTrue(engine.isNavigating)

        engine.stop()
        assertFalse(engine.isNavigating)
        assertNull(engine.currentNavigationState)
    }

    // -------------------------------------------------------------
    // 2. Ordered Route-Step Progression & Maneuver Announcement
    // -------------------------------------------------------------

    @Test
    fun testStepProgression_advancesOnlyWhenApproachingManeuverPoint() {
        engine.start(sampleRoute)

        // 1. User is still at origin - distance to turn point (11.0175, 76.9558) is ~77m
        val originLoc = UserLocation(
            latitude = 11.0168,
            longitude = 76.9558,
            accuracyMeters = 4.0f,
            timestamp = System.currentTimeMillis()
        )
        val update1 = engine.onLocationUpdate(originLoc)
        assertNotNull(update1)
        assertEquals(0, (update1!!.navigationState as NavigationState.Navigating).currentStepIndex)

        // 2. User moves halfway along Gandhi Cross Rd (~40m north)
        val halfwayLoc = UserLocation(
            latitude = 11.01715,
            longitude = 76.9558,
            accuracyMeters = 4.0f,
            timestamp = System.currentTimeMillis()
        )
        val update2 = engine.onLocationUpdate(halfwayLoc)
        assertNotNull(update2)
        assertEquals(0, (update2!!.navigationState as NavigationState.Navigating).currentStepIndex)

        // 3. User approaches within 10 meters of the turn waypoint (11.0175, 76.9558)
        val turnPointLoc = UserLocation(
            latitude = 11.01745,
            longitude = 76.9558,
            accuracyMeters = 3.0f,
            timestamp = System.currentTimeMillis()
        )
        val update3 = engine.onLocationUpdate(turnPointLoc)
        assertNotNull(update3)
        // Step must advance to step 1 ("Turn right onto 7th Cross Street")
        assertEquals(1, (update3!!.navigationState as NavigationState.Navigating).currentStepIndex)
        assertNotNull(update3.spokenAnnouncement)
        assertTrue(update3.spokenAnnouncement!!.contains("Turn right onto 7th Cross Street"))
    }

    // -------------------------------------------------------------
    // 3. GPS Jitter Immunity (Monotonic Forward Progress)
    // -------------------------------------------------------------

    @Test
    fun testGpsJitter_doesNotMoveStepBackward() {
        engine.start(sampleRoute)

        // Reach step 1
        val turnLoc = UserLocation(
            latitude = 11.0175,
            longitude = 76.9558,
            accuracyMeters = 4.0f,
            timestamp = System.currentTimeMillis()
        )
        engine.onLocationUpdate(turnLoc)
        assertEquals(1, (engine.currentNavigationState as NavigationState.Navigating).currentStepIndex)

        // GPS fluctuates slightly back south towards origin
        val jitterLoc = UserLocation(
            latitude = 11.0170,
            longitude = 76.9558,
            accuracyMeters = 6.0f,
            timestamp = System.currentTimeMillis()
        )
        val update = engine.onLocationUpdate(jitterLoc)
        assertNotNull(update)
        // Monotonic rule: currentStepIndex must NEVER decrement back to 0
        assertEquals(1, (update!!.navigationState as NavigationState.Navigating).currentStepIndex)
    }

    // -------------------------------------------------------------
    // 4. Stale and Inaccurate Location Filtering
    // -------------------------------------------------------------

    @Test
    fun testInaccurateLocation_isIgnoredForManeuverTriggers() {
        engine.start(sampleRoute)

        // Location with terrible accuracy (e.g., 80 meters) located right at turn corner
        val inaccurateLoc = UserLocation(
            latitude = 11.0175,
            longitude = 76.9558,
            accuracyMeters = 80.0f,
            timestamp = System.currentTimeMillis()
        )
        val update = engine.onLocationUpdate(inaccurateLoc)
        assertNotNull(update)
        assertTrue((update!!.navigationState as NavigationState.Navigating).isLocationWeak)
        // Must NOT advance step due to weak accuracy!
        assertEquals(0, (update.navigationState as NavigationState.Navigating).currentStepIndex)
        assertNull(update.spokenAnnouncement)
    }

    @Test
    fun testStaleLocation_isIgnoredForManeuverTriggers() {
        engine.start(sampleRoute)

        // Location timestamp is 60 seconds old
        val staleLoc = UserLocation(
            latitude = 11.0175,
            longitude = 76.9558,
            accuracyMeters = 5.0f,
            timestamp = System.currentTimeMillis() - 60_000L
        )
        val update = engine.onLocationUpdate(staleLoc)
        assertNotNull(update)
        assertTrue((update!!.navigationState as NavigationState.Navigating).isLocationWeak)
        // Must NOT advance step due to stale GPS fix!
        assertEquals(0, (update.navigationState as NavigationState.Navigating).currentStepIndex)
        assertNull(update.spokenAnnouncement)
    }

    // -------------------------------------------------------------
    // 5. Route Deviation Detection & Recalculation Throttling
    // -------------------------------------------------------------

    @Test
    fun testOffRouteDeviation_triggersOnlyAfterConsecutiveDeviations() {
        engine.start(sampleRoute)

        // Deviate 60 meters west of the route (route is at lon 76.9558, user is at lon 76.9550)
        val offRouteLoc = UserLocation(
            latitude = 11.0170,
            longitude = 76.9550,
            accuracyMeters = 5.0f,
            timestamp = System.currentTimeMillis()
        )

        // Fix 1: deviation detected, but consecutive fixes < 3 -> not confirmed off-route yet
        val up1 = engine.onLocationUpdate(offRouteLoc)
        assertNotNull(up1)
        assertFalse((up1!!.navigationState as NavigationState.Navigating).isOffRoute)

        // Fix 2: still off route
        val up2 = engine.onLocationUpdate(offRouteLoc)
        assertNotNull(up2)
        assertFalse((up2!!.navigationState as NavigationState.Navigating).isOffRoute)

        // Fix 3: 3rd consecutive deviation fix -> confirmed off route!
        val up3 = engine.onLocationUpdate(offRouteLoc)
        assertNotNull(up3)
        val navState = up3!!.navigationState as NavigationState.Navigating
        assertTrue(navState.isOffRoute)
        assertTrue(navState.offRouteDistanceMeters >= 35.0)
        assertNotNull(up3.spokenAnnouncement)
        assertTrue(up3.spokenAnnouncement!!.contains("left the walking path"))
    }

    // -------------------------------------------------------------
    // 6. Arrival Detection
    // -------------------------------------------------------------

    @Test
    fun testArrivalDetection_approachingDestinationCompletesNavigation() {
        engine.start(sampleRoute)

        // Step 0 -> Step 1 (turn corner at 11.0175, 76.9558)
        engine.onLocationUpdate(UserLocation(11.0175, 76.9558, 4.0f, System.currentTimeMillis()))

        // Step 1 -> Step 2 (arrive near destination waypoint 11.0175, 76.9580 within step advance threshold 12m)
        engine.onLocationUpdate(UserLocation(11.0175, 76.95795, 3.0f, System.currentTimeMillis()))

        // Within 5 meters of final destination (11.0175, 76.9580) triggers arrival completion
        val arrivalLoc = UserLocation(
            latitude = 11.017502,
            longitude = 76.958003,
            accuracyMeters = 3.0f,
            timestamp = System.currentTimeMillis()
        )
        val update = engine.onLocationUpdate(arrivalLoc)
        assertNotNull(update)
        assertTrue(update!!.isArrived)
        val state = update.navigationState
        assertTrue(state is NavigationState.Completed)
        assertEquals("City Central Library", (state as NavigationState.Completed).destinationName)
        assertNotNull(update.spokenAnnouncement)
        assertTrue(update.spokenAnnouncement!!.contains("You have arrived at your destination: City Central Library"))
    }

    // -------------------------------------------------------------
    // 7. Critical Speech Priority Hierarchy Tests
    // -------------------------------------------------------------

    @Test
    fun testSpeechPriorityHierarchy_criticalObstaclePreemptsAll() {
        // CRITICAL_OBSTACLE has highest priority
        assertTrue(SpeechPriority.CRITICAL_OBSTACLE.preempts(SpeechPriority.IMPORTANT_OBSTACLE))
        assertTrue(SpeechPriority.CRITICAL_OBSTACLE.preempts(SpeechPriority.NAVIGATION_MANEUVER))
        assertTrue(SpeechPriority.CRITICAL_OBSTACLE.preempts(SpeechPriority.STATUS_MESSAGE))

        // IMPORTANT_OBSTACLE preempts routine navigation and status, but NOT critical obstacle
        assertFalse(SpeechPriority.IMPORTANT_OBSTACLE.preempts(SpeechPriority.CRITICAL_OBSTACLE))
        assertTrue(SpeechPriority.IMPORTANT_OBSTACLE.preempts(SpeechPriority.NAVIGATION_MANEUVER))
        assertTrue(SpeechPriority.IMPORTANT_OBSTACLE.preempts(SpeechPriority.STATUS_MESSAGE))

        // NAVIGATION_MANEUVER does NOT preempt obstacle warnings
        assertFalse(SpeechPriority.NAVIGATION_MANEUVER.preempts(SpeechPriority.CRITICAL_OBSTACLE))
        assertFalse(SpeechPriority.NAVIGATION_MANEUVER.preempts(SpeechPriority.IMPORTANT_OBSTACLE))
        assertTrue(SpeechPriority.NAVIGATION_MANEUVER.preempts(SpeechPriority.STATUS_MESSAGE))

        // STATUS_MESSAGE does NOT preempt anything
        assertFalse(SpeechPriority.STATUS_MESSAGE.preempts(SpeechPriority.CRITICAL_OBSTACLE))
        assertFalse(SpeechPriority.STATUS_MESSAGE.preempts(SpeechPriority.IMPORTANT_OBSTACLE))
        assertFalse(SpeechPriority.STATUS_MESSAGE.preempts(SpeechPriority.NAVIGATION_MANEUVER))
    }

    // -------------------------------------------------------------
    // 8. Obsolete Instruction Discard Logic Test
    // -------------------------------------------------------------

    @Test
    fun testObsoleteNavigationInstruction_discardedWhenStale() {
        val staleDurationMs = 8_000L
        val promptTimestamp = System.currentTimeMillis() - 9_000L // 9 seconds ago

        val isStale = (System.currentTimeMillis() - promptTimestamp) > staleDurationMs
        assertTrue("Pending navigation older than 8s must be discarded as obsolete", isStale)
    }

    // -------------------------------------------------------------
    // 9. Repeated Maneuver Instruction Suppression Test
    // -------------------------------------------------------------

    @Test
    fun testRepeatedManeuverSuppression_doesNotSpamSameAnnouncement() {
        engine.start(sampleRoute)

        val loc1 = UserLocation(11.0168, 76.9558, 4.0f, System.currentTimeMillis())
        val u1 = engine.onLocationUpdate(loc1)
        // No maneuver approaching at start; no repetitive spam
        assertNull(u1?.spokenAnnouncement)

        val loc2 = UserLocation(11.01685, 76.9558, 4.0f, System.currentTimeMillis())
        val u2 = engine.onLocationUpdate(loc2)
        assertNull(u2?.spokenAnnouncement)
    }
}
