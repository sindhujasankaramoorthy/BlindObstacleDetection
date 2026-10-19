package com.example.blindobstacledetection.tts

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.example.blindobstacledetection.models.DetectedObject
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Explicit priority hierarchy for speech arbitration.
 * Higher rank numbers strictly preempt lower rank numbers.
 */
enum class SpeechPriority(val rank: Int) {
    CRITICAL_OBSTACLE(4),    // Emergency warnings (e.g. 1 step away, Stop!) - Flushes everything
    IMPORTANT_OBSTACLE(3),   // Regular obstacle warnings - Preempts navigation and status
    NAVIGATION_MANEUVER(2),  // Routine turn-by-turn navigation instructions
    STATUS_MESSAGE(1);       // System prompts and feedback (e.g. "Location acquired")

    fun preempts(other: SpeechPriority?): Boolean = other == null || this.rank > other.rank
}

/**
 * Centralized, prioritized speech coordinator for the Blind Obstacle Detection System.
 * Ensures safety-critical obstacle warnings strictly preempt routine navigation speech,
 * prevents stale navigation instructions from playing after interruption, and maintains
 * obstacle vibration alerts regardless of speech state.
 */
class SpeechManager(
    private val context: Context,
    private val onInitComplete: (Boolean) -> Unit = {}
) : TextToSpeech.OnInitListener {

    companion object {
        @Volatile
        private var instance: SpeechManager? = null

        fun getInstance(context: Context): SpeechManager {
            return instance ?: synchronized(this) {
                instance ?: SpeechManager(context.applicationContext).also { instance = it }
            }
        }
    }

    private var tts: TextToSpeech? = TextToSpeech(context.applicationContext, this)
    var isInitialized = false
        private set

    val cooldownManager = SpeechCooldownManager()

    private val vibrator: Vibrator? by lazy {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    // Active speech tracking
    private val currentPriority = AtomicReference<SpeechPriority?>(null)
    private val isNavigationMutedState = AtomicBoolean(false)

    @Volatile
    private var lastObstacleSpokenTimestamp = 0L

    // Pending navigation maneuver held while obstacle alert speaks
    private data class PendingNavigation(
        val text: String,
        val timestamp: Long = System.currentTimeMillis()
    )

    @Volatile
    private var pendingNavigation: PendingNavigation? = null

    var isNavigationMuted: Boolean
        get() = isNavigationMutedState.get()
        set(value) = isNavigationMutedState.set(value)

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
                setupProgressListener()
                onInitComplete(true)
            }
        } else {
            Log.e("SpeechManager", "TextToSpeech initialization failed.")
            isInitialized = false
            onInitComplete(false)
        }
    }

    private fun setupProgressListener() {
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                // Utterance started
            }

            override fun onDone(utteranceId: String?) {
                handleUtteranceCompleted(utteranceId)
            }

            override fun onError(utteranceId: String?) {
                handleUtteranceCompleted(utteranceId)
            }
        })
    }

    private fun handleUtteranceCompleted(utteranceId: String?) {
        val completedPriority = currentPriority.get()
        currentPriority.set(null)

        if (completedPriority == SpeechPriority.CRITICAL_OBSTACLE || completedPriority == SpeechPriority.IMPORTANT_OBSTACLE) {
            lastObstacleSpokenTimestamp = System.currentTimeMillis()

            // If a navigation instruction was delayed by the obstacle alert, resume it only if still fresh (< 8s)
            val pending = pendingNavigation
            pendingNavigation = null
            if (pending != null && !isNavigationMuted) {
                val age = System.currentTimeMillis() - pending.timestamp
                if (age <= 8000L) {
                    speakNavigationInternal(pending.text)
                } else {
                    Log.d("SpeechManager", "Discarded stale navigation instruction after obstacle alert ($age ms old)")
                }
            }
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

    // -------------------------------------------------------------
    // Priority 1 & 2: Obstacle Warnings (Highest Safety Priority)
    // -------------------------------------------------------------

    /**
     * Announces detected obstacles with strict priority preemption.
     * Critical/very close obstacles preempt routine navigation speech immediately.
     */
    fun announceObstacles(
        objects: List<DetectedObject>,
        cooldownSeconds: Int,
        vibrationEnabled: Boolean
    ) {
        if (objects.isEmpty()) return

        // Top 3 sorting: Closest distance, highest confidence
        val topObjects = objects.take(3)

        val hasHighPriority = topObjects.any { it.isHighPriority }
        val hasVeryClose = topObjects.any { it.distance.equals("Very Close", ignoreCase = true) }

        // Trigger safety vibration pattern regardless of speech availability
        if (vibrationEnabled && (hasVeryClose || hasHighPriority)) {
            triggerVibration(hasVeryClose)
        }

        if (!isInitialized || tts == null) return

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
                speechStatements.add(formatPhrase(obj))
            }
        }

        if (speechStatements.isEmpty()) return

        val fullAnnouncement = speechStatements.joinToString(". ")
        val isCritical = hasVeryClose || hasHighPriority
        val priority = if (isCritical) SpeechPriority.CRITICAL_OBSTACLE else SpeechPriority.IMPORTANT_OBSTACLE

        synchronized(this) {
            // Discard any pending navigation when critical hazard appears
            if (isCritical) {
                pendingNavigation = null
            }

            currentPriority.set(priority)
            lastObstacleSpokenTimestamp = System.currentTimeMillis()

            // Critical obstacle uses QUEUE_FLUSH to immediately interrupt routine navigation
            val queueMode = if (isCritical) TextToSpeech.QUEUE_FLUSH else {
                val active = currentPriority.get()
                if (active == SpeechPriority.NAVIGATION_MANEUVER || active == SpeechPriority.STATUS_MESSAGE) {
                    TextToSpeech.QUEUE_FLUSH
                } else {
                    TextToSpeech.QUEUE_ADD
                }
            }

            tts?.speak(fullAnnouncement, queueMode, null, "OBSTACLE_TTS_${priority.name}")
        }
    }

    // -------------------------------------------------------------
    // Priority 3: Turn-by-Turn Navigation Maneuvers
    // -------------------------------------------------------------

    /**
     * Speaks a turn-by-turn navigation maneuver.
     * Strictly subordinated to obstacle warnings:
     * - Never interrupts active obstacle speech.
     * - If obstacle warning is speaking, holds instruction temporarily.
     * - Automatically discarded if replaced by a newer maneuver or if it becomes stale.
     */
    fun speakNavigation(instruction: String) {
        if (isNavigationMuted || instruction.isBlank()) return

        val active = currentPriority.get()
        val now = System.currentTimeMillis()

        // If obstacle warning is active or spoke within last 1.5s, buffer and wait
        if (active == SpeechPriority.CRITICAL_OBSTACLE ||
            active == SpeechPriority.IMPORTANT_OBSTACLE ||
            (now - lastObstacleSpokenTimestamp < 1500L)
        ) {
            pendingNavigation = PendingNavigation(instruction, now)
            Log.d("SpeechManager", "Navigation speech buffered behind active obstacle alert")
            return
        }

        speakNavigationInternal(instruction)
    }

    private fun speakNavigationInternal(instruction: String) {
        if (!isInitialized || tts == null || isNavigationMuted) return

        synchronized(this) {
            currentPriority.set(SpeechPriority.NAVIGATION_MANEUVER)
            // Routine navigation flushes any previous routine navigation or status message
            tts?.speak(instruction, TextToSpeech.QUEUE_FLUSH, null, "NAV_TTS_ID")
        }
    }

    /**
     * Cancels any active or pending navigation speech without disturbing obstacle detection.
     */
    fun cancelNavigationSpeech() {
        pendingNavigation = null
        if (currentPriority.get() == SpeechPriority.NAVIGATION_MANEUVER) {
            currentPriority.set(null)
            tts?.stop()
        }
    }

    // -------------------------------------------------------------
    // Priority 4: Status Messages & UI Feedback
    // -------------------------------------------------------------

    /**
     * Speaks an assistive voice prompt or status update (lowest priority).
     * Uses QUEUE_ADD to avoid preempting obstacle warnings or navigation maneuvers.
     */
    fun speakPrompt(text: String, flush: Boolean = false) {
        if (!isInitialized || tts == null || text.isBlank()) return

        val active = currentPriority.get()
        // Never preempt obstacle alerts with a status prompt
        if (active == SpeechPriority.CRITICAL_OBSTACLE || active == SpeechPriority.IMPORTANT_OBSTACLE) {
            return
        }

        synchronized(this) {
            val mode = if (flush && active == null) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
            currentPriority.set(SpeechPriority.STATUS_MESSAGE)
            tts?.speak(text, mode, null, "PROMPT_TTS_ID")
        }
    }

    fun speakDirect(text: String, flush: Boolean = true) {
        speakPrompt(text, flush = flush)
    }

    // -------------------------------------------------------------
    // Helpers & Vibrations
    // -------------------------------------------------------------

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
            currentPriority.set(null)
            pendingNavigation = null
            tts?.stop()
        } catch (e: Exception) {
            Log.w("SpeechManager", "Error stopping TTS: ${e.message}")
        }
    }

    fun shutdown() {
        try {
            currentPriority.set(null)
            pendingNavigation = null
            tts?.stop()
            tts?.shutdown()
            tts = null
            isInitialized = false
        } catch (e: Exception) {
            Log.w("SpeechManager", "Error shutting down TTS: ${e.message}")
        }
    }
}
