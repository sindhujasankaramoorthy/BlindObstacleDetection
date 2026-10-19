package com.example.blindobstacledetection.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.blindobstacledetection.ui.components.AccessibleButton
import com.example.blindobstacledetection.ui.components.GlassCard
import com.example.blindobstacledetection.ui.components.StatusCard
import com.example.blindobstacledetection.ui.components.VoiceDestinationCard
import com.example.blindobstacledetection.ui.components.LocationStatusCard
import com.example.blindobstacledetection.ui.theme.EmeraldDark
import com.example.blindobstacledetection.ui.theme.EmeraldPrimary
import com.example.blindobstacledetection.viewmodel.DestinationViewModel
import com.example.blindobstacledetection.viewmodel.MainViewModel

@Composable
fun HomeScreen(
    navController: NavController,
    mainViewModel: MainViewModel,
    destinationViewModel: DestinationViewModel = viewModel()
) {
    val context = LocalContext.current
    val isBackendOnline by mainViewModel.isBackendOnline.collectAsState()
    val isInternetAvailable by mainViewModel.isInternetAvailable.collectAsState()

    var isCameraGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        isCameraGranted = granted
        if (granted) {
            navController.navigate("camera")
        }
    }

    var isMicrophoneGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val microphonePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        isMicrophoneGranted = granted
        destinationViewModel.onPermissionResult(granted)
    }

    var isLocationGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        isLocationGranted = fineGranted || coarseGranted
        destinationViewModel.onLocationPermissionResult(fineGranted, coarseGranted)
    }

    LaunchedEffect(Unit) {
        mainViewModel.checkBackendConnection()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(20.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "AI Navigation Assistant",
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary
        )

        Text(
            text = "Blind Obstacle Assistant",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Child & Parent Safety Protection Banner
        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            backgroundColor = EmeraldDark.copy(alpha = 0.85f),
            contentPadding = 14.dp
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "🛡️ Child Safety Protection Active: Human step warnings (\"1 step away!\"), emergency 3-pulse vibrations, and spatial YOLO AI designed to protect visually impaired users.",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "System Status",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Status Cards Grid
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                StatusCard(
                    title = "Backend Status",
                    statusText = when (isBackendOnline) {
                        true -> "Online"
                        false -> "Offline"
                        null -> "Checking..."
                    },
                    isOnline = isBackendOnline,
                    icon = Icons.Default.Cloud,
                    modifier = Modifier.weight(1f)
                )

                StatusCard(
                    title = "Camera Status",
                    statusText = if (isCameraGranted) "Ready" else "Permission Required",
                    isOnline = isCameraGranted,
                    icon = Icons.Default.Videocam,
                    modifier = Modifier.weight(1f)
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                StatusCard(
                    title = "Internet Status",
                    statusText = if (isInternetAvailable) "Connected" else "No Connection",
                    isOnline = isInternetAvailable,
                    icon = Icons.Default.Wifi,
                    modifier = Modifier.weight(1f)
                )

                StatusCard(
                    title = "AI Model Status",
                    statusText = if (isBackendOnline == true) "YOLOv8 Active" else "Standby",
                    isOnline = isBackendOnline,
                    icon = Icons.Default.Memory,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Phase 17: Voice Destination Input Card
        VoiceDestinationCard(
            viewModel = destinationViewModel,
            hasMicrophonePermission = isMicrophoneGranted,
            onRequestPermission = {
                microphonePermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Phase 18: Live GPS Location Card
        LocationStatusCard(
            viewModel = destinationViewModel,
            hasLocationPermission = isLocationGranted,
            onRequestPermission = {
                locationPermissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                )
            }
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Navigation Control",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Big Primary Action Button
        AccessibleButton(
            text = "Start Obstacle Detection",
            onClick = {
                if (isCameraGranted) {
                    navController.navigate("camera")
                } else {
                    cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                }
            },
            icon = Icons.Default.PlayArrow,
            containerColor = EmeraldPrimary,
            contentColor = androidx.compose.ui.graphics.Color.Black,
            height = 70.dp,
            fontSize = 20
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Secondary Action Buttons
        AccessibleButton(
            text = "Detected Objects & History",
            onClick = { navController.navigate("history") },
            icon = Icons.Default.History,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            height = 56.dp,
            fontSize = 16
        )

        Spacer(modifier = Modifier.height(12.dp))

        AccessibleButton(
            text = "Settings & Voice Pitch",
            onClick = { navController.navigate("settings") },
            icon = Icons.Default.Settings,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            height = 56.dp,
            fontSize = 16
        )

        Spacer(modifier = Modifier.height(12.dp))

        AccessibleButton(
            text = "About Assistant & Safety",
            onClick = { navController.navigate("about") },
            icon = Icons.Default.Info,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            height = 56.dp,
            fontSize = 16
        )

        Spacer(modifier = Modifier.height(24.dp))
    }
}
