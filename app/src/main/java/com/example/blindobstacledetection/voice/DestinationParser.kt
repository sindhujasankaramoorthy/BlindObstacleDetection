package com.example.blindobstacledetection.voice

/**
 * Utility to extract clean destination text from natural spoken commands.
 *
 * Examples:
 * - "Navigate to Gandhipuram Bus Stand." -> "Gandhipuram Bus Stand"
 * - "Take me to Coimbatore Railway Station." -> "Coimbatore Railway Station"
 * - "I want to go to the nearest hospital." -> "the nearest hospital"
 * - "Gandhipuram Bus Stand" -> "Gandhipuram Bus Stand"
 * - "Navigate to" -> null (invalid destination)
 * - "" -> null
 */
object DestinationParser {

    private val COMMAND_PREFIXES = listOf(
        "please take me to",
        "please navigate to",
        "please direct me to",
        "i would like to go to",
        "i'd like to go to",
        "i want to go to",
        "i need to go to",
        "can you take me to",
        "can you navigate to",
        "take me to",
        "navigate to",
        "directions to",
        "direct me to",
        "route to",
        "lead me to",
        "guide me to",
        "bring me to",
        "drive to",
        "walk to",
        "go to",
        "head to"
    )

    /**
     * Parses the raw spoken input string and returns the cleaned destination name,
     * or null if the input is blank or contains only a command prefix.
     */
    fun parse(rawInput: String?): String? {
        if (rawInput.isNullOrBlank()) return null

        var cleaned = rawInput.trim()
        // Remove trailing punctuation (. ? ! , ;)
        cleaned = cleaned.replace(Regex("[\\.\\?\\!,;]+$"), "").trim()

        if (cleaned.isBlank()) return null

        val lower = cleaned.lowercase()

        for (prefix in COMMAND_PREFIXES) {
            if (lower.startsWith(prefix)) {
                val candidate = cleaned.substring(prefix.length).trim()
                val candidateCleaned = candidate.replace(Regex("^[\\.\\?\\!,;:\\s]+|[\\.\\?\\!,;:\\s]+$"), "").trim()
                return if (candidateCleaned.isNotBlank()) {
                    candidateCleaned
                } else {
                    null // It was only the prefix, e.g. "navigate to"
                }
            }
        }

        // If no prefix matched, check if the input itself is just command words (e.g. "navigate", "go")
        val lowerCleaned = cleaned.lowercase()
        if (lowerCleaned in setOf("navigate", "go", "take me", "directions", "route", "destination")) {
            return null
        }

        return cleaned
    }
}
