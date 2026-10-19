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
import com.example.blindobstacledetection.navigation.NavigationConfig
import com.example.blindobstacledetection.navigation.geocoding.GeocodingRepository
import com.example.blindobstacledetection.navigation.geocoding.NominatimGeocodingRepository
import com.example.blindobstacledetection.navigation.models.RouteUiState
import com.example.blindobstacledetection.navigation.models.WalkingRoute
import com.example.blindobstacledetection.navigation.routing.MissingApiKeyException
import com.example.blindobstacledetection.navigation.routing.OpenRouteServiceWalkingRepository
import com.example.blindobstacledetection.navigation.routing.RouteNotFoundException
import com.example.blindobstacledetection.navigation.routing.WalkingRouteRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

import com.example.blindobstacledetection.navigation.models.NavigationState
import com.example.blindobstacledetection.navigation.TurnByTurnNavigationEngine
import com.example.blindobstacledetection.navigation.geocoding.RegionalPlaceNormalizer
import java.util.Locale

/**
 * ViewModel coordinating voice destination input (Phase 17), live GPS location
 * acquisition (Phase 18), pedestrian walking route generation (Phase 19),
 * and spoken turn-by-turn navigation with obstacle alert coordination (Phase 20).
 *
 * Coordinates data flows without blocking real-time camera inference or audio alerts.
 */
