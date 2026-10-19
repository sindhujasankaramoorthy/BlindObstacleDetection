package com.example.blindobstacledetection.viewmodel

import android.app.Application
import android.speech.SpeechRecognizer
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.blindobstacledetection.location.CurrentLocationProvider
import com.example.blindobstacledetection.location.LocationUiState
import com.example.blindobstacledetection.location.UserLocation
import com.example.blindobstacledetection.tts.SpeechManager
import com.example.blindobstacledetection.voice.DestinationParser
import com.example.blindobstacledetection.voice.DestinationUiState
import com.example.blindobstacledetection.voice.VoiceInputManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel coordinating voice destination input and live GPS location acquisition.
 * Preserves confirmed destination text alongside live UserLocation in memory
 * for consumption in Phase 19 walking-route navigation.
 */
class DestinationViewModel(application: Application) : AndroidViewModel(application) {

    private val voiceInputManager = VoiceInputManager(application)
    private val locationProvider = CurrentLocationProvider(application)
    val speechManager = SpeechManager(application)

    // Phase 17 Destination State
    private val _uiState = MutableStateFlow<DestinationUiState>(DestinationUiState.Idle)
    val uiState: StateFlow<DestinationUiState> = _uiState.asStateFlow()

    private val _confirmedDestination = MutableStateFlow<String?>(null)
    val confirmedDestination: StateFlow<String?> = _confirmedDestination.asStateFlow()

    val isRecognitionAvailable: Boolean
        get() = voiceInputManager.isAvailable()

    // Phase 18 Location State
    private val _locationState = MutableStateFlow<LocationUiState>(LocationUiState.Idle)
    val locationState: StateFlow<LocationUiState> = _locationState.asStateFlow()

    private val _currentLocation = MutableStateFlow<UserLocation?>(null)
    val currentLocation: StateFlow<UserLocation?> = _currentLocation.asStateFlow()

    val isLocationServicesEnabled: Boolean
        get() = locationProvider.isLocationServicesEnabled()

    val hasLocationPermission: Boolean
        get() = locationProvider.hasLocationPermission()

    // -------------------------------------------------------------
    // Voice Destination Actions (Phase 17)
    // -------------------------------------------------------------

    fun onMicrophoneClicked(hasPermission: Boolean, onRequestPermission: () -> Unit) {
        val currentState = _uiState.value

        // If already listening, clicking again cancels/stops listening cleanly
        if (currentState is DestinationUiState.Listening) {
            cancelListening()
            return
        }

        if (!hasPermission) {
            _uiState.value = DestinationUiState.RequestingPermission
            onRequestPermission()
            return
        }

        startVoiceRecognition()
    }

    fun onPermissionResult(isGranted: Boolean) {
        if (isGranted) {
            startVoiceRecognition()
        } else {
            _uiState.value = DestinationUiState.Error(
                message = "Microphone permission is required to speak your destination. Please grant permission in Settings or tap to retry.",
                isPermissionError = true
            )
            speechManager.speakPrompt("Microphone permission was denied. Tap to grant permission.")
        }
    }

    private fun startVoiceRecognition() {
        if (!voiceInputManager.isAvailable()) {
            _uiState.value = DestinationUiState.Error(
                message = "Speech recognition service is not available on this device. Please check Google Voice Services."
            )
            speechManager.speakPrompt("Speech recognition service is unavailable on this device.")
            return
        }

        // Stop any active TTS to ensure the microphone does not transcribe speaker audio
        speechManager.stop()

        _uiState.value = DestinationUiState.Listening(rmsDb = 0f, partialText = "")

        voiceInputManager.startListening(
            listener = object : VoiceInputManager.Listener {
                override fun onReadyForSpeech() {
                    _uiState.value = DestinationUiState.Listening(rmsDb = 0f, partialText = "Listening...")
                }

                override fun onBeginningOfSpeech() {
                    _uiState.value = DestinationUiState.Listening(rmsDb = 0f, partialText = "Listening to speech...")
                }

                override fun onRmsChanged(rmsdB: Float) {
                    val current = _uiState.value
                    if (current is DestinationUiState.Listening) {
                        _uiState.value = current.copy(rmsDb = rmsdB)
                    }
                }

                override fun onPartialResult(partialText: String) {
                    val current = _uiState.value
                    if (current is DestinationUiState.Listening) {
                        _uiState.value = current.copy(partialText = partialText)
                    }
                }

                override fun onFinalResult(recognizedText: String) {
                    val parsed = DestinationParser.parse(recognizedText)
                    if (!parsed.isNullOrBlank()) {
                        _uiState.value = DestinationUiState.Recognized(
                            rawText = recognizedText,
                            parsedDestination = parsed
                        )
                        speechManager.speakPrompt("Destination recognized: $parsed. Please tap Confirm to accept.")
                    } else {
                        _uiState.value = DestinationUiState.Error(
                            message = "Could not identify a destination from \"$recognizedText\". Please speak a place name (e.g. \"Navigate to Central Station\")."
                        )
                        speechManager.speakPrompt("Could not identify destination. Please tap to speak again.")
                    }
                }

                override fun onError(errorMessage: String, errorCode: Int) {
                    val isPermissionError = (errorCode == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS)
                    _uiState.value = DestinationUiState.Error(
                        message = errorMessage,
                        isPermissionError = isPermissionError
                    )
                    speechManager.speakPrompt(errorMessage)
                }

                override fun onEndOfSpeech() {
                    val current = _uiState.value
                    if (current is DestinationUiState.Listening) {
                        _uiState.value = current.copy(partialText = "Processing audio...")
                    }
                }
            }
        )
    }

