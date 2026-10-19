package com.example.blindobstacledetection.voice

import com.example.blindobstacledetection.navigation.geocoding.RegionalPlaceNormalizer

/**
 * Utility to extract clean destination text from natural spoken commands
 * in both English and Tamil.
 *
 * Supports:
 * - English prefixes: "Navigate to Gandhipuram", "Take me to Neelambur"
 * - Tamil prefixes & suffixes: "நீலாம்பூருக்கு போ", "வழி காட்டு காந்திபுரம்", "பேருந்து நிலையத்திற்கு போ"
 * - Phonetic alias normalization: "Nilambur" -> "Neelambur", "Pilamedu" -> "Peelamedu"
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

    private val TAMIL_PREFIXES = listOf(
        "தயவுசெய்து வழி காட்டு",
        "தயவுசெய்து",
        "வழி காட்டு",
        "வழிகாட்டு",
        "என்னை அழைத்து செல்",
        "அழைத்து செல்",
        "போ",
        "செல்"
    )

    private val TAMIL_SUFFIXES = listOf(
        "க்கு வழி காட்டு",
        " வழி காட்டு",
        " வழிகாட்டு",
        "க்கு செல்ல வேண்டும்",
        " செல்ல வேண்டும்",
        "க்கு போக வேண்டும்",
        " போக வேண்டும்",
        "க்கு அழைத்து செல்",
        " அழைத்து செல்",
        "க்கு கொண்டு போ",
        " கொண்டு போ",
        "க்கு போ",
        " போ",
        "க்கு செல்",
        " செல்"
    )

    /**
     * Parses the raw spoken input string and returns the cleaned, normalized destination name,
     * or null if the input is blank or contains only a command prefix/word.
     */
    fun parse(rawInput: String?): String? {
        if (rawInput.isNullOrBlank()) return null

        var cleaned = rawInput.trim()
        // Remove trailing punctuation (. ? ! , ;)
        cleaned = cleaned.replace(Regex("[\\.\\?\\!,;]+$"), "").trim()

        if (cleaned.isBlank()) return null

        // 1. Check English command prefixes
        val lower = cleaned.lowercase()
        var extracted: String? = null

        for (prefix in COMMAND_PREFIXES) {
            if (lower.startsWith(prefix)) {
                val candidate = cleaned.substring(prefix.length).trim()
                val candidateCleaned = candidate.replace(Regex("^[\\.\\?\\!,;:\\s]+|[\\.\\?\\!,;:\\s]+$"), "").trim()
                if (candidateCleaned.isNotBlank()) {
                    extracted = candidateCleaned
                } else {
                    return null // It was only the prefix, e.g. "navigate to"
                }
                break
            }
        }

        // 2. If no English prefix matched, check Tamil command prefixes / suffixes
        if (extracted == null) {
            var tamilCandidate = cleaned

            // Strip Tamil prefixes
            for (prefix in TAMIL_PREFIXES) {
                if (tamilCandidate.startsWith(prefix)) {
                    val candidate = tamilCandidate.substring(prefix.length).trim()
                    if (candidate.isNotBlank()) {
                        tamilCandidate = candidate
                        break
                    }
                }
            }

            // Strip Tamil suffixes
            for (suffix in TAMIL_SUFFIXES) {
                if (tamilCandidate.endsWith(suffix)) {
                    val candidate = tamilCandidate.substring(0, tamilCandidate.length - suffix.length).trim()
                    if (candidate.isNotBlank()) {
                        tamilCandidate = candidate
                        break
                    }
                }
            }

            // Restore base nouns from Tamil dative case inflections
            // e.g. "காந்திபுரத்திற்கு" -> "காந்திபுரம்", "நீலாம்பூருக்கு" -> "நீலாம்பூர்"
            tamilCandidate = normalizeTamilDative(tamilCandidate)

            if (tamilCandidate.isNotBlank() && tamilCandidate != cleaned) {
                extracted = tamilCandidate
            }
        }

        val candidate = extracted ?: cleaned

        // If candidate is just command keywords (English or Tamil), return null
        val lowerCandidate = candidate.lowercase()
        if (lowerCandidate in setOf(
                "navigate", "go", "take me", "directions", "route", "destination",
                "போ", "செல்", "வழி", "வழிகாட்டு", "வழி காட்டு"
            )
        ) {
            return null
        }

        // 3. Apply regional phonetic alias normalization (e.g. "Nilambur" -> "Neelambur")
        return RegionalPlaceNormalizer.normalize(candidate)
    }

    /**
     * Normalizes Tamil dative inflections to restore base place names.
     */
    private fun normalizeTamilDative(word: String): String {
        var result = word.trim()
        when {
            result.endsWith("த்திற்கு") -> result = result.removeSuffix("த்திற்கு") + "ம்"
            result.endsWith("த்துக்கு") -> result = result.removeSuffix("த்துக்கு") + "ம்"
            result.endsWith("ூருக்கு") -> result = result.removeSuffix("ூருக்கு") + "ூர்"
            result.endsWith("ருக்கு") -> result = result.removeSuffix("ருக்கு") + "ர்"
            result.endsWith("வுக்கு") -> result = result.removeSuffix("வுக்கு")
            result.endsWith("க்கு") -> result = result.removeSuffix("க்கு")
        }

        // If after suffix stripping the word ends in "ரு" (e.g. "நீலாம்பூரு" -> "நீலாம்பூர்")
        if (result.endsWith("ூரு")) {
            result = result.removeSuffix("ூரு") + "ூர்"
        } else if (result.endsWith("ரு") && !result.endsWith("திரு")) {
            result = result.removeSuffix("ரு") + "ர்"
        }

        return result
    }
}
