package com.example.blindobstacledetection.navigation.models

import com.example.blindobstacledetection.location.UserLocation

/**
 * Complete, explicit navigation lifecycle state for turn-by-turn walking guidance.
 * Coordinates user actions, location tracking, and speech arbiter announcements.
 */
sealed interface NavigationState {

    /**
     * Initial idle state: no destination selected.
     */
    data object NoDestination : NavigationState

    /**
     * Voice destination confirmed from Phase 17; waiting for route generation.
     */
    data class DestinationConfirmed(val destination: String) : NavigationState

    /**
     * Waiting for GPS location fix from Phase 18.
     */
    data class WaitingForLocation(val destination: String) : NavigationState

    /**
     * Pedestrian walking route is being calculated by routing provider.
     */
    data class PlanningRoute(val destination: String) : NavigationState

    /**
     * Walking route is ready for display and inspection; user has not yet started navigation.
     * (Navigation does NOT start automatically without user consent).
     */
    data class RouteReady(val route: WalkingRoute) : NavigationState

    /**
     * Turn-by-turn walking navigation is active.
     * Location progress, step advancement, and maneuver announcements are active.
     */
    data class Navigating(
        val route: WalkingRoute,
        val currentStepIndex: Int,
        val currentStep: RouteStep,
        val distanceToNextStepMeters: Double,
        val distanceToDestinationMeters: Double,
        val isApproachingManeuver: Boolean = false,
        val isOffRoute: Boolean = false,
        val offRouteDistanceMeters: Double = 0.0,
        val currentLocation: UserLocation? = null,
        val isLocationWeak: Boolean = false
    ) : NavigationState {
        val totalSteps: Int
            get() = route.steps.size

        val distanceToNextManeuverMeters: Double
            get() = distanceToNextStepMeters

        val remainingDistanceMeters: Double
            get() = distanceToDestinationMeters

        val formattedDistanceToNextStep: String
            get() = when {
                distanceToNextStepMeters < 5.0 -> "Immediately"
                distanceToNextStepMeters < 1000.0 -> "${kotlin.math.round(distanceToNextStepMeters).toInt()} m"
                else -> "%.1f km".format(distanceToNextStepMeters / 1000.0)
            }

        val formattedRemainingDistance: String
            get() = when {
                distanceToDestinationMeters < 1000.0 -> "${kotlin.math.round(distanceToDestinationMeters).toInt()} m"
                else -> "%.1f km".format(distanceToDestinationMeters / 1000.0)
            }
    }

    /**
     * Navigation paused by user; GPS tracking suspended.
     */
    data class Paused(
        val route: WalkingRoute,
        val currentStepIndex: Int,
        val currentStep: RouteStep
    ) : NavigationState

    /**
     * Destination reached successfully.
     */
    data class Completed(
        val route: WalkingRoute
    ) : NavigationState {
        val destinationName: String
            get() = route.destinationName
    }

    /**
     * An error occurred (e.g. route failure, GPS unavailable, deviation unrecoverable).
     */
    data class Error(
        val message: String,
        val canRetry: Boolean = true
    ) : NavigationState
}
