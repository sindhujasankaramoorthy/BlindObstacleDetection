package com.example.blindobstacledetection.navigation.models

/**
 * Sealed hierarchy representing the UI state of walking route calculation.
 */
sealed interface RouteUiState {

    /**
     * No active route calculation; idle state.
     */
    data object Idle : RouteUiState

    /**
     * Walking route calculation in progress.
     *
     * @param stage Description of the current asynchronous stage (e.g. "Locating destination...", "Calculating walking path...").
     */
    data class Loading(
        val stage: String = "Calculating walking route..."
    ) : RouteUiState

    /**
     * Walking route calculation completed successfully.
     *
     * @param route The verified pedestrian walking route.
     */
    data class Success(
        val route: WalkingRoute
    ) : RouteUiState

    /**
     * API key for the routing provider is missing or unconfigured.
     * Prompts the user or developer with clear instructions.
     */
    data class MissingApiKey(
        val message: String = "OpenRouteService API key not configured. Add ORS_API_KEY in local.properties or provide a key to plan walking routes."
    ) : RouteUiState

    /**
     * An error occurred during route calculation or geocoding.
     *
     * @param message Human-readable error message.
     * @param canRetry Whether the user can tap to retry the calculation.
     * @param isLocationError True if the error is caused by missing GPS / location permissions.
     * @param isDestinationError True if the error is caused by an unresolvable destination name.
     */
    data class Error(
        val message: String,
        val canRetry: Boolean = true,
        val isLocationError: Boolean = false,
        val isDestinationError: Boolean = false
    ) : RouteUiState
}
