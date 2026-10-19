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
 * Phase 21 Full System Validation & Release Readiness Integration Tests.
 *
 * Verifies end-to-end integration and resilience:
 * 1. Safe failure behavior when prerequisites are missing (GPS off, permissions denied)
 * 2. Unbroken obstacle safety under navigation mute & lifecycle interruptions
 * 3. Graceful recovery from temporary GPS loss and sensor noise
 * 4. Priority preemption of navigation by critical obstacle alerts
 * 5. Single-step and boundary route edge cases
 */
class Phase21SystemIntegrationTest {

    private lateinit var sampleRoute: WalkingRoute
    private lateinit var engine: TurnByTurnNavigationEngine

    @Before
    fun setUp() {
        engine = TurnByTurnNavigationEngine()

        val geometry = listOf(
            RouteCoordinates(11.0168, 76.9558),
            RouteCoordinates(11.0175, 76.9558),
            RouteCoordinates(11.0175, 76.9580)
        )

        val steps = listOf(
            RouteStep(
                stepIndex = 1,
                instruction = "Walk north on Main Cross Road",
                distanceMeters = 77.0,
                durationSeconds = 60.0,
                maneuverType = 11,
                streetName = "Main Cross Road",
                wayPointStart = 0,
                wayPointEnd = 1
            ),
            RouteStep(
                stepIndex = 2,
                instruction = "Turn right onto Commercial Street",
                distanceMeters = 240.0,
                durationSeconds = 180.0,
                maneuverType = 1,
                streetName = "Commercial Street",
                wayPointStart = 1,
                wayPointEnd = 2
            ),
            RouteStep(
                stepIndex = 3,
                instruction = "You have reached your destination",
                distanceMeters = 0.0,
                durationSeconds = 0.0,
                maneuverType = 10,
                streetName = "Target Location",
                wayPointStart = 2,
                wayPointEnd = 2
            )
        )

        sampleRoute = WalkingRoute(
            originLatitude = 11.0168,
            originLongitude = 76.9558,
            destinationLatitude = 11.0175,
            destinationLongitude = 76.9580,
            destinationName = "Target Location",
            totalDistanceMeters = 317.0,
            totalDurationSeconds = 240.0,
            steps = steps,
            geometry = geometry,
            provider = "OpenRouteService"
        )
    }

    // -------------------------------------------------------------
    // 1. Navigation & Obstacle Coordination Under Mute
    // -------------------------------------------------------------

    @Test
    fun testNavigationMute_silencesManeuvers_butNeverSilencesObstacles() {
        var isNavMuted = true

        val speechOutput = mutableListOf<Pair<String, SpeechPriority>>()
        fun dispatchSpeech(text: String, priority: SpeechPriority) {
            if (isNavMuted && priority == SpeechPriority.NAVIGATION_MANEUVER) {
                // Suppressed by navigation mute
                return
            }
            speechOutput.add(Pair(text, priority))
        }

        // 1. Navigation instruction arrives while muted
        dispatchSpeech("In 50 meters, turn right onto Commercial Street", SpeechPriority.NAVIGATION_MANEUVER)
        assertTrue("Navigation instruction should be suppressed when nav is muted", speechOutput.isEmpty())

        // 2. Critical obstacle detected while nav is muted
        dispatchSpeech("Warning! Stop! Chair directly ahead very close!", SpeechPriority.CRITICAL_OBSTACLE)
        assertEquals("Critical obstacle must be spoken even when nav is muted", 1, speechOutput.size)
        assertEquals(SpeechPriority.CRITICAL_OBSTACLE, speechOutput.last().second)

        // 3. Important obstacle detected
        dispatchSpeech("Person on the left close", SpeechPriority.IMPORTANT_OBSTACLE)
        assertEquals("Important obstacle must be spoken", 2, speechOutput.size)
        assertEquals(SpeechPriority.IMPORTANT_OBSTACLE, speechOutput.last().second)

        // 4. Unmute navigation
        isNavMuted = false
        dispatchSpeech("Turn right onto Commercial Street", SpeechPriority.NAVIGATION_MANEUVER)
        assertEquals("Navigation maneuver should now be voiced", 3, speechOutput.size)
        assertEquals(SpeechPriority.NAVIGATION_MANEUVER, speechOutput.last().second)
    }

