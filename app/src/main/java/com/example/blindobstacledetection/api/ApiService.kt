package com.example.blindobstacledetection.api

import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

interface ApiService {

    @Multipart
    @POST("detect")
    suspend fun detectObjects(
        @Part file: MultipartBody.Part
    ): Response<DetectionResponse>
}