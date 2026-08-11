package com.example.blindobstacledetection.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.blindobstacledetection.database.AppDatabase
import com.example.blindobstacledetection.database.SettingsEntity
import com.example.blindobstacledetection.repository.ObstacleRepository
import com.example.blindobstacledetection.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = SettingsRepository(AppDatabase.getInstance(application).settingsDao())
    private val obstacleRepository = ObstacleRepository()

    private val _settingsState = MutableStateFlow(SettingsEntity())
    val settingsState: StateFlow<SettingsEntity> = _settingsState.asStateFlow()

    private val _connectionTestResult = MutableStateFlow<String?>(null)
    val connectionTestResult: StateFlow<String?> = _connectionTestResult.asStateFlow()

    private val _isTestingConnection = MutableStateFlow(false)
    val isTestingConnection: StateFlow<Boolean> = _isTestingConnection.asStateFlow()

    init {
        viewModelScope.launch {
            repository.settingsFlow.collectLatest { settings ->
                if (settings != null) {
                    _settingsState.value = settings
                } else {
                    repository.getSettings() // Creates default if absent
                }
            }
        }
    }

    fun updateVoiceSpeed(speed: Float) {
        updateAndSave(_settingsState.value.copy(voiceSpeed = speed))
    }

    fun updateVoicePitch(pitch: Float) {
        updateAndSave(_settingsState.value.copy(voicePitch = pitch))
    }

    fun updateLanguage(language: String) {
        updateAndSave(_settingsState.value.copy(language = language))
    }

    fun updateTheme(theme: String) {
        updateAndSave(_settingsState.value.copy(theme = theme))
    }

    fun updateDetectionInterval(intervalMs: Long) {
        updateAndSave(_settingsState.value.copy(detectionIntervalMs = intervalMs))
    }

    fun updateConfidenceThreshold(threshold: Float) {
        updateAndSave(_settingsState.value.copy(confidenceThreshold = threshold))
    }

    fun updateSpeechCooldown(cooldownSec: Int) {
        updateAndSave(_settingsState.value.copy(speechCooldownSeconds = cooldownSec))
    }

    fun updateBaseUrl(url: String) {
        updateAndSave(_settingsState.value.copy(baseUrl = url))
    }

    fun updateVibrationEnabled(enabled: Boolean) {
        updateAndSave(_settingsState.value.copy(vibrationEnabled = enabled))
    }

    fun updateUseOfflineMode(enabled: Boolean) {
        updateAndSave(_settingsState.value.copy(useOfflineMode = enabled))
    }

    fun testBackendUrl(url: String = _settingsState.value.baseUrl) {
        viewModelScope.launch {
            _isTestingConnection.value = true
            _connectionTestResult.value = "Connecting to $url..."
            val result = obstacleRepository.checkBackendStatus(url)
            if (result.isSuccess) {
                _connectionTestResult.value = "Success! Connected to Backend Server."
            } else {
                _connectionTestResult.value = "Connection Failed: ${result.exceptionOrNull()?.localizedMessage}"
            }
            _isTestingConnection.value = false
        }
    }

    private fun updateAndSave(newSettings: SettingsEntity) {
        _settingsState.value = newSettings
        viewModelScope.launch {
            repository.updateSettings(newSettings)
        }
    }
}
