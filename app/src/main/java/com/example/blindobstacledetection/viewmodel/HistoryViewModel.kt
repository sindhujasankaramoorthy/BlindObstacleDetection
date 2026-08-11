package com.example.blindobstacledetection.viewmodel

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.blindobstacledetection.database.AppDatabase
import com.example.blindobstacledetection.database.DetectionHistoryEntity
import com.example.blindobstacledetection.repository.HistoryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HistoryViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = HistoryRepository(AppDatabase.getInstance(application).detectionHistoryDao())

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedDirectionFilter = MutableStateFlow("All")
    val selectedDirectionFilter: StateFlow<String> = _selectedDirectionFilter.asStateFlow()

    private val _selectedDistanceFilter = MutableStateFlow("All")
    val selectedDistanceFilter: StateFlow<String> = _selectedDistanceFilter.asStateFlow()

    private val _filteredHistory = MutableStateFlow<List<DetectionHistoryEntity>>(emptyList())
    val filteredHistory: StateFlow<List<DetectionHistoryEntity>> = _filteredHistory.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                repository.allHistory,
                _searchQuery,
                _selectedDirectionFilter,
                _selectedDistanceFilter
            ) { historyList, query, direction, distance ->
                historyList.filter { item ->
                    val matchesQuery = query.isBlank() || item.objectName.contains(query, ignoreCase = true)
                    val matchesDirection = direction == "All" || item.direction.equals(direction, ignoreCase = true)
                    val matchesDistance = distance == "All" || item.distance.equals(distance, ignoreCase = true)
                    matchesQuery && matchesDirection && matchesDistance
                }
            }.collect { list ->
                _filteredHistory.value = list
            }
        }
    }

    fun onSearchQueryChanged(newQuery: String) {
        _searchQuery.value = newQuery
    }

    fun onDirectionFilterSelected(direction: String) {
        _selectedDirectionFilter.value = direction
    }

    fun onDistanceFilterSelected(distance: String) {
        _selectedDistanceFilter.value = distance
    }

    fun deleteItem(id: Long) {
        viewModelScope.launch {
            repository.deleteById(id)
        }
    }

    fun clearAll() {
        viewModelScope.launch {
            repository.clearAll()
        }
    }

    fun exportToCsv(context: Context, uri: Uri): Boolean {
        return try {
            val historyList = _filteredHistory.value
            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                OutputStreamWriter(outputStream).use { writer ->
                    writer.write("ID,Object Name,Confidence,Direction,Distance,Priority,Timestamp,Date\n")
                    val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                    for (item in historyList) {
                        val dateStr = dateFormat.format(Date(item.timestamp))
                        writer.write("${item.id},\"${item.objectName}\",${item.confidence},${item.direction},${item.distance},${item.priority},${item.timestamp},\"$dateStr\"\n")
                    }
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
