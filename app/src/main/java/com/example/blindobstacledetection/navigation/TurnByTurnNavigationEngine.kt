package com.example.blindobstacledetection.navigation

import com.example.blindobstacledetection.location.UserLocation
import com.example.blindobstacledetection.navigation.models.NavigationState
import com.example.blindobstacledetection.navigation.models.RouteCoordinates
import com.example.blindobstacledetection.navigation.models.RouteStep
import com.example.blindobstacledetection.navigation.models.WalkingRoute
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Result returned by [TurnByTurnNavigationEngine] on each location fix.
 */
data class NavigationEngineResult(
    val navigationState: NavigationState,
    val spokenAnnouncement: String? = null,
    val isOffRoute: Boolean = false,
    val isArrived: Boolean = false
)

/**
 * Intelligent, deterministic turn-by-turn navigation engine.
 * Computes live progress along verified route geometry, prevents GPS jitter flaps,
 * detects meaningful route deviations, and emits timely, non-repetitive maneuver announcements.
 */
class TurnByTurnNavigationEngine {

    companion object {
        const val STEP_ADVANCE_THRESHOLD_METERS = 12.0
        const val IMMINENT_MANEUVER_MIN_METERS = 20.0
        const val IMMINENT_MANEUVER_MAX_METERS = 35.0
        const val ARRIVAL_THRESHOLD_METERS = 10.0
        const val OFF_ROUTE_THRESHOLD_METERS = 35.0
        const val OFF_ROUTE_MIN_CONSECUTIVE_COUNT = 3
        const val WEAK_GPS_ACCURACY_THRESHOLD_METERS = 35.0
        const val OFF_ROUTE_ANNOUNCEMENT_COOLDOWN_MS = 25_000L
    }

    private var activeRoute: WalkingRoute? = null
    var currentStepIndex: Int = 0
        private set

    private var lastSpokenStepIndex = -1
    private var lastSpokenApproachingIndex = -1
    private var offRouteConsecutiveCount = 0
    private var lastOffRouteWarningTimestamp = 0L

    var isPaused: Boolean = false
        private set
    var isCompleted: Boolean = false
        private set

    val isNavigating: Boolean
        get() = activeRoute != null && !isPaused && !isCompleted

    var currentNavigationState: NavigationState? = null
        private set

    fun start(route: WalkingRoute): NavigationEngineResult {
        activeRoute = route
        currentStepIndex = 0
        lastSpokenStepIndex = -1
        lastSpokenApproachingIndex = -1
        offRouteConsecutiveCount = 0
        lastOffRouteWarningTimestamp = 0L
        isPaused = false
        isCompleted = false

        val initialStep = route.steps.firstOrNull() ?: RouteStep(
            stepIndex = 1,
            instruction = "Proceed to ${route.destinationName}",
            distanceMeters = route.totalDistanceMeters,
            durationSeconds = route.totalDurationSeconds
        )

        // Initial departure announcement
        val departureAnnouncement = "Starting navigation to ${route.destinationName}. ${initialStep.instruction}."
        lastSpokenStepIndex = 0

        val state = NavigationState.Navigating(
            route = route,
            currentStepIndex = 0,
            currentStep = initialStep,
            distanceToNextStepMeters = initialStep.distanceMeters,
            distanceToDestinationMeters = route.totalDistanceMeters,
            isApproachingManeuver = false,
            isOffRoute = false,
            currentLocation = null,
            isLocationWeak = false
        )
        currentNavigationState = state

        return NavigationEngineResult(
            navigationState = state,
            spokenAnnouncement = departureAnnouncement
        )
    }

    fun pause(): NavigationState? {
        val route = activeRoute ?: return null
        isPaused = true
        val step = route.steps.getOrNull(currentStepIndex) ?: route.steps.last()
        val state = NavigationState.Paused(route, currentStepIndex, step)
        currentNavigationState = state
        return state
    }

    fun resume(): NavigationState? {
        val route = activeRoute ?: return null
        isPaused = false
        val step = route.steps.getOrNull(currentStepIndex) ?: route.steps.last()
        val state = NavigationState.Navigating(
            route = route,
            currentStepIndex = currentStepIndex,
            currentStep = step,
            distanceToNextStepMeters = step.distanceMeters,
            distanceToDestinationMeters = route.totalDistanceMeters,
            isApproachingManeuver = false,
            isOffRoute = false,
            currentLocation = null,
            isLocationWeak = false
        )
        currentNavigationState = state
        return state
    }

