package com.example.blindobstacledetection.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.blindobstacledetection.api.NetworkConnectivityObserver
import com.example.blindobstacledetection.database.AppDatabase
import com.example.blindobstacledetection.repository.ObstacleRepository
import com.example.blindobstacledetection.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val obstacleRepository = ObstacleRepository()
    private val settingsRepository = SettingsRepository(AppDatabase.getInstance(application).settingsDao())
    private val connectivityObserver = NetworkConnectivityObserver(application)

    private val _isBackendOnline = MutableStateFlow<Boolean?>(null)
    val isBackendOnline: StateFlow<Boolean?> = _isBackendOnline.asStateFlow()

    private val _isInternetAvailable = MutableStateFlow<Boolean>(true)
    val isInternetAvailable: StateFlow<Boolean> = _isInternetAvailable.asStateFlow()

    private val _baseUrl = MutableStateFlow("http://192.168.1.40:8000/")
    val baseUrl: StateFlow<String> = _baseUrl.asStateFlow()

    private val _isCheckingBackend = MutableStateFlow(false)
    val isCheckingBackend: StateFlow<Boolean> = _isCheckingBackend.asStateFlow()

    init {
        observeNetwork()
        loadSettingsAndCheckBackend()
    }

    private fun observeNetwork() {
        viewModelScope.launch {
            connectivityObserver.observe().collectLatest { status ->
                _isInternetAvailable.value = (status == NetworkConnectivityObserver.Status.Available)
            }
        }
    }

    fun loadSettingsAndCheckBackend() {
        viewModelScope.launch {
            val settings = settingsRepository.getSettings()
            _baseUrl.value = settings.baseUrl
            checkBackendConnection(settings.baseUrl)
        }
    }

    fun checkBackendConnection(url: String = _baseUrl.value) {
        viewModelScope.launch {
            _isCheckingBackend.value = true
            val result = obstacleRepository.checkBackendStatus(url)
            _isBackendOnline.value = result.isSuccess
            _isCheckingBackend.value = false
        }
    }
}
