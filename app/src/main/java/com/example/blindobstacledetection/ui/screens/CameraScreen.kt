package com.example.blindobstacledetection.ui.screens

import android.annotation.SuppressLint
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import com.example.blindobstacledetection.navigation.models.NavigationState
import com.example.blindobstacledetection.viewmodel.DestinationViewModel
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavController
import com.example.blindobstacledetection.models.DetectedObject
import com.example.blindobstacledetection.ui.components.BoundingBoxOverlay
import com.example.blindobstacledetection.ui.components.GlassCard
import com.example.blindobstacledetection.ui.theme.ColorClose
import com.example.blindobstacledetection.ui.theme.ColorFar
import com.example.blindobstacledetection.ui.theme.ColorMedium
import com.example.blindobstacledetection.ui.theme.ColorVeryClose
import com.example.blindobstacledetection.ui.theme.EmeraldPrimary
import com.example.blindobstacledetection.utils.ImageUtils
import com.example.blindobstacledetection.viewmodel.DetectionViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

@SuppressLint("UnsafeOptInUsageError")
@Composable
fun CameraScreen(
    navController: NavController,
    viewModel: DetectionViewModel,
    destinationViewModel: DestinationViewModel? = null
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val navigationState by destinationViewModel?.navigationState?.collectAsState() ?: remember { androidx.compose.runtime.mutableStateOf(NavigationState.NoDestination) }

    val detectedObjects by viewModel.detectedObjects.collectAsState()
    val statusText by viewModel.statusText.collectAsState()
    val isProcessing by viewModel.isProcessing.collectAsState()
    val fps by viewModel.fps.collectAsState()
    val isPaused by viewModel.isDetectionPaused.collectAsState()
    val isMuted by viewModel.isVoiceMuted.collectAsState()
    val isFlashOn by viewModel.isFlashOn.collectAsState()
    val useFrontCamera by viewModel.useFrontCamera.collectAsState()
    val cameraError by viewModel.cameraError.collectAsState()

    val previewView = remember { PreviewView(context) }
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }

    val timeFormat = remember { SimpleDateFormat("hh:mm:ss a", Locale.getDefault()) }

    DisposableEffect(Unit) {
        onDispose {
            cameraExecutor.shutdown()
        }
    }

    // Camera Bind Lifecycle Effect
    LaunchedEffect(useFrontCamera, isFlashOn) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()

                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()

                imageAnalysis.setAnalyzer(cameraExecutor) { image ->
                    try {
                        val jpegBytes = ImageUtils.imageProxyToJpegByteArray(image, quality = 80, maxDimension = 640)
                        viewModel.processFrame(jpegBytes)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    } finally {
                        image.close()
                    }
                }

                val cameraSelector = if (useFrontCamera) {
                    CameraSelector.DEFAULT_FRONT_CAMERA
                } else {
                    CameraSelector.DEFAULT_BACK_CAMERA
                }

                cameraProvider.unbindAll()
                val camera = cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    imageAnalysis
                )

                if (camera.cameraInfo.hasFlashUnit()) {
                    camera.cameraControl.enableTorch(isFlashOn)
                }
                viewModel.setCameraError(null)
            } catch (e: Exception) {
                e.printStackTrace()
                viewModel.setCameraError("Camera unavailable: ${e.localizedMessage ?: "Device hardware unavailable"}")
            }
        }, ContextCompat.getMainExecutor(context))
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Fullscreen Camera Preview
        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize()
        )

        // Real-Time Bounding Box Canvas Overlay
        BoundingBoxOverlay(
            detectedObjects = detectedObjects,
            modifier = Modifier.fillMaxSize()
        )

        // Camera Failure Alert Overlay (if hardware or permission issue occurs)
        cameraError?.let { errorMsg ->
            GlassCard(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth()
                    .padding(24.dp),
                backgroundColor = ColorVeryClose.copy(alpha = 0.95f),
                contentPadding = 16.dp
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Camera hardware error",
                        tint = Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Camera Unavailable",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = errorMsg,
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.9f)
                    )
                }
            }
        }

        // Top HUD Header
        GlassCard(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(top = 40.dp, start = 16.dp, end = 16.dp),
            contentPadding = 12.dp
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                IconButton(
                    onClick = { navController.popBackStack() },
                    modifier = Modifier.background(Color.Black.copy(alpha = 0.4f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isProcessing) {
                            CircularProgressIndicator(
                                color = EmeraldPrimary,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                        }
                        Text(
                            text = if (isPaused) "PAUSED" else "LIVE AI DETECTION",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isPaused) ColorVeryClose else EmeraldPrimary
                        )
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = "FPS: $fps | Objects: ${detectedObjects.size}",
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                }

                IconButton(
                    onClick = { viewModel.toggleCameraLens() },
                    modifier = Modifier.background(Color.Black.copy(alpha = 0.4f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Cameraswitch,
                        contentDescription = "Switch Camera",
                        tint = Color.White
                    )
                }
            }
        }

        // Active Turn-by-Turn Navigation HUD Banner (Phase 20)
        val activeNav = navigationState
        if (activeNav is NavigationState.Navigating) {
            GlassCard(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .padding(top = 112.dp, start = 16.dp, end = 16.dp),
                backgroundColor = if (activeNav.isOffRoute) ColorVeryClose.copy(alpha = 0.9f) else Color.Black.copy(alpha = 0.85f),
                contentPadding = 10.dp
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.DirectionsWalk,
                        contentDescription = "Walking Navigation Active",
                        tint = if (activeNav.isOffRoute) Color.White else EmeraldPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (activeNav.isOffRoute) "⚠️ Off Route (~${activeNav.offRouteDistanceMeters.toInt()}m off)" else activeNav.currentStep.instruction,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1
                        )
                        Text(
                            text = if (activeNav.distanceToNextManeuverMeters > 0) {
                                "in ${activeNav.distanceToNextManeuverMeters.toInt()}m • ${activeNav.remainingDistanceMeters.toInt()}m remaining"
                            } else {
                                "${activeNav.remainingDistanceMeters.toInt()}m remaining"
                            },
                            fontSize = 11.sp,
                            color = if (activeNav.isOffRoute) Color.White.copy(alpha = 0.9f) else EmeraldPrimary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        // Bottom Announcement HUD & Live Predicted Objects Bar
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Live Objects Horizontal Cards
            AnimatedVisibility(visible = detectedObjects.isNotEmpty()) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                ) {
                    items(detectedObjects) { obj ->
                        val (distanceText, badgeColor) = when (obj.distance.lowercase()) {
                            "very close", "critical" -> Pair("🔴 Very Close (~0.8m)", ColorVeryClose)
                            "close" -> Pair("🟠 Close (~1.5m)", ColorClose)
                            "medium" -> Pair("🟡 Medium (~2.5m)", ColorMedium)
                            else -> Pair("🟢 Far (~3.5m+)", ColorFar)
                        }

                        GlassCard(
                            backgroundColor = Color.Black.copy(alpha = 0.8f),
                            contentPadding = 8.dp
                        ) {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = obj.name.replaceFirstChar { it.uppercase() },
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "${(obj.confidence * 100).toInt()}%",
                                        fontSize = 11.sp,
                                        color = EmeraldPrimary,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = distanceText,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = badgeColor
                                )
                                Text(
                                    text = "📍 ${obj.direction} • 🕒 ${timeFormat.format(Date())}",
                                    fontSize = 10.sp,
                                    color = Color.White.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                }
            }

            // Speech Status Card
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                backgroundColor = Color.Black.copy(alpha = 0.85f),
                contentPadding = 16.dp
            ) {
                Column {
                    Text(
                        text = "AUDIO ANNOUNCEMENT",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = EmeraldPrimary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = statusText,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Control Buttons Row
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = 8.dp
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Pause/Play Button
                    IconButton(
                        onClick = { viewModel.togglePauseDetection() },
                        modifier = Modifier
                            .size(52.dp)
                            .background(
                                if (isPaused) ColorVeryClose else Color.White.copy(alpha = 0.15f),
                                CircleShape
                            )
                    ) {
                        Icon(
                            imageVector = if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                            contentDescription = "Pause",
                            tint = Color.White
                        )
                    }

                    // Voice Mute Button
                    IconButton(
                        onClick = { viewModel.toggleVoiceMute() },
                        modifier = Modifier
                            .size(52.dp)
                            .background(
                                if (isMuted) ColorVeryClose else Color.White.copy(alpha = 0.15f),
                                CircleShape
                            )
                    ) {
                        Icon(
                            imageVector = if (isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = "Voice",
                            tint = Color.White
                        )
                    }

                    // Flash Toggle Button
                    IconButton(
                        onClick = { viewModel.toggleFlash() },
                        modifier = Modifier
                            .size(52.dp)
                            .background(
                                if (isFlashOn) EmeraldPrimary else Color.White.copy(alpha = 0.15f),
                                CircleShape
                            )
                    ) {
                        Icon(
                            imageVector = if (isFlashOn) Icons.Default.FlashOn else Icons.Default.FlashOff,
                            contentDescription = "Flash",
                            tint = if (isFlashOn) Color.Black else Color.White
                        )
                    }
                }
            }
        }
    }
}
