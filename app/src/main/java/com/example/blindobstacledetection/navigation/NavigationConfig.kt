package com.example.blindobstacledetection.navigation

/**
 * Configuration manager for navigation and routing services.
 * Keeps API credentials outside hardcoded source files.
 * Supports credentials supplied via local.properties / BuildConfig or runtime user entry.
 */
object NavigationConfig {

    private var runtimeApiKey: String? = null

    /**
     * Resolves the active OpenRouteService API key.
     * Checks runtime configured key first, then falls back to BuildConfig.ORS_API_KEY if present.
     */
    val apiKey: String
        get() {
            val key = runtimeApiKey?.trim()
            if (!key.isNullOrBlank()) {
                return key
            }
            return try {
                val buildConfigClass = Class.forName("com.example.blindobstacledetection.BuildConfig")
                val field = buildConfigClass.getField("ORS_API_KEY")
                val value = field.get(null) as? String
                value?.trim().orEmpty()
            } catch (_: Throwable) {
                ""
            }
        }

    /**
     * True if a non-blank API key has been supplied.
     */
    val isConfigured: Boolean
        get() = apiKey.isNotBlank()

    /**
     * Dynamically sets an API key at runtime (e.g. from the in-app API key input dialog).
     * Kept in memory only without storing secrets in persistent SQLite database.
     */
    fun setRuntimeApiKey(key: String) {
        runtimeApiKey = key.trim()
    }

    /**
     * Clears any runtime API key.
     */
    fun clearRuntimeApiKey() {
        runtimeApiKey = null
    }
}