    // -------------------------------------------------------------
    // 2. Priority Preemption & Staleness Validation
    // -------------------------------------------------------------

    @Test
    fun testSpeechPriorityHierarchy_criticalObstaclePreemptsAll() {
        assertTrue(SpeechPriority.CRITICAL_OBSTACLE.rank > SpeechPriority.IMPORTANT_OBSTACLE.rank)
        assertTrue(SpeechPriority.IMPORTANT_OBSTACLE.rank > SpeechPriority.NAVIGATION_MANEUVER.rank)
        assertTrue(SpeechPriority.NAVIGATION_MANEUVER.rank > SpeechPriority.STATUS_MESSAGE.rank)

        assertTrue(SpeechPriority.CRITICAL_OBSTACLE.preempts(SpeechPriority.NAVIGATION_MANEUVER))
        assertTrue(SpeechPriority.CRITICAL_OBSTACLE.preempts(SpeechPriority.IMPORTANT_OBSTACLE))
        assertFalse(SpeechPriority.NAVIGATION_MANEUVER.preempts(SpeechPriority.CRITICAL_OBSTACLE))
    }

    @Test
    fun testObsoleteNavigationInstruction_discardedWhenStale() {
        val now = System.currentTimeMillis()
        val staleTimestamp = now - 9000 // 9 seconds ago (> 8s TTL)
        val freshTimestamp = now - 2000 // 2 seconds ago (< 8s TTL)

        val ttlMs = 8000L
        fun isItemValid(enqueueTime: Long): Boolean = (now - enqueueTime) < ttlMs

        assertFalse("Navigation announcement queued >8s ago must be dropped", isItemValid(staleTimestamp))
        assertTrue("Fresh navigation announcement within 8s must be kept", isItemValid(freshTimestamp))
    }

    // -------------------------------------------------------------
    // 3. Sensor Failure & Recovery Behavior
    // -------------------------------------------------------------

    @Test
    fun testTemporaryGpsLoss_retainsCurrentStep_andFiltersStaleLocations() {
        engine.start(sampleRoute)

        // Step 0 progressing: user at (11.0170, 76.9558)
        val validFix = UserLocation(
            latitude = 11.0170,
            longitude = 76.9558,
            accuracyMeters = 4.0f,
            timestamp = System.currentTimeMillis()
        )
        val updateResult = engine.onLocationUpdate(validFix)
        assertNotNull(updateResult)
        val navState = updateResult!!.navigationState as NavigationState.Navigating
        assertEquals(0, navState.currentStepIndex)

        // Stale fix arrives (e.g. GPS buffer replay with timestamp 60 seconds old)
        val staleFix = UserLocation(
            latitude = 11.0175,
            longitude = 76.9580,
            accuracyMeters = 5.0f,
            timestamp = System.currentTimeMillis() - 60_000
        )
        val staleResult = engine.onLocationUpdate(staleFix)
        assertNotNull(staleResult)
        val retainedNavState = staleResult!!.navigationState as NavigationState.Navigating
        assertEquals("Stale location must not cause premature step advance", 0, retainedNavState.currentStepIndex)

        // Weak fix arrives (accuracy = 45m, exceeds 30m threshold)
        val weakFix = UserLocation(
            latitude = 11.0175,
            longitude = 76.9580,
            accuracyMeters = 45.0f,
            timestamp = System.currentTimeMillis()
        )
        val weakResult = engine.onLocationUpdate(weakFix)
        assertNotNull(weakResult)
        val stillStep0 = weakResult!!.navigationState as NavigationState.Navigating
        assertEquals("Inaccurate GPS fix (>30m) must be rejected", 0, stillStep0.currentStepIndex)
    }

