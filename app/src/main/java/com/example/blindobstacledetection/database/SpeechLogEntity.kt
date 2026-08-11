package com.example.blindobstacledetection.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "speech_logs")
data class SpeechLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val spokenText: String,
    val isInterrupted: Boolean,
    val priority: String,
    val timestamp: Long = System.currentTimeMillis()
)
