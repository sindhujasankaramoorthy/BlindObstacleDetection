package com.example.blindobstacledetection.tts

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.speech.tts.TextToSpeech
import android.util.Log
import com.example.blindobstacledetection.models.DetectedObject
import java.util.Locale

class SpeechManager(
    private val context: Context,
    private val onInitComplete: (Boolean) -> Unit = {}
) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = TextToSpeech(context.applicationContext, this)
    private var isInitialized = false
    val cooldownManager = SpeechCooldownManager()

    private val vibrator: Vibrator? by lazy {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale.US)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e("SpeechManager", "Language US is not supported or missing data.")
                isInitialized = false
                onInitComplete(false)
            } else {
                isInitialized = true
                tts?.setSpeechRate(1.05f)
                tts?.setPitch(1.0f)
                onInitComplete(true)
            }
        } else {
            Log.e("SpeechManager", "TextToSpeech initialization failed.")
            isInitialized = false
            onInitComplete(false)
        }
    }

    fun updateSpeechConfig(rate: Float, pitch: Float, languageTag: String) {
        if (!isInitialized) return
        try {
            tts?.setSpeechRate(rate.coerceIn(0.5f, 2.0f))
            tts?.setPitch(pitch.coerceIn(0.5f, 2.0f))
            val locale = Locale.forLanguageTag(languageTag)
            tts?.setLanguage(locale)
        } catch (e: Exception) {
            Log.e("SpeechManager", "Failed to update TTS config: ${e.message}")
        }
    }

    fun announceObstacles(
        objects: List<DetectedObject>,
        cooldownSeconds: Int,
        vibrationEnabled: Boolean
    ) {
        if (!isInitialized || tts == null || objects.isEmpty()) return

        // Top 3 sorting: Closest distance, highest confidence
        val topObjects = objects.take(3)

        val hasHighPriority = topObjects.any { it.isHighPriority }
        val hasVeryClose = topObjects.any { it.distance.equals("Very Close", ignoreCase = true) }

        // Trigger safety vibration pattern for danger ("Very Close" or High Priority)
        if (vibrationEnabled && (hasVeryClose || hasHighPriority)) {
            triggerVibration(hasVeryClose)
        }

        // Build list of valid speech items passing cooldown check
        val speechStatements = mutableListOf<String>()

        for (obj in topObjects) {
            val shouldSpeak = cooldownManager.shouldAnnounce(
                objectName = obj.name,
                direction = obj.direction,
                distance = obj.distance,
                isHighPriority = obj.isHighPriority,
                cooldownSeconds = cooldownSeconds
            )

            if (shouldSpeak) {
                val phrase = formatPhrase(obj)
                speechStatements.add(phrase)
            }
        }

        if (speechStatements.isEmpty()) return

        val fullAnnouncement = speechStatements.joinToString(". ")

        // High priority uses QUEUE_FLUSH, Normal uses QUEUE_ADD
        val queueMode = if (hasHighPriority || hasVeryClose) {
            TextToSpeech.QUEUE_FLUSH
        } else {
            TextToSpeech.QUEUE_ADD
        }

        tts?.speak(fullAnnouncement, queueMode, null, "OBSTACLE_TTS_ID")
    }

    fun speakDirect(text: String, flush: Boolean = true) {
        if (!isInitialized || tts == null) return
        val mode = if (flush) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
        tts?.speak(text, mode, null, "DIRECT_TTS_ID")
    }

    private fun formatPhrase(obj: DetectedObject): String {
        val directionText = when (obj.direction) {
            "Left" -> "on your left"
            "Right" -> "on your right"
            else -> "directly ahead"
        }
        val nameCap = obj.name.replaceFirstChar { it.uppercase() }

        return when (obj.distance.lowercase()) {
            "very close", "critical" -> "Danger! $nameCap $directionText, one step away! Please stop!"
            "close" -> "Caution! $nameCap $directionText, two steps away"
            "medium" -> "$nameCap $directionText, four steps away"
            else -> "$nameCap $directionText, nearby"
        }
    }

    private fun triggerVibration(isVeryClose: Boolean) {
        try {
            vibrator?.let { v ->
                if (v.hasVibrator()) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        if (isVeryClose) {
                            // 3-pulse emergency hazard vibration
                            val pattern = longArrayOf(0, 150, 80, 150, 80, 300)
                            v.vibrate(VibrationEffect.createWaveform(pattern, -1))
                        } else {
                            v.vibrate(VibrationEffect.createOneShot(200L, VibrationEffect.DEFAULT_AMPLITUDE))
                        }
                    } else {
                        @Suppress("DEPRECATION")
                        v.vibrate(if (isVeryClose) 400L else 200L)
                    }
                }
            }
        } catch (e: Exception) {
            Log.w("SpeechManager", "Vibration error: ${e.message}")
        }
    }

    fun stop() {
        try {
            tts?.stop()
        } catch (e: Exception) {
            Log.w("SpeechManager", "Error stopping TTS: ${e.message}")
        }
    }

    fun shutdown() {
        try {
            tts?.stop()
            tts?.shutdown()
            tts = null
            isInitialized = false
        } catch (e: Exception) {
            Log.w("SpeechManager", "Error shutting down TTS: ${e.message}")
        }
    }
}
