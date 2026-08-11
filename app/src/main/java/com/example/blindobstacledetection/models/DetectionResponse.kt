package com.example.blindobstacledetection.models

import com.google.gson.annotations.SerializedName

data class DetectionResponse(
    @SerializedName("count") val count: Int,
    @SerializedName("objects") val objects: List<DetectedObject>,
    @SerializedName("image_width") val imageWidth: Int? = 640,
    @SerializedName("image_height") val imageHeight: Int? = 480,
    @SerializedName("processing_time_ms") val processingTimeMs: Float? = 0f,
    @SerializedName("error") val error: String? = null
)
