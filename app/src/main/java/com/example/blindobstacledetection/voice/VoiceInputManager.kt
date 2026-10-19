package com.example.blindobstacledetection.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import java.util.Locale

/**
 * Dedicated manager for Android's native SpeechRecognizer API.
 * Handles lifecycle, permissions checking, recognition intents,
 * real-time partial/final results, and descriptive error mappings.
 */
class VoiceInputManager(context: Context) {

    private val appContext: Context = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())

    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening: Boolean = false

    interface Listener {
        fun onReadyForSpeech() {}
        fun onBeginningOfSpeech() {}
        fun onRmsChanged(rmsdB: Float) {}
        fun onPartialResult(partialText: String) {}
        fun onFinalResult(recognizedText: String) {}
        fun onError(errorMessage: String, errorCode: Int) {}
        fun onEndOfSpeech() {}
    }

    private var activeListener: Listener? = null

    /**
     * Checks if SpeechRecognizer is available on this Android device.
     */
    fun isAvailable(): Boolean {
        return SpeechRecognizer.isRecognitionAvailable(appContext)
    }

    /**
     * Starts listening for spoken destination input.
     * Guaranteed to execute on the Android main thread.
     * Defaults to Indian English (en-IN) with multilingual Tamil (ta-IN) support.
     */
    @Synchronized
    fun startListening(languageLocale: Locale = Locale.forLanguageTag("en-IN"), listener: Listener): Boolean {
        if (!isAvailable()) {
            listener.onError("Speech recognition service is not available on this device.", -1)
            return false
        }

        if (isListening) {
            Log.w("VoiceInputManager", "Already listening. Ignoring duplicate start request.")
            return false
        }

        activeListener = listener

        mainHandler.post {
            try {
                if (speechRecognizer == null) {
                    speechRecognizer = SpeechRecognizer.createSpeechRecognizer(appContext).apply {
                        setRecognitionListener(InternalRecognitionListener())
                    }
                }

                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageLocale.toLanguageTag())
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, languageLocale.toLanguageTag())
                    // Provide bilingual recognition support (en-IN & ta-IN)
                    val additional = if (languageLocale.language == "ta") {
                        arrayOf("en-IN", "ta-IN")
                    } else {
                        arrayOf("ta-IN", "en-IN")
                    }
                    putExtra("android.speech.extra.ADDITIONAL_LANGUAGES", additional)
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
                    val prompt = if (languageLocale.language == "ta") {
                        "நீங்கள் எங்கு செல்ல விரும்புகிறீர்கள்? / Where would you like to go?"
                    } else {
                        "Where would you like to go?"
                    }
                    putExtra(RecognizerIntent.EXTRA_PROMPT, prompt)
                }

                isListening = true
                speechRecognizer?.startListening(intent)
            } catch (e: Exception) {
                isListening = false
                Log.e("VoiceInputManager", "Failed to start speech recognition: ${e.message}", e)
                activeListener?.onError("Failed to start voice recognition: ${e.localizedMessage}", -1)
            }
        }

        return true
    }

    /**
     * Stops listening and processes audio recorded so far.
     */
    fun stopListening() {
        mainHandler.post {
            try {
                if (isListening) {
                    speechRecognizer?.stopListening()
                }
            } catch (e: Exception) {
                Log.w("VoiceInputManager", "Error stopping recognizer: ${e.message}")
            } finally {
                isListening = false
            }
        }
    }

    /**
     * Cancels the active recognition session immediately without processing results.
     */
    fun cancel() {
        mainHandler.post {
            try {
                speechRecognizer?.cancel()
            } catch (e: Exception) {
                Log.w("VoiceInputManager", "Error canceling recognizer: ${e.message}")
            } finally {
                isListening = false
                activeListener = null
            }
        }
    }

    /**
     * Releases all recognizer resources cleanly. Must be called in ViewModel/Lifecycle disposal.
     */
    fun destroy() {
        mainHandler.post {
            try {
                speechRecognizer?.cancel()
                speechRecognizer?.destroy()
                speechRecognizer = null
            } catch (e: Exception) {
                Log.w("VoiceInputManager", "Error destroying recognizer: ${e.message}")
            } finally {
                isListening = false
                activeListener = null
            }
        }
    }

    private inner class InternalRecognitionListener : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            activeListener?.onReadyForSpeech()
        }

        override fun onBeginningOfSpeech() {
            activeListener?.onBeginningOfSpeech()
        }

        override fun onRmsChanged(rmsdB: Float) {
            activeListener?.onRmsChanged(rmsdB)
        }

        override fun onBufferReceived(buffer: ByteArray?) {}

        override fun onEndOfSpeech() {
            isListening = false
            activeListener?.onEndOfSpeech()
        }

        override fun onError(error: Int) {
            isListening = false
            val message = mapErrorCodeToMessage(error)
            Log.e("VoiceInputManager", "Speech recognition error ($error): $message")
            activeListener?.onError(message, error)
        }

        override fun onResults(results: Bundle?) {
            isListening = false
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val topMatch = matches?.firstOrNull()?.trim() ?: ""
            if (topMatch.isNotBlank()) {
                activeListener?.onFinalResult(topMatch)
            } else {
                activeListener?.onError(
                    "No speech recognized. Please speak clearly and try again.",
                    SpeechRecognizer.ERROR_NO_MATCH
                )
            }
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val partialText = matches?.firstOrNull()?.trim() ?: ""
            if (partialText.isNotBlank()) {
                activeListener?.onPartialResult(partialText)
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    companion object {
        fun mapErrorCodeToMessage(errorCode: Int): String {
            return when (errorCode) {
                SpeechRecognizer.ERROR_AUDIO -> "Audio recording error. Please check your microphone."
                SpeechRecognizer.ERROR_CLIENT -> "Speech recognition client error."
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is required."
                SpeechRecognizer.ERROR_NETWORK -> "Network error during speech recognition. Please check your connection."
                SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout. Please check your connection."
                SpeechRecognizer.ERROR_NO_MATCH -> "No speech recognized. Please speak clearly and try again."
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Speech recognition service is busy. Please try again."
                SpeechRecognizer.ERROR_SERVER -> "Speech server error. Please try again."
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected. Please tap the microphone and speak."
                else -> "Speech recognition error ($errorCode). Please try again."
            }
        }
    }
}
