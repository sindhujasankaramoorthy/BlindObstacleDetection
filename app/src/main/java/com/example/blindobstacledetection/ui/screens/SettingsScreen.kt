package com.example.blindobstacledetection.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.blindobstacledetection.ui.components.GlassCard
import com.example.blindobstacledetection.ui.theme.EmeraldPrimary
import com.example.blindobstacledetection.viewmodel.SettingsViewModel

@Composable
fun SettingsScreen(
    navController: NavController,
    viewModel: SettingsViewModel
) {
    val settings by viewModel.settingsState.collectAsState()
    val testResult by viewModel.connectionTestResult.collectAsState()
    val isTesting by viewModel.isTestingConnection.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // Header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            IconButton(onClick = { navController.popBackStack() }) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }
            Text(
                text = "Assistant Settings",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Offline On-Device AI Mode Card
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Offline On-Device AI Mode",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Runs YOLOv8 AI directly on phone without laptop or server",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = settings.useOfflineMode,
                        onCheckedChange = { viewModel.updateUseOfflineMode(it) }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Backend IP & Server Configuration Card
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column {
                Text(
                    text = "Backend Server URL (Cloud or Local)",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = settings.baseUrl,
                    onValueChange = { viewModel.updateBaseUrl(it) },
                    label = { Text("FastAPI Server Base URL") },
                    placeholder = { Text("http://10.0.2.2:8000/") },
                    singleLine = true,
                    enabled = !settings.useOfflineMode,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Button(
                        onClick = { viewModel.testBackendUrl(settings.baseUrl) },
                        enabled = !isTesting && !settings.useOfflineMode,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        if (isTesting) {
                            CircularProgressIndicator(
                                strokeWidth = 2.dp,
                                modifier = Modifier.height(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Icon(Icons.Default.NetworkCheck, contentDescription = "Test")
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Test Connection")
                    }
                }

                if (testResult != null && !settings.useOfflineMode) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = testResult!!,
                        fontSize = 13.sp,
                        color = if (testResult!!.startsWith("Success")) EmeraldPrimary else MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Voice Guidance Controls
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column {
                Text(
                    text = "Voice Guidance & Audio",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Voice Speed
                Text(
                    text = "Speech Rate: ${String.format("%.1fx", settings.voiceSpeed)}",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
                Slider(
                    value = settings.voiceSpeed,
                    onValueChange = { viewModel.updateVoiceSpeed(it) },
                    valueRange = 0.5f..2.0f,
                    steps = 15
                )

                // Voice Pitch
                Text(
                    text = "Speech Pitch: ${String.format("%.1fx", settings.voicePitch)}",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
                Slider(
                    value = settings.voicePitch,
                    onValueChange = { viewModel.updateVoicePitch(it) },
                    valueRange = 0.5f..2.0f,
                    steps = 15
                )

                // Speech Cooldown
                Text(
                    text = "Speech Cooldown: ${settings.speechCooldownSeconds}s",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
                Slider(
                    value = settings.speechCooldownSeconds.toFloat(),
                    onValueChange = { viewModel.updateSpeechCooldown(it.toInt()) },
                    valueRange = 2f..10f,
                    steps = 8
                )

                // Vibration Toggle
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Haptic Vibration on Proximity Warning",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = settings.vibrationEnabled,
                        onCheckedChange = { viewModel.updateVibrationEnabled(it) }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // AI Detection Parameters
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column {
                Text(
                    text = "AI Detection Tuning",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Confidence Threshold
                Text(
                    text = "Confidence Threshold: ${(settings.confidenceThreshold * 100).toInt()}%",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
                Slider(
                    value = settings.confidenceThreshold,
                    onValueChange = { viewModel.updateConfidenceThreshold(it) },
                    valueRange = 0.10f..0.90f,
                    steps = 16
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Interval Selection
                Text(
                    text = "Frame Interval: ${settings.detectionIntervalMs / 1000.0}s",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    listOf(500L, 1000L, 2000L).forEach { interval ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = (settings.detectionIntervalMs == interval),
                                onClick = { viewModel.updateDetectionInterval(interval) }
                            )
                            Text("${interval / 1000.0}s", fontSize = 13.sp)
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Theme Selector
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column {
                Text(
                    text = "Appearance & Theme",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    listOf("System", "Dark", "Light").forEach { themeName ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = (settings.theme == themeName),
                                onClick = { viewModel.updateTheme(themeName) }
                            )
                            Text(themeName, fontSize = 14.sp)
                            Spacer(modifier = Modifier.width(12.dp))
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}
