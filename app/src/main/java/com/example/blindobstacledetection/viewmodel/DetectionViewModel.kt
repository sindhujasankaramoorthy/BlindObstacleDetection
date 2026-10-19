package com.example.blindobstacledetection.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.blindobstacledetection.database.AppDatabase
import com.example.blindobstacledetection.database.DetectionHistoryEntity
import com.example.blindobstacledetection.models.DetectedObject
import com.example.blindobstacledetection.repository.HistoryRepository
import com.example.blindobstacledetection.repository.ObstacleRepository
import com.example.blindobstacledetection.repository.SettingsRepository
import com.example.blindobstacledetection.tts.SpeechManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DetectionViewModel(application: Application) : AndroidViewModel(application) {

    private val obstacleRepository = ObstacleRepository()
    private val historyRepository = HistoryRepository(AppDatabase.getInstance(application).detectionHistoryDao())
    private val settingsRepository = SettingsRepository(AppDatabase.getInstance(application).settingsDao())

    val speechManager = SpeechManager.getInstance(application)

    private val _detectedObjects = MutableStateFlow<List<DetectedObject>>(emptyList())
    val detectedObjects: StateFlow<List<DetectedObject>> = _detectedObjects.asStateFlow()

    private val _statusText = MutableStateFlow("Initializing Camera...")
    val statusText: StateFlow<String> = _statusText.asStateFlow()

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    private val _fps = MutableStateFlow(0)
    val fps: StateFlow<Int> = _fps.asStateFlow()

    private val _isDetectionPaused = MutableStateFlow(false)
    val isDetectionPaused: StateFlow<Boolean> = _isDetectionPaused.asStateFlow()

    private val _isVoiceMuted = MutableStateFlow(false)
    val isVoiceMuted: StateFlow<Boolean> = _isVoiceMuted.asStateFlow()

    private val _isFlashOn = MutableStateFlow(false)
    val isFlashOn: StateFlow<Boolean> = _isFlashOn.asStateFlow()

    private val _useFrontCamera = MutableStateFlow(false)
    val useFrontCamera: StateFlow<Boolean> = _useFrontCamera.asStateFlow()

    private val _cameraError = MutableStateFlow<String?>(null)
    val cameraError: StateFlow<String?> = _cameraError.asStateFlow()

    // Configurable parameters from Settings
    private var baseUrl = "http://192.168.1.40:8000/"
    private var confidenceThreshold = 0.50f
    private var speechCooldownSeconds = 5
    private var vibrationEnabled = true
    private var detectionIntervalMs = 1000L
    private var useOfflineMode = false

    private val onDeviceDetector = com.example.blindobstacledetection.utils.OnDeviceDetector(application)

    private var lastFrameTimestamp = 0L
    private var frameCount = 0
    private var lastFpsCalculationTime = System.currentTimeMillis()

    init {
        loadSettings()
        viewModelScope.launch(Dispatchers.IO) {
            onDeviceDetector.initialize()
        }
    }

    private fun loadSettings() {
        viewModelScope.launch {
            settingsRepository.settingsFlow.collectLatest { settings ->
                if (settings != null) {
                    baseUrl = settings.baseUrl
                    confidenceThreshold = settings.confidenceThreshold
                    speechCooldownSeconds = settings.speechCooldownSeconds
                    vibrationEnabled = settings.vibrationEnabled
                    detectionIntervalMs = settings.detectionIntervalMs
                    useOfflineMode = settings.useOfflineMode

                    speechManager.updateSpeechConfig(
                        rate = settings.voiceSpeed,
                        pitch = settings.voicePitch,
                        languageTag = settings.language
                    )
                }
            }
        }
    }

    fun processFrame(jpegBytes: ByteArray) {
        if (_isDetectionPaused.value || _isProcessing.value) return

        val now = System.currentTimeMillis()
        if (now - lastFrameTimestamp < detectionIntervalMs) return
        lastFrameTimestamp = now

        // Calculate FPS
        frameCount++
        if (now - lastFpsCalculationTime >= 1000) {
            _fps.value = frameCount
            frameCount = 0
            lastFpsCalculationTime = now
        }

        viewModelScope.launch(Dispatchers.IO) {
            _isProcessing.value = true
            _statusText.value = if (useOfflineMode) "Analyzing (On-Device AI)..." else "Analyzing Frame..."

            val result = if (useOfflineMode) {
                Result.success(onDeviceDetector.detect(jpegBytes, confidenceThreshold))
            } else {
                val remoteResult = obstacleRepository.detectObstacles(
                    jpegBytes = jpegBytes,
                    baseUrl = baseUrl,
                    confThreshold = confidenceThreshold
                )
                if (remoteResult.isFailure) {
                    // Fallback to local on-device detector if backend fails
                    Result.success(onDeviceDetector.detect(jpegBytes, confidenceThreshold))
                } else {
                    remoteResult
                }
            }

            result.onSuccess { response ->
                val objects = response.objects.filter { it.confidence >= confidenceThreshold }
                _detectedObjects.value = objects

                if (objects.isEmpty()) {
                    _statusText.value = if (useOfflineMode) "No obstacle detected (Offline)" else "No obstacle detected"
                } else {
                    val summary = objects.take(3).joinToString(", ") { "${it.name} (${it.direction}, ${it.distance})" }
                    _statusText.value = summary

                    // Announce via SpeechManager if voice not muted
                    if (!_isVoiceMuted.value) {
                        withContext(Dispatchers.Main) {
                            speechManager.announceObstacles(
                                objects = objects,
                                cooldownSeconds = speechCooldownSeconds,
                                vibrationEnabled = vibrationEnabled
                            )
                        }
                    }

                    // Save history asynchronously
                    saveHistory(objects)
                }
            }.onFailure { error ->
                Log.e("DetectionViewModel", "Detection Error: ${error.message}")
                _statusText.value = "Error: ${error.localizedMessage}"
            }

            _isProcessing.value = false
        }
    }

    private suspend fun saveHistory(objects: List<DetectedObject>) {
        try {
            val entities = objects.map { obj ->
                DetectionHistoryEntity(
                    objectName = obj.name,
                    confidence = obj.confidence,
                    direction = obj.direction,
                    distance = obj.distance,
                    priority = obj.priority ?: "NORMAL"
                )
            }
            historyRepository.insertAll(entities)
        } catch (e: Exception) {
            Log.w("DetectionViewModel", "Failed to save history: ${e.message}")
        }
    }

    fun togglePauseDetection() {
        _isDetectionPaused.value = !_isDetectionPaused.value
        _statusText.value = if (_isDetectionPaused.value) "Detection Paused" else "Detection Active"
    }

    fun toggleVoiceMute() {
        _isVoiceMuted.value = !_isVoiceMuted.value
        if (_isVoiceMuted.value) {
            speechManager.stop()
        } else {
            speechManager.speakDirect("Voice announcements enabled")
        }
    }

    fun toggleFlash() {
        _isFlashOn.value = !_isFlashOn.value
    }

    fun toggleCameraLens() {
        _useFrontCamera.value = !_useFrontCamera.value
    }

    fun setCameraError(error: String?) {
        _cameraError.value = error
        if (error != null) {
            _statusText.value = error
            speechManager.speakPrompt("Camera error. Obstacle detection is currently unavailable.")
        }
    }

    override fun onCleared() {
        super.onCleared()
        speechManager.stop()
    }
}
