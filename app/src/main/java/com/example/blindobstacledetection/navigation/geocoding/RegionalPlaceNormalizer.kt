package com.example.blindobstacledetection.navigation.geocoding

/**
 * Normalizes phonetic speech recognition variations and transliterations
 * for South Indian / Tamil Nadu / Coimbatore place names.
 *
 * Speech-to-text engines frequently transcribe South Indian names into
 * alternative spellings (e.g. "Nilambur" instead of "Neelambur",
 * "Pilamedu" instead of "Peelamedu", "Thudiyaloor" instead of "Thudiyalur").
 * This normalizer maps them to their canonical local OpenStreetMap representations.
 */
object RegionalPlaceNormalizer {

    /**
     * Map of common phonetic speech recognition variations to canonical place names.
     */
    private val PHONETIC_ALIASES = mapOf(
        "nilambur" to "Neelambur",
        "nilamboor" to "Neelambur",
        "neelamboor" to "Neelambur",
        "neelambur" to "Neelambur",

        "pilamedu" to "Peelamedu",
        "peelamedu" to "Peelamedu",

        "thudiyaloor" to "Thudiyalur",
        "thudiyalur" to "Thudiyalur",

        "singanaloor" to "Singanallur",
        "singanallur" to "Singanallur",

        "gaandhipuram" to "Gandhipuram",
        "gandhipuram" to "Gandhipuram",

        "kuniamuthur" to "Kuniyamuthur",
        "kuniyamuthur" to "Kuniyamuthur",

        "vadhavalli" to "Vadavalli",
        "vadavalli" to "Vadavalli",

        "saravanampatty" to "Saravanampatti",
        "saravanampatti" to "Saravanampatti",

        "ganapathi" to "Ganapathy",
        "ganapathy" to "Ganapathy",

        "ondipudoor" to "Ondipudur",
        "ondipudur" to "Ondipudur",

        "peroor" to "Perur",
        "perur" to "Perur",

        "podanoor" to "Podanur",
        "podanur" to "Podanur",

        "kovaipudoor" to "Kovaipudur",
        "kovaipudur" to "Kovaipudur",

        "malumichampatty" to "Malumichampatti",
        "malumichampatti" to "Malumichampatti",

        "kinathukadavoo" to "Kinathukadavu",
        "kinathukadavu" to "Kinathukadavu",

        "mettuppalayam" to "Mettupalayam",
        "mettupalayam" to "Mettupalayam",

        "polachi" to "Pollachi",
        "pollachi" to "Pollachi",

        "tirupur" to "Tiruppur",
        "tiruppur" to "Tiruppur",

        "suloor" to "Sulur",
        "sulur" to "Sulur",

        "irugoor" to "Irugur",
        "irugur" to "Irugur",

        "ukkadom" to "Ukkadam",
        "ukkadam" to "Ukkadam",

        "townhall" to "Town Hall",
        "town hall" to "Town Hall",

        "saibaba colony" to "Saibaba Colony",
        "sai baba colony" to "Saibaba Colony",

        "rs puram" to "RS Puram",
        "r.s. puram" to "RS Puram",
        "r s puram" to "RS Puram",

        "ramanathapuram" to "Ramanathapuram",
        "ramnathapuram" to "Ramanathapuram",

        "chitra" to "Chitra, Civil Aerodrome Post",
        "hope college" to "Hopes College, Peelamedu",
        "hopes" to "Hopes College, Peelamedu"
    )

    /**
     * Map of common Tamil script place names to English romanized equivalents for TTS.
     */
    private val TAMIL_TO_ROMANIZED = mapOf(
        "நீலாம்பூர்" to "Neelambur",
        "காந்திபுரம்" to "Gandhipuram",
        "பீளமேடு" to "Peelamedu",
        "சிங்காநல்லூர்" to "Singanallur",
        "துடியலூர்" to "Thudiyalur",
        "சூலூர்" to "Sulur",
        "உக்கடம்" to "Ukkadam",
        "பேரூர்" to "Perur",
        "போத்தனூர்" to "Podanur",
        "கோவைப்புதூர்" to "Kovaipudur",
        "சரவணம்பட்டி" to "Saravanampatti",
        "வடவள்ளி" to "Vadavalli",
        "மருத்துவமனை" to "Hospital",
        "பேருந்து நிலையம்" to "Bus Stand",
        "ரயில் நிலையம்" to "Railway Station"
    )

    /**
     * Normalizes a place name query by substituting phonetic speech variants.
     */
    fun normalize(input: String): String {
        val trimmed = input.trim()
        if (trimmed.isBlank()) return trimmed

        val lower = trimmed.lowercase()

        // 1. Direct exact alias match
        PHONETIC_ALIASES[lower]?.let { return it }

        // 2. Token / word replacement for multi-word destination (e.g. "Nilambur Bus Stop" -> "Neelambur Bus Stop")
        var result = trimmed
        for ((alias, canonical) in PHONETIC_ALIASES) {
            val pattern = Regex("(?i)\\b" + Regex.escape(alias) + "\\b")
            if (pattern.containsMatchIn(result)) {
                result = pattern.replace(result, canonical)
            }
        }
        return result
    }

    /**
     * Returns true if the string contains Tamil Unicode characters (\u0B80..\u0BFF).
     */
    fun containsTamil(text: String): Boolean {
        return text.any { it in '\u0B80'..'\u0BFF' }
    }

    /**
     * Gets a Romanized English representation of a Tamil place name for TTS speech feedback.
     */
    fun getRomanizedName(tamilName: String): String? {
        val trimmed = tamilName.trim()
        return TAMIL_TO_ROMANIZED[trimmed]
    }
}