    fun confirmDestination(destination: String) {
        val trimmed = destination.trim()
        if (trimmed.isNotBlank()) {
            _confirmedDestination.value = trimmed
            _uiState.value = DestinationUiState.Confirmed(trimmed)
            speechManager.speakPrompt("Destination confirmed: $trimmed.")
        }
    }

    fun retryListening() {
        startVoiceRecognition()
    }

    fun cancelListening() {
        voiceInputManager.cancel()
        _uiState.value = DestinationUiState.Idle
    }

    fun clearDestination() {
        voiceInputManager.cancel()
        _confirmedDestination.value = null
        _uiState.value = DestinationUiState.Idle
        speechManager.speakPrompt("Destination cleared.")
    }

    // -------------------------------------------------------------
    // Live Location Actions (Phase 18)
    // -------------------------------------------------------------

    fun requestCurrentLocation(
        hasPermission: Boolean,
        onRequestPermission: () -> Unit
    ) {
        if (!hasPermission) {
            _locationState.value = LocationUiState.RequestingPermission
            onRequestPermission()
            return
        }

        acquireLocationInternal()
    }

    fun onLocationPermissionResult(fineGranted: Boolean, coarseGranted: Boolean) {
        if (fineGranted || coarseGranted) {
            acquireLocationInternal()
        } else {
            _locationState.value = LocationUiState.Error(
                message = "Location permission is required to acquire your current coordinates.",
                isPermissionError = true
            )
            speechManager.speakPrompt("Location permission was denied. Tap to grant permission.")
        }
    }

    private fun acquireLocationInternal() {
        if (!locationProvider.isLocationServicesEnabled()) {
            _locationState.value = LocationUiState.LocationDisabled(
                message = "Location services (GPS) are disabled. Please enable GPS in device settings."
            )
            speechManager.speakPrompt("Location services are disabled. Please enable GPS.")
            return
        }

        viewModelScope.launch {
            _locationState.value = LocationUiState.Loading("Getting your location...")
            speechManager.speakPrompt("Getting your location...")

            val result = locationProvider.getCurrentLocation(timeoutMs = 15000L)
            result.onSuccess { loc ->
                _currentLocation.value = loc
                _locationState.value = LocationUiState.Success(
                    location = loc,
                    message = "Location acquired"
                )
                // Spoken output confirms acquisition without reciting raw coordinate numbers
                speechManager.speakPrompt("Location acquired.")
            }.onFailure { error ->
                if (error is IllegalStateException) {
                    _locationState.value = LocationUiState.LocationDisabled(
                        message = error.localizedMessage ?: "Location services are turned off."
                    )
                    speechManager.speakPrompt("Location services are disabled.")
                } else if (error is SecurityException) {
                    _locationState.value = LocationUiState.Error(
                        message = error.localizedMessage ?: "Location permission missing.",
                        isPermissionError = true
                    )
                    speechManager.speakPrompt("Location permission is missing.")
                } else {
                    _locationState.value = LocationUiState.Error(
                        message = error.localizedMessage ?: "Failed to acquire location. Please try again."
                    )
                    speechManager.speakPrompt("Could not acquire location. Please try again.")
                }
            }
        }
    }

    fun retryLocation() {
        if (locationProvider.hasLocationPermission()) {
            acquireLocationInternal()
        } else {
            _locationState.value = LocationUiState.Error(
                message = "Location permission is required to acquire your coordinates.",
                isPermissionError = true
            )
        }
    }

    fun cancelLocationRequest() {
        locationProvider.cancel()
        _locationState.value = LocationUiState.Idle
    }

    fun clearLocation() {
        locationProvider.cancel()
        _currentLocation.value = null
        _locationState.value = LocationUiState.Idle
        speechManager.speakPrompt("Location cleared.")
    }

    override fun onCleared() {
        super.onCleared()
        locationProvider.cancel()
        voiceInputManager.destroy()
        speechManager.shutdown()
    }
}