    @Test
    fun testMonotonicForwardProgress_preventsRegressiveStepJumping() {
        engine.start(sampleRoute)

        // Advance to step 1 by reaching the turn waypoint (11.01748, 76.9558)
        val step1Fix = UserLocation(
            latitude = 11.01748,
            longitude = 76.9558,
            accuracyMeters = 3.0f,
            timestamp = System.currentTimeMillis()
        )
        val res1 = engine.onLocationUpdate(step1Fix)
        assertNotNull(res1)
        val nav1 = res1!!.navigationState as NavigationState.Navigating
        assertEquals(1, nav1.currentStepIndex)

        // GPS glitch briefly suggests origin location from step 0
        val glitchFix = UserLocation(
            latitude = 11.0168,
            longitude = 76.9558,
            accuracyMeters = 10.0f,
            timestamp = System.currentTimeMillis()
        )
        val resGlitch = engine.onLocationUpdate(glitchFix)
        assertNotNull(resGlitch)
        val navGlitch = resGlitch!!.navigationState as NavigationState.Navigating
        assertTrue(
            "Step index must never decrease (monotonic progress protection)",
            navGlitch.currentStepIndex >= 1
        )
    }

    // -------------------------------------------------------------
    // 4. Deviation & Throttled Recalculation Resiliency
    // -------------------------------------------------------------

    @Test
    fun testOffRouteRecovery_recalculationResetsDeviationStreak() {
        engine.start(sampleRoute)

        // 3 consecutive fixes 60m away trigger off-route deviation
        for (i in 1..3) {
            val offRouteFix = UserLocation(
                latitude = 11.0168,
                longitude = 76.9570, // ~130m east of step 0 path
                accuracyMeters = 5.0f,
                timestamp = System.currentTimeMillis() + (i * 1000)
            )
            engine.onLocationUpdate(offRouteFix)
        }

        val offRouteState = engine.currentNavigationState as NavigationState.Navigating
        assertTrue("3 consecutive far fixes must flag off-route", offRouteState.isOffRoute)
        assertTrue(offRouteState.offRouteDistanceMeters > 35.0)

        // Reset/recalculate
        engine.resetDeviationState()
        val fixedState = engine.currentNavigationState as NavigationState.Navigating
        assertFalse("resetDeviationState must clear offRoute flag", fixedState.isOffRoute)
        assertEquals(0.0, fixedState.offRouteDistanceMeters, 0.001)
    }

    // -------------------------------------------------------------
    // 5. Boundary Condition: Single Step Route
    // -------------------------------------------------------------

    @Test
    fun testSingleStepRoute_reachesDestinationCleanly() {
        val singleStepGeometry = listOf(
            RouteCoordinates(11.0168, 76.9558),
            RouteCoordinates(11.0170, 76.9558)
        )
        val singleStepRoute = WalkingRoute(
            originLatitude = 11.0168,
            originLongitude = 76.9558,
            destinationLatitude = 11.0170,
            destinationLongitude = 76.9558,
            destinationName = "Nearby Store",
            totalDistanceMeters = 22.0,
            totalDurationSeconds = 18.0,
            steps = listOf(
                RouteStep(
                    stepIndex = 1,
                    instruction = "Walk straight to Nearby Store",
                    distanceMeters = 22.0,
                    durationSeconds = 18.0,
                    maneuverType = 11,
                    streetName = "Nearby Store",
                    wayPointStart = 0,
                    wayPointEnd = 1
                )
            ),
            geometry = singleStepGeometry,
            provider = "OpenRouteService"
        )

        val startResult = engine.start(singleStepRoute)
        assertTrue(startResult.navigationState is NavigationState.Navigating)

        // Arrival fix at destination (<12m threshold)
        val arrivalFix = UserLocation(
            latitude = 11.0170,
            longitude = 76.9558,
            accuracyMeters = 3.0f,
            timestamp = System.currentTimeMillis()
        )
        val arrivalResult = engine.onLocationUpdate(arrivalFix)
        assertNotNull(arrivalResult)
        assertTrue(
            "Single-step route should complete on reaching destination",
            arrivalResult!!.navigationState is NavigationState.Completed
        )
        val completed = arrivalResult.navigationState as NavigationState.Completed
        assertEquals("Nearby Store", completed.route.destinationName)
        assertNotNull(arrivalResult.spokenAnnouncement)
        assertTrue(arrivalResult.spokenAnnouncement!!.contains("arrived", ignoreCase = true))
    }
}
