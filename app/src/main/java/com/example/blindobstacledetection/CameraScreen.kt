package com.example.blindobstacledetection

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.speech.tts.TextToSpeech
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.core.content.ContextCompat
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.blindobstacledetection.api.RetrofitClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.Locale
import java.util.concurrent.Executors

@SuppressLint("UnsafeOptInUsageError")
@Composable
fun CameraScreen() {

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var detectedObjects by remember {
        mutableStateOf("Detecting...")
    }

    var lastSpoken by remember {
        mutableStateOf("")
    }

    val previewView = remember {
        PreviewView(context)
    }

    val cameraExecutor = remember {
        Executors.newSingleThreadExecutor()
    }

    val tts = remember {
        TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                // initialized
            }
        }
    }

    LaunchedEffect(Unit) {
        tts.language = Locale.US
    }

    DisposableEffect(Unit) {

        onDispose {

            tts.stop()
            tts.shutdown()
            cameraExecutor.shutdown()

        }

    }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {

        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize()
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(20.dp)
        ) {

            Text(
                text = detectedObjects,
                fontSize = 22.sp,
                style = MaterialTheme.typography.bodyLarge
            )

        }

    }


    LaunchedEffect(Unit) {

        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

        cameraProviderFuture.addListener({

            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build()

            preview.setSurfaceProvider(previewView.surfaceProvider)

            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            var lastFrameTime = 0L

            imageAnalysis.setAnalyzer(cameraExecutor) { image ->

                val current = System.currentTimeMillis()

                if (current - lastFrameTime < 1000) {
                    image.close()
                    return@setAnalyzer
                }

                lastFrameTime = current

                try {

                    val bitmap = ImageUtils.imageProxyToBitmap(image)

                    val jpeg = ImageUtils.bitmapToJpeg(bitmap)

                    uploadFrame(jpeg) { text ->

                        detectedObjects = text

                        if (
                            text.isNotEmpty() &&
                            text != "No Object" &&
                            text != lastSpoken
                        ) {

                            lastSpoken = text

                            tts.speak(
                                text,
                                TextToSpeech.QUEUE_FLUSH,
                                null,
                                null
                            )
                        }
                    }

                } finally {
                    image.close()
                }

            }

            cameraProvider.unbindAll()

            cameraProvider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                imageAnalysis
            )

        }, ContextCompat.getMainExecutor(context))
    }


}

private fun uploadFrame(
    jpeg: ByteArray,
    onResult: (String) -> Unit
) {

    CoroutineScope(Dispatchers.IO).launch {

        try {

            val requestBody =
                jpeg.toRequestBody(
                    "image/jpeg".toMediaType()
                )

            val part =
                MultipartBody.Part.createFormData(
                    "file",
                    "frame.jpg",
                    requestBody
                )

            val response =
                RetrofitClient.api.detectObjects(part)

            if (response.isSuccessful) {

                val body = response.body()

                if (body != null) {

                    if (body.objects.isEmpty()) {

                        CoroutineScope(Dispatchers.Main).launch {

                            onResult("No Object")

                        }

                    } else {

                        val detected =
                            body.objects.joinToString(", ") {
                                "${it.name} (${(it.confidence * 100).toInt()}%)"
                            }

                        CoroutineScope(Dispatchers.Main).launch {

                            onResult(detected)

                        }

                    }

                } else {

                    CoroutineScope(Dispatchers.Main).launch {

                        onResult("No Object")

                    }

                }

            } else {

                CoroutineScope(Dispatchers.Main).launch {

                    onResult("Server Error")

                }

            }

        } catch (e: Exception) {

            CoroutineScope(Dispatchers.Main).launch {

                onResult("Backend Offline")

            }

        }

    }

}