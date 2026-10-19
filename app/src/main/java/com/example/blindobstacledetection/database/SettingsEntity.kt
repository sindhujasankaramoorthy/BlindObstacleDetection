package com.example.blindobstacledetection.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "app_settings")
data class SettingsEntity(
    @PrimaryKey val id: Int = 1,
    val voiceSpeed: Float = 1.0f,
    val voicePitch: Float = 1.0f,
    val language: String = "en-US",
    val theme: String = "System", // Light, Dark, System
    val detectionIntervalMs: Long = 1000L, // 500, 1000, 2000
    val confidenceThreshold: Float = 0.50f, // 0.10 to 0.90
    val speechCooldownSeconds: Int = 5,
    val baseUrl: String = "http://192.168.1.40:8000/",
    val vibrationEnabled: Boolean = true,
    val highPriorityOnly: Boolean = false,
    val cameraResolution: String = "Medium", // Low, Medium, High
    val useOfflineMode: Boolean = false
)
