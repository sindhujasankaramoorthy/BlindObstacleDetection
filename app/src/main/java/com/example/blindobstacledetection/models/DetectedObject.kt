package com.example.blindobstacledetection.models

import com.google.gson.annotations.SerializedName

data class DetectedObject(
    @SerializedName("name") val name: String,
    @SerializedName("confidence") val confidence: Float,
    @SerializedName("direction") val direction: String, // Left, Center, Right
    @SerializedName("fine_direction") val fineDirection: String? = null, // Far Left, Slight Left, etc.
    @SerializedName("distance") val distance: String, // Very Close, Close, Medium, Far
    @SerializedName("priority") val priority: String? = "NORMAL", // HIGH or NORMAL
    @SerializedName("bbox") val bbox: List<Float>? = null, // [x1, y1, x2, y2]
    @SerializedName("norm_bbox") val normBbox: List<Float>? = null // [x1, y1, x2, y2] normalized 0..1
) {
    val isHighPriority: Boolean
        get() = priority.equals("HIGH", ignoreCase = true) ||
                HIGH_PRIORITY_CLASSES.contains(name.lowercase())

    companion object {
        val HIGH_PRIORITY_CLASSES = setOf(
            "person", "car", "bus", "truck", "motorcycle", "bicycle",
            "stairs", "pole", "traffic light", "fire hydrant", "stop sign", "train"
        )
    }
}
