package com.example.blindobstacledetection.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SpeechLogDao {

    @Query("SELECT * FROM speech_logs ORDER BY timestamp DESC LIMIT 100")
    fun getRecentSpeechLogs(): Flow<List<SpeechLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(speechLog: SpeechLogEntity)

    @Query("DELETE FROM speech_logs")
    suspend fun clearLogs()
}