class DestinationViewModel @JvmOverloads constructor(
    application: Application,
    private val geocodingRepository: GeocodingRepository = NominatimGeocodingRepository(),
    private val walkingRouteRepository: WalkingRouteRepository = OpenRouteServiceWalkingRepository()
) : AndroidViewModel(application) {

    private val voiceInputManager = VoiceInputManager(application)
    private val locationProvider = CurrentLocationProvider(application)
    val speechManager: SpeechManager = SpeechManager.getInstance(application)
    val navigationEngine = TurnByTurnNavigationEngine()

    enum class VoiceLanguage(val locale: Locale, val label: String) {
        ENGLISH_INDIA(Locale.forLanguageTag("en-IN"), "English (India)"),
        TAMIL_INDIA(Locale.forLanguageTag("ta-IN"), "தமிழ் (Tamil)")
    }

    private val _voiceLanguage = MutableStateFlow(VoiceLanguage.ENGLISH_INDIA)
    val voiceLanguage: StateFlow<VoiceLanguage> = _voiceLanguage.asStateFlow()

    fun setVoiceLanguage(language: VoiceLanguage) {
        _voiceLanguage.value = language
        val msg = if (language == VoiceLanguage.TAMIL_INDIA) {
            "Voice language set to Tamil. நீங்கள் தமிழில் பேசலாம்."
        } else {
            "Voice language set to English India."
        }
        speechManager.speakPrompt(msg)
    }

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

    // Phase 19 Walking Route State
    private val _routeState = MutableStateFlow<RouteUiState>(RouteUiState.Idle)
    val routeState: StateFlow<RouteUiState> = _routeState.asStateFlow()

    private val _currentRoute = MutableStateFlow<WalkingRoute?>(null)
    val currentRoute: StateFlow<WalkingRoute?> = _currentRoute.asStateFlow()

    private var routePlanningJob: Job? = null

    // Phase 20 Turn-by-Turn Navigation State
    private val _navigationState = MutableStateFlow<NavigationState>(NavigationState.NoDestination)
    val navigationState: StateFlow<NavigationState> = _navigationState.asStateFlow()

    private val _isNavigationMuted = MutableStateFlow(false)
    val isNavigationMuted: StateFlow<Boolean> = _isNavigationMuted.asStateFlow()

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
            languageLocale = _voiceLanguage.value.locale,
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
                        val romanized = RegionalPlaceNormalizer.getRomanizedName(parsed)
                        val speechFeedback = if (romanized != null) {
                            "Destination recognized: $parsed, $romanized. Please tap Confirm to accept."
                        } else {
                            "Destination recognized: $parsed. Please tap Confirm to accept."
                        }
                        speechManager.speakPrompt(speechFeedback)
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
            _navigationState.value = NavigationState.DestinationConfirmed(trimmed)
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
        // Stale route for a previous destination must be cleared
        clearRoute()
        _navigationState.value = NavigationState.NoDestination
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
        // Invalidate current route since origin location was cleared
        clearRoute()
        speechManager.speakPrompt("Location cleared.")
    }

    // -------------------------------------------------------------
    // Walking Route Planning Actions (Phase 19)
    // -------------------------------------------------------------

    /**
     * Plans a pedestrian walking route using the confirmed destination from Phase 17
     * and the actual acquired GPS location from Phase 18.
     *
     * Prevents duplicate requests, validates readiness, resolves destination via geocoding,
     * and calculates real pedestrian directions.
     */
    fun planWalkingRoute() {
        // Prevent duplicate calls if already calculating
        if (_routeState.value is RouteUiState.Loading) {
            return
        }

        val location = _currentLocation.value
        if (location == null) {
            _routeState.value = RouteUiState.Error(
                message = "Current GPS location not acquired yet. Please tap \"Get Current Location\" above.",
                isLocationError = true
            )
            speechManager.speakPrompt("Current location is not available. Please acquire your location first.")
            return
        }

        val destination = _confirmedDestination.value
        if (destination.isNullOrBlank()) {
            _routeState.value = RouteUiState.Error(
                message = "Destination not confirmed yet. Please speak and confirm a destination above.",
                isDestinationError = true
            )
            speechManager.speakPrompt("Destination is not set. Please speak and confirm your destination first.")
            return
        }

        if (!NavigationConfig.isConfigured) {
            _routeState.value = RouteUiState.MissingApiKey()
            speechManager.speakPrompt("OpenRouteService API key is missing. Please configure your API key.")
            return
        }

        // Cancel any previous calculation before launching a new request
        routePlanningJob?.cancel()

        routePlanningJob = viewModelScope.launch {
            _routeState.value = RouteUiState.Loading("Resolving destination: \"$destination\"...")
            speechManager.speakPrompt("Planning walking route to $destination...")

            val geocodeResult = geocodingRepository.geocode(
                query = destination,
                proximityLat = location.latitude,
                proximityLon = location.longitude
            )

            geocodeResult.onFailure { error ->
                val errorMsg = error.localizedMessage ?: "Could not resolve destination location."
                _routeState.value = RouteUiState.Error(
                    message = errorMsg,
                    isDestinationError = true
                )
                speechManager.speakPrompt("Could not locate destination. Please check the destination name.")
                return@launch
            }

            val geocoded = geocodeResult.getOrNull()!!
            _routeState.value = RouteUiState.Loading("Calculating walking route to ${geocoded.shortName}...")

            val routeResult = walkingRouteRepository.calculateWalkingRoute(
                originLat = location.latitude,
                originLon = location.longitude,
                destLat = geocoded.latitude,
                destLon = geocoded.longitude,
                destinationName = geocoded.shortName
            )

            routeResult.onSuccess { route ->
                _currentRoute.value = route
                _routeState.value = RouteUiState.Success(route)
                _navigationState.value = NavigationState.RouteReady(route)
                // Spoken confirmation of route summary with clear prompt to start guidance
                speechManager.speakPrompt(
                    "Walking route planned. Total distance ${route.formattedDistance}, estimated duration ${route.formattedDuration}. Tap Start Navigation to begin guidance."
                )
            }.onFailure { error ->
                when (error) {
                    is MissingApiKeyException -> {
                        _routeState.value = RouteUiState.MissingApiKey(
                            error.message ?: "OpenRouteService API key not configured."
                        )
                        speechManager.speakPrompt("Routing API key is missing.")
                    }
                    is RouteNotFoundException -> {
                        _routeState.value = RouteUiState.Error(
                            message = error.localizedMessage ?: "No walking route found between these locations.",
                            canRetry = true
                        )
                        speechManager.speakPrompt("No walking route could be found to this destination.")
                    }
                    else -> {
                        val message = error.localizedMessage ?: "Failed to calculate walking route."
                        _routeState.value = RouteUiState.Error(
                            message = message,
                            canRetry = true
                        )
                        speechManager.speakPrompt("Could not calculate walking route. Please try again.")
                    }
                }
            }
        }
    }

    fun retryRoutePlanning() {
        planWalkingRoute()
    }

    fun cancelRoutePlanning() {
        routePlanningJob?.cancel()
        routePlanningJob = null
        if (_routeState.value is RouteUiState.Loading) {
            _routeState.value = RouteUiState.Idle
            speechManager.speakPrompt("Route planning cancelled.")
        }
    }

    fun clearRoute() {
        stopNavigation()
        routePlanningJob?.cancel()
        routePlanningJob = null
        _currentRoute.value = null
        _routeState.value = RouteUiState.Idle
        val dest = _confirmedDestination.value
        _navigationState.value = if (!dest.isNullOrBlank()) NavigationState.DestinationConfirmed(dest) else NavigationState.NoDestination
    }

    fun setApiKey(apiKey: String) {
        NavigationConfig.setRuntimeApiKey(apiKey)
        if (apiKey.isNotBlank()) {
            speechManager.speakPrompt("API key configured.")
            if (_routeState.value is RouteUiState.MissingApiKey) {
                planWalkingRoute()
            }
        }
    }

    // -------------------------------------------------------------
    // Turn-by-Turn Spoken Navigation Actions (Phase 20)
    // -------------------------------------------------------------

    /**
     * Starts spoken turn-by-turn navigation for the verified walking route.
     * Starts continuous GPS location tracking and speaks initial departure maneuver.
     */
    fun startNavigation() {
        val route = _currentRoute.value
        if (route == null) {
            speechManager.speakPrompt("No route ready to start. Please plan a walking route first.")
            return
        }

        if (!locationProvider.hasLocationPermission()) {
            speechManager.speakPrompt("Location permission is required to start navigation.")
            _navigationState.value = NavigationState.Error("Location permission required to navigate.", canRetry = true)
            return
        }

        if (!locationProvider.isLocationServicesEnabled()) {
            speechManager.speakPrompt("Device location is turned off. Please turn on GPS in settings.")
            _navigationState.value = NavigationState.Error("Device GPS is turned off. Please enable GPS.", canRetry = true)
            return
        }

        val startResult = navigationEngine.start(route)
        _navigationState.value = startResult.navigationState
        startResult.spokenAnnouncement?.let { speechManager.speakNavigation(it) }

        locationProvider.startNavigationUpdates(intervalMs = 2500L, minDistanceMeters = 1.5f) { loc ->
            onNavigationLocationUpdate(loc)
        }
    }

    /**
     * Processes live GPS location updates during active navigation.
     * Evaluates progress against route steps, announces maneuvers, and detects off-route deviation.
     */
    fun onNavigationLocationUpdate(location: UserLocation) {
        _currentLocation.value = location
        val update = navigationEngine.onLocationUpdate(location) ?: return
        _navigationState.value = update.navigationState

        update.spokenAnnouncement?.let {
            speechManager.speakNavigation(it)
        }

        if (update.isArrived) {
            locationProvider.stopNavigationUpdates()
        }
    }

    /**
     * Pauses turn-by-turn navigation. Suspends continuous GPS tracking and cancels active navigation speech.
     */
    fun pauseNavigation() {
        val pausedState = navigationEngine.pause() ?: return
        _navigationState.value = pausedState
        locationProvider.stopNavigationUpdates()
        speechManager.cancelNavigationSpeech()
        speechManager.speakPrompt("Navigation paused.")
    }

    /**
     * Resumes turn-by-turn navigation from the current step.
     */
    fun resumeNavigation() {
        val navigatingState = navigationEngine.resume() ?: return
        _navigationState.value = navigatingState
        speechManager.speakPrompt("Resuming navigation.")

        locationProvider.startNavigationUpdates(intervalMs = 2500L, minDistanceMeters = 1.5f) { loc ->
            onNavigationLocationUpdate(loc)
        }
    }

    /**
     * Stops turn-by-turn navigation completely, releases continuous location updates,
     * and clears any pending navigation speech.
     */
    fun stopNavigation() {
        navigationEngine.stop()
        locationProvider.stopNavigationUpdates()
        speechManager.cancelNavigationSpeech()
        val route = _currentRoute.value
        _navigationState.value = if (route != null) NavigationState.RouteReady(route) else NavigationState.NoDestination
        speechManager.speakPrompt("Navigation stopped.")
    }

    /**
     * Toggles muting of routine navigation maneuver speech.
     * Critical and important obstacle warnings remain completely unmuted and active for safety.
     */
    fun toggleNavigationMute() {
        val newMuted = !speechManager.isNavigationMuted
        speechManager.isNavigationMuted = newMuted
        _isNavigationMuted.value = newMuted
        if (newMuted) {
            speechManager.speakPrompt("Navigation speech muted. Obstacle alerts remain active.")
        } else {
            speechManager.speakPrompt("Navigation speech unmuted.")
        }
    }

    /**
     * Recalculates route when user has deviated off-path or needs a new route.
     */
    fun recalculateRoute() {
        speechManager.cancelNavigationSpeech()
        speechManager.speakPrompt("Recalculating walking route...")
        planWalkingRoute()
    }

    override fun onCleared() {
        super.onCleared()
        stopNavigation()
        routePlanningJob?.cancel()
        locationProvider.cancel()
        voiceInputManager.destroy()
        speechManager.stop()
    }
}

