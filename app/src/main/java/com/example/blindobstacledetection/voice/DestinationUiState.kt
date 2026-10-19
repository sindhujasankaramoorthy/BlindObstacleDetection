package com.example.blindobstacledetection.voice

/**
 * UI State representing the voice destination input flow.
 */
sealed interface DestinationUiState {

    /**
     * Initial idle state. Ready to start voice recognition.
     */
    object Idle : DestinationUiState

    /**
     * Prompting the user for microphone runtime permission.
     */
    object RequestingPermission : DestinationUiState

    /**
     * Microphone is actively listening for spoken input.
     * @param rmsDb Audio volume level for visual soundwave indicator.
     * @param partialText Real-time partial transcription.
     */
    data class Listening(
        val rmsDb: Float = 0f,
        val partialText: String = ""
    ) : DestinationUiState

    /**
     * Speech completed and successfully parsed. Awaiting user confirmation.
     * @param rawText The full raw transcribed speech.
     * @param parsedDestination The extracted destination place name.
     */
    data class Recognized(
        val rawText: String,
        val parsedDestination: String
    ) : DestinationUiState

    /**
     * The user explicitly confirmed the destination.
     * Available in memory for consumption by Phase 18 navigation.
     * @param destination The confirmed destination name.
     */
    data class Confirmed(
        val destination: String
    ) : DestinationUiState

    /**
     * An error occurred during recognition or permission was denied.
     * @param message Human-readable error description.
     * @param isPermissionError True if the error is due to missing microphone permission.
     */
    data class Error(
        val message: String,
        val isPermissionError: Boolean = false
    ) : DestinationUiState
}
