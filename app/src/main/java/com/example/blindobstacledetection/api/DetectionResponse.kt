package com.example.blindobstacledetection.api

data class DetectionResponse(
    val count: Int,
    val objects: List<DetectedObject>
)

data class DetectedObject(
    val name: String,
    val confidence: Double
)