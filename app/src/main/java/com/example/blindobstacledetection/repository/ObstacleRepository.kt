package com.example.blindobstacledetection.repository

import com.example.blindobstacledetection.api.RetrofitClient
import com.example.blindobstacledetection.models.DetectionResponse
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

class ObstacleRepository {

    suspend fun checkBackendStatus(baseUrl: String): Result<Boolean> {
        return try {
            val response = RetrofitClient.getService(baseUrl).checkHealth()
            if (response.isSuccessful) {
                Result.success(true)
            } else {
                Result.failure(Exception("HTTP Error: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun detectObstacles(
        jpegBytes: ByteArray,
        baseUrl: String,
        confThreshold: Float
    ): Result<DetectionResponse> {
        return try {
            val requestBody = jpegBytes.toRequestBody("image/jpeg".toMediaType())
            val filePart = MultipartBody.Part.createFormData("file", "frame.jpg", requestBody)

            val apiService = RetrofitClient.getService(baseUrl)
            val response = apiService.detectObjects(filePart, confThreshold)

            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Server returned error code ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
