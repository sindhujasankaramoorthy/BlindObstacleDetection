package com.example.blindobstacledetection.location

/**
 * UI State representing the real-time location acquisition workflow.
 */
sealed interface LocationUiState {

    /**
     * Initial idle state. Location has not been requested yet.
     */
    object Idle : LocationUiState

    /**
     * Runtime location permission request is in flight.
     */
    object RequestingPermission : LocationUiState

    /**
     * Actively acquiring current location via GPS/Fused provider.
     */
    data class Loading(
        val message: String = "Getting your location..."
    ) : LocationUiState

    /**
     * Current location successfully acquired.
     * @param location Acquired user coordinates, accuracy, and timestamp.
     * @param message Assistive status label ("Location acquired").
     */
    data class Success(
        val location: UserLocation,
        val message: String = "Location acquired"
    ) : LocationUiState

    /**
     * Device location services (GPS toggle) are disabled.
     */
    data class LocationDisabled(
        val message: String = "Location services (GPS) are disabled. Please enable location in your device settings."
    ) : LocationUiState

    /**
     * Failed to acquire location due to permission denial, timeout, or hardware unavailability.
     * @param message Human-readable error description.
     * @param isPermissionError True if missing ACCESS_FINE_LOCATION or ACCESS_COARSE_LOCATION.
     */
    data class Error(
        val message: String,
        val isPermissionError: Boolean = false
    ) : LocationUiState
}