    fun stop() {
        activeRoute = null
        currentStepIndex = 0
        isPaused = false
        isCompleted = false
        currentNavigationState = null
    }

    fun resetDeviationState() {
        offRouteConsecutiveCount = 0
        lastOffRouteWarningTimestamp = 0L
        val current = currentNavigationState
        if (current is NavigationState.Navigating) {
            currentNavigationState = current.copy(isOffRoute = false, offRouteDistanceMeters = 0.0)
        }
    }

    /**
     * Evaluates a live GPS fix against route geometry and ordered steps.
     */
    fun onLocationUpdate(location: UserLocation): NavigationEngineResult? {
        val route = activeRoute ?: return null
        if (isPaused || isCompleted) return null

        val now = System.currentTimeMillis()
        val isStale = (now - location.timestamp) > 20_000L
        val accuracy = location.accuracyMeters ?: 10f
        val isLocationWeak = accuracy > WEAK_GPS_ACCURACY_THRESHOLD_METERS || isStale

        // Calculate direct distance to destination
        val distToDestination = haversineDistanceMeters(
            location.latitude, location.longitude,
            route.destinationLatitude, route.destinationLongitude
        )

        val currentStep = route.steps.getOrNull(currentStepIndex) ?: route.steps.last()

        // 1. Arrival Check
        val isLastStep = (currentStepIndex >= route.steps.lastIndex)
        if (isLastStep && distToDestination <= ARRIVAL_THRESHOLD_METERS) {
            isCompleted = true
            val completedState = NavigationState.Completed(route)
            currentNavigationState = completedState
            return NavigationEngineResult(
                navigationState = completedState,
                spokenAnnouncement = "You have arrived at your destination: ${route.destinationName}.",
                isArrived = true
            )
        }

        // 2. Maneuver Target Coordinate
        val maneuverCoord = getManeuverCoordinate(route, currentStepIndex)
        val distToNextManeuver = haversineDistanceMeters(
            location.latitude, location.longitude,
            maneuverCoord.latitude, maneuverCoord.longitude
        )

        // 3. Step Advancement (with Hysteresis - monotonic forward)
        var updatedStepIndex = currentStepIndex
        if (!isLocationWeak && !isLastStep && distToNextManeuver <= STEP_ADVANCE_THRESHOLD_METERS) {
            updatedStepIndex = currentStepIndex + 1
            currentStepIndex = updatedStepIndex
        }

        val activeStep = route.steps.getOrNull(currentStepIndex) ?: route.steps.last()
        val nextManeuverCoord = getManeuverCoordinate(route, currentStepIndex)
        val updatedDistToManeuver = haversineDistanceMeters(
            location.latitude, location.longitude,
            nextManeuverCoord.latitude, nextManeuverCoord.longitude
        )

        // 4. Off-Route Detection (Minimum cross-track distance to geometry path)
        var isOffRoute = false
        var currentCrossTrackMeters = 0.0
        if (!isLocationWeak && route.geometry.isNotEmpty()) {
            val minDistanceToPath = minDistanceToGeometryMeters(
                location.latitude, location.longitude,
                route.geometry
            )
            currentCrossTrackMeters = minDistanceToPath

            if (minDistanceToPath > OFF_ROUTE_THRESHOLD_METERS && accuracy <= 25f) {
                offRouteConsecutiveCount++
                if (offRouteConsecutiveCount >= OFF_ROUTE_MIN_CONSECUTIVE_COUNT) {
                    isOffRoute = true
                }
            } else if (minDistanceToPath <= 25.0) {
                offRouteConsecutiveCount = 0
            }
        }

        // 5. Spoken Announcement Generation
        var announcement: String? = null

        if (currentStepIndex != lastSpokenStepIndex) {
            // New step entered: announce step guidance
            lastSpokenStepIndex = currentStepIndex
            announcement = activeStep.instruction
        } else if (!isLocationWeak && !isLastStep &&
            updatedDistToManeuver in IMMINENT_MANEUVER_MIN_METERS..IMMINENT_MANEUVER_MAX_METERS &&
            activeStep.distanceMeters > 50.0 &&
            currentStepIndex != lastSpokenApproachingIndex
        ) {
            // Approaching turn notification
            lastSpokenApproachingIndex = currentStepIndex
            val nextStep = route.steps.getOrNull(currentStepIndex + 1)
            if (nextStep != null) {
                announcement = "In ${round(updatedDistToManeuver).toInt()} meters, ${nextStep.instruction}"
            }
        } else if (isOffRoute && (now - lastOffRouteWarningTimestamp > OFF_ROUTE_ANNOUNCEMENT_COOLDOWN_MS)) {
            // Off-route warning
            lastOffRouteWarningTimestamp = now
            announcement = "You appear to have left the walking path. Tap Recalculate to update your route."
        }

        val isApproaching = updatedDistToManeuver <= IMMINENT_MANEUVER_MAX_METERS

        val state = NavigationState.Navigating(
            route = route,
            currentStepIndex = currentStepIndex,
            currentStep = activeStep,
            distanceToNextStepMeters = updatedDistToManeuver,
            distanceToDestinationMeters = distToDestination,
            isApproachingManeuver = isApproaching,
            isOffRoute = isOffRoute,
            offRouteDistanceMeters = currentCrossTrackMeters,
            currentLocation = location,
            isLocationWeak = isLocationWeak
        )
        currentNavigationState = state

        return NavigationEngineResult(
            navigationState = state,
            spokenAnnouncement = announcement,
            isOffRoute = isOffRoute
        )
    }

