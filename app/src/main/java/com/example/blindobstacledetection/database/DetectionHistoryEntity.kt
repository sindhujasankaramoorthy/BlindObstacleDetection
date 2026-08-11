package com.example.blindobstacledetection.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "detection_history")
data class DetectionHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val objectName: String,
    val confidence: Float,
    val direction: String,
    val distance: String,
    val priority: String,
    val timestamp: Long = System.currentTimeMillis()
)
