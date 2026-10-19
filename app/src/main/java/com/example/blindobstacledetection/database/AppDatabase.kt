package com.example.blindobstacledetection.database

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow

class AppDatabase private constructor(context: Context) : SQLiteOpenHelper(
    context.applicationContext,
    DB_NAME,
    null,
    DB_VERSION
) {
    companion object {
        private const val DB_NAME = "blind_obstacle_detection.db"
        private const val DB_VERSION = 2

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = AppDatabase(context)
                INSTANCE = instance
                instance
            }
        }
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS detection_history (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                objectName TEXT NOT NULL,
                confidence REAL NOT NULL,
                direction TEXT NOT NULL,
                distance TEXT NOT NULL,
                priority TEXT NOT NULL,
                timestamp INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS app_settings (
                id INTEGER PRIMARY KEY NOT NULL,
                voiceSpeed REAL NOT NULL,
                voicePitch REAL NOT NULL,
                language TEXT NOT NULL,
                theme TEXT NOT NULL,
                detectionIntervalMs INTEGER NOT NULL,
                confidenceThreshold REAL NOT NULL,
                speechCooldownSeconds INTEGER NOT NULL,
                baseUrl TEXT NOT NULL,
                vibrationEnabled INTEGER NOT NULL,
                highPriorityOnly INTEGER NOT NULL,
                cameraResolution TEXT NOT NULL,
                useOfflineMode INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS speech_logs (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                spokenText TEXT NOT NULL,
                isInterrupted INTEGER NOT NULL,
                priority TEXT NOT NULL,
                timestamp INTEGER NOT NULL
            )
            """.trimIndent()
        )

        // Insert default settings
        val cv = ContentValues().apply {
            put("id", 1)
            put("voiceSpeed", 1.0f)
            put("voicePitch", 1.0f)
            put("language", "en-US")
            put("theme", "System")
            put("detectionIntervalMs", 1000L)
            put("confidenceThreshold", 0.50f)
            put("speechCooldownSeconds", 5)
            put("baseUrl", "http://192.168.1.40:8000/")
            put("vibrationEnabled", 1)
            put("highPriorityOnly", 0)
            put("cameraResolution", "Medium")
            put("useOfflineMode", 0)
        }
        db.insertWithOnConflict("app_settings", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            try {
                db.execSQL("ALTER TABLE app_settings ADD COLUMN useOfflineMode INTEGER NOT NULL DEFAULT 0")
            } catch (e: Exception) {
                db.execSQL("DROP TABLE IF EXISTS detection_history")
                db.execSQL("DROP TABLE IF EXISTS app_settings")
                db.execSQL("DROP TABLE IF EXISTS speech_logs")
                onCreate(db)
            }
        }
    }

    // DAOs
    private val detectionHistoryDaoImpl = object : DetectionHistoryDao {
        override fun getAllHistory(): Flow<List<DetectionHistoryEntity>> = channelFlow {
            send(fetchHistoryInternal(null, null, null))
            awaitClose { }
        }

        override fun searchHistory(query: String): Flow<List<DetectionHistoryEntity>> = channelFlow {
            send(fetchHistoryInternal("objectName LIKE ?", arrayOf("%$query%"), null))
            awaitClose { }
        }

        override fun filterByDirection(direction: String): Flow<List<DetectionHistoryEntity>> = channelFlow {
            send(fetchHistoryInternal("direction = ?", arrayOf(direction), null))
            awaitClose { }
        }

        override fun filterByDistance(distance: String): Flow<List<DetectionHistoryEntity>> = channelFlow {
            send(fetchHistoryInternal("distance = ?", arrayOf(distance), null))
            awaitClose { }
        }

        override suspend fun insert(history: DetectionHistoryEntity) {
            val db = writableDatabase
            val cv = ContentValues().apply {
                if (history.id != 0L) put("id", history.id)
                put("objectName", history.objectName)
                put("confidence", history.confidence)
                put("direction", history.direction)
                put("distance", history.distance)
                put("priority", history.priority)
                put("timestamp", history.timestamp)
            }
            db.insertWithOnConflict("detection_history", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
        }

        override suspend fun insertAll(histories: List<DetectionHistoryEntity>) {
            histories.forEach { insert(it) }
        }

        override suspend fun deleteById(id: Long) {
            writableDatabase.delete("detection_history", "id = ?", arrayOf(id.toString()))
        }

        override suspend fun clearAll() {
            writableDatabase.delete("detection_history", null, null)
        }
    }

    private val settingsDaoImpl = object : SettingsDao {
        override fun getSettingsFlow(): Flow<SettingsEntity?> = channelFlow {
            send(getSettingsDirect())
            awaitClose { }
        }

        override suspend fun getSettingsDirect(): SettingsEntity? {
            val db = readableDatabase
            val cursor = db.rawQuery("SELECT * FROM app_settings WHERE id = 1 LIMIT 1", null)
            val settings = cursor.use {
                if (it.moveToFirst()) {
                    SettingsEntity(
                        id = it.getInt(it.getColumnIndexOrThrow("id")),
                        voiceSpeed = it.getFloat(it.getColumnIndexOrThrow("voiceSpeed")),
                        voicePitch = it.getFloat(it.getColumnIndexOrThrow("voicePitch")),
                        language = it.getString(it.getColumnIndexOrThrow("language")),
                        theme = it.getString(it.getColumnIndexOrThrow("theme")),
                        detectionIntervalMs = it.getLong(it.getColumnIndexOrThrow("detectionIntervalMs")),
                        confidenceThreshold = it.getFloat(it.getColumnIndexOrThrow("confidenceThreshold")),
                        speechCooldownSeconds = it.getInt(it.getColumnIndexOrThrow("speechCooldownSeconds")),
                        baseUrl = it.getString(it.getColumnIndexOrThrow("baseUrl")),
                        vibrationEnabled = it.getInt(it.getColumnIndexOrThrow("vibrationEnabled")) == 1,
                        highPriorityOnly = it.getInt(it.getColumnIndexOrThrow("highPriorityOnly")) == 1,
                        cameraResolution = it.getString(it.getColumnIndexOrThrow("cameraResolution")),
                        useOfflineMode = try { it.getInt(it.getColumnIndexOrThrow("useOfflineMode")) == 1 } catch (_: Exception) { false }
                    )
                } else null
            }
            if (settings != null && settings.baseUrl.contains("10.0.2.2")) {
                val updated = settings.copy(baseUrl = "http://127.0.0.1:8000/")
                insertOrUpdate(updated)
                return updated
            }
            return settings
        }

        override suspend fun insertOrUpdate(settings: SettingsEntity) {
            val db = writableDatabase
            val cv = ContentValues().apply {
                put("id", 1)
                put("voiceSpeed", settings.voiceSpeed)
                put("voicePitch", settings.voicePitch)
                put("language", settings.language)
                put("theme", settings.theme)
                put("detectionIntervalMs", settings.detectionIntervalMs)
                put("confidenceThreshold", settings.confidenceThreshold)
                put("speechCooldownSeconds", settings.speechCooldownSeconds)
                put("baseUrl", settings.baseUrl)
                put("vibrationEnabled", if (settings.vibrationEnabled) 1 else 0)
                put("highPriorityOnly", if (settings.highPriorityOnly) 1 else 0)
                put("cameraResolution", settings.cameraResolution)
                put("useOfflineMode", if (settings.useOfflineMode) 1 else 0)
            }
            db.insertWithOnConflict("app_settings", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
        }
    }

    private val speechLogDaoImpl = object : SpeechLogDao {
        override fun getRecentSpeechLogs(): Flow<List<SpeechLogEntity>> = channelFlow {
            val db = readableDatabase
            val cursor = db.rawQuery("SELECT * FROM speech_logs ORDER BY timestamp DESC LIMIT 100", null)
            val list = mutableListOf<SpeechLogEntity>()
            cursor.use {
                while (it.moveToNext()) {
                    list.add(
                        SpeechLogEntity(
                            id = it.getLong(it.getColumnIndexOrThrow("id")),
                            spokenText = it.getString(it.getColumnIndexOrThrow("spokenText")),
                            isInterrupted = it.getInt(it.getColumnIndexOrThrow("isInterrupted")) == 1,
                            priority = it.getString(it.getColumnIndexOrThrow("priority")),
                            timestamp = it.getLong(it.getColumnIndexOrThrow("timestamp"))
                        )
                    )
                }
            }
            send(list)
            awaitClose { }
        }

        override suspend fun insert(speechLog: SpeechLogEntity) {
            val db = writableDatabase
            val cv = ContentValues().apply {
                put("spokenText", speechLog.spokenText)
                put("isInterrupted", if (speechLog.isInterrupted) 1 else 0)
                put("priority", speechLog.priority)
                put("timestamp", speechLog.timestamp)
            }
            db.insertWithOnConflict("speech_logs", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
        }

        override suspend fun clearLogs() {
            writableDatabase.delete("speech_logs", null, null)
        }
    }

    private fun fetchHistoryInternal(selection: String?, selectionArgs: Array<String>?, orderBy: String?): List<DetectionHistoryEntity> {
        val db = readableDatabase
        val cursor = db.query(
            "detection_history",
            null,
            selection,
            selectionArgs,
            null,
            null,
            orderBy ?: "timestamp DESC"
        )
        val list = mutableListOf<DetectionHistoryEntity>()
        cursor.use {
            while (it.moveToNext()) {
                list.add(
                    DetectionHistoryEntity(
                        id = it.getLong(it.getColumnIndexOrThrow("id")),
                        objectName = it.getString(it.getColumnIndexOrThrow("objectName")),
                        confidence = it.getFloat(it.getColumnIndexOrThrow("confidence")),
                        direction = it.getString(it.getColumnIndexOrThrow("direction")),
                        distance = it.getString(it.getColumnIndexOrThrow("distance")),
                        priority = it.getString(it.getColumnIndexOrThrow("priority")),
                        timestamp = it.getLong(it.getColumnIndexOrThrow("timestamp"))
                    )
                )
            }
        }
        return list
    }

    fun detectionHistoryDao(): DetectionHistoryDao = detectionHistoryDaoImpl
    fun settingsDao(): SettingsDao = settingsDaoImpl
    fun speechLogDao(): SpeechLogDao = speechLogDaoImpl
}
