package com.example.blindobstacledetection.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DetectionHistoryDao {

    @Query("SELECT * FROM detection_history ORDER BY timestamp DESC")
    fun getAllHistory(): Flow<List<DetectionHistoryEntity>>

    @Query("SELECT * FROM detection_history WHERE objectName LIKE '%' || :query || '%' ORDER BY timestamp DESC")
    fun searchHistory(query: String): Flow<List<DetectionHistoryEntity>>

    @Query("SELECT * FROM detection_history WHERE direction = :direction ORDER BY timestamp DESC")
    fun filterByDirection(direction: String): Flow<List<DetectionHistoryEntity>>

    @Query("SELECT * FROM detection_history WHERE distance = :distance ORDER BY timestamp DESC")
    fun filterByDistance(distance: String): Flow<List<DetectionHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(history: DetectionHistoryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(histories: List<DetectionHistoryEntity>)

    @Query("DELETE FROM detection_history WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM detection_history")
    suspend fun clearAll()
}
