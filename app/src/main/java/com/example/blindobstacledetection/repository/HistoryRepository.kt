package com.example.blindobstacledetection.repository

import com.example.blindobstacledetection.database.DetectionHistoryDao
import com.example.blindobstacledetection.database.DetectionHistoryEntity
import kotlinx.coroutines.flow.Flow

class HistoryRepository(private val dao: DetectionHistoryDao) {

    val allHistory: Flow<List<DetectionHistoryEntity>> = dao.getAllHistory()

    fun searchHistory(query: String): Flow<List<DetectionHistoryEntity>> {
        return if (query.isBlank()) dao.getAllHistory() else dao.searchHistory(query.trim())
    }

    fun filterByDirection(direction: String): Flow<List<DetectionHistoryEntity>> {
        return dao.filterByDirection(direction)
    }

    fun filterByDistance(distance: String): Flow<List<DetectionHistoryEntity>> {
        return dao.filterByDistance(distance)
    }

    suspend fun insert(history: DetectionHistoryEntity) {
        dao.insert(history)
    }

    suspend fun insertAll(histories: List<DetectionHistoryEntity>) {
        dao.insertAll(histories)
    }

    suspend fun deleteById(id: Long) {
        dao.deleteById(id)
    }

    suspend fun clearAll() {
        dao.clearAll()
    }
}
