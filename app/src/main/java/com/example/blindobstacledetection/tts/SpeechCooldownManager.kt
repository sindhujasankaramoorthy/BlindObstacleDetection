package com.example.blindobstacledetection.tts

import java.util.concurrent.ConcurrentHashMap

class SpeechCooldownManager {

    // Maps announcement key (e.g. "chair_Left") to timestamp (ms)
    private val lastSpokenTimestamps = ConcurrentHashMap<String, Long>()

    // Tracks last distance category for object key to trigger alert if object gets dangerously closer
    private val lastDistanceState = ConcurrentHashMap<String, String>()

    fun shouldAnnounce(
        objectName: String,
        direction: String,
        distance: String,
        isHighPriority: Boolean,
        cooldownSeconds: Int = 5
    ): Boolean {
        val now = System.currentTimeMillis()
        val key = "${objectName.lowercase()}_${direction.lowercase()}"
        val lastTime = lastSpokenTimestamps[key] ?: 0L
        val previousDistance = lastDistanceState[key]
        val cooldownMillis = cooldownSeconds * 1000L

        // High priority objects always bypass normal cooldown if time > 2.5s or distance escalated
        if (isHighPriority) {
            if (now - lastTime > 2500L || distance == "Very Close") {
                lastSpokenTimestamps[key] = now
                lastDistanceState[key] = distance
                return true
            }
        }

        // If distance changed to "Very Close", announce immediately
        if (distance == "Very Close" && previousDistance != "Very Close") {
            lastSpokenTimestamps[key] = now
            lastDistanceState[key] = distance
            return true
        }

        // Check standard cooldown
        if (now - lastTime >= cooldownMillis) {
            lastSpokenTimestamps[key] = now
            lastDistanceState[key] = distance
            return true
        }

        return false
    }

    fun reset() {
        lastSpokenTimestamps.clear()
        lastDistanceState.clear()
    }
}