    private fun getManeuverCoordinate(route: WalkingRoute, stepIdx: Int): RouteCoordinates {
        val step = route.steps.getOrNull(stepIdx)
        if (step != null && route.geometry.isNotEmpty()) {
            val endIdx = step.wayPointEnd.coerceIn(0, route.geometry.lastIndex)
            return route.geometry[endIdx]
        }
        return RouteCoordinates(route.destinationLatitude, route.destinationLongitude)
    }

    // -------------------------------------------------------------
    // Geodesic Mathematics
    // -------------------------------------------------------------

    /**
     * Calculates the Great Circle distance in meters between two coordinates using the Haversine formula.
     */
    fun haversineDistanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val earthRadiusMeters = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)

        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)

        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return earthRadiusMeters * c
    }

    /**
     * Calculates the minimum perpendicular distance from a point to route geometry LineString.
     */
    fun minDistanceToGeometryMeters(userLat: Double, userLon: Double, geometry: List<RouteCoordinates>): Double {
        if (geometry.isEmpty()) return Double.MAX_VALUE
        if (geometry.size == 1) {
            return haversineDistanceMeters(userLat, userLon, geometry[0].latitude, geometry[0].longitude)
        }

        var minDistance = Double.MAX_VALUE
        for (i in 0 until geometry.size - 1) {
            val p1 = geometry[i]
            val p2 = geometry[i + 1]
            val d = distanceToSegmentMeters(userLat, userLon, p1.latitude, p1.longitude, p2.latitude, p2.longitude)
            if (d < minDistance) {
                minDistance = d
            }
        }
        return minDistance
    }

    /**
     * Calculates perpendicular distance from point P to line segment AB.
     */
    fun distanceToSegmentMeters(
        pLat: Double, pLon: Double,
        aLat: Double, aLon: Double,
        bLat: Double, bLon: Double
    ): Double {
        val segmentLength = haversineDistanceMeters(aLat, aLon, bLat, bLon)
        if (segmentLength < 0.1) {
            return haversineDistanceMeters(pLat, pLon, aLat, aLon)
        }

        // Project point P onto segment AB using equirectangular approximation for local distances
        val xP = (pLon - aLon) * cos(Math.toRadians((aLat + bLat) / 2))
        val yP = pLat - aLat
        val xB = (bLon - aLon) * cos(Math.toRadians((aLat + bLat) / 2))
        val yB = bLat - aLat

        val dot = xP * xB + yP * yB
        val lenSq = xB * xB + yB * yB
        val t = max(0.0, min(1.0, dot / lenSq))

        val projLat = aLat + t * (bLat - aLat)
        val projLon = aLon + t * (bLon - aLon)

        return haversineDistanceMeters(pLat, pLon, projLat, projLon)
    }
}
