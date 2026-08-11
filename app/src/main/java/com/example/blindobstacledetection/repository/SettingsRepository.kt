package com.example.blindobstacledetection.repository

import com.example.blindobstacledetection.database.SettingsDao
import com.example.blindobstacledetection.database.SettingsEntity
import kotlinx.coroutines.flow.Flow

class SettingsRepository(private val settingsDao: SettingsDao) {

    val settingsFlow: Flow<SettingsEntity?> = settingsDao.getSettingsFlow()

    suspend fun getSettings(): SettingsEntity {
        return settingsDao.getSettingsDirect() ?: SettingsEntity().also {
            settingsDao.insertOrUpdate(it)
        }
    }

    suspend fun updateSettings(settings: SettingsEntity) {
        settingsDao.insertOrUpdate(settings)
    }
}
