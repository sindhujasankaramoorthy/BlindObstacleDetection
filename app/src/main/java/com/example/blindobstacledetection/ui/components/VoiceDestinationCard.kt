package com.example.blindobstacledetection.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.blindobstacledetection.ui.theme.ColorClose
import com.example.blindobstacledetection.ui.theme.ColorVeryClose
import com.example.blindobstacledetection.ui.theme.EmeraldDark
import com.example.blindobstacledetection.ui.theme.EmeraldPrimary
import com.example.blindobstacledetection.ui.theme.SapphireSecondary
import com.example.blindobstacledetection.viewmodel.DestinationViewModel
import com.example.blindobstacledetection.voice.DestinationUiState

/**
 * Accessible, high-contrast Jetpack Compose card for voice destination input.
 * Provides distinct states: Idle, RequestingPermission, Listening, Recognized, Confirmed, Error.
 * Follows Material 3 styling, supports TalkBack semantics, and offers large touch targets.
 */
@Composable
fun VoiceDestinationCard(
    viewModel: DestinationViewModel,
    hasMicrophonePermission: Boolean,
    onRequestPermission: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val confirmedDestination by viewModel.confirmedDestination.collectAsState()

    GlassCard(
        modifier = modifier.fillMaxWidth(),
        contentPadding = 16.dp
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header Row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(EmeraldPrimary.copy(alpha = 0.15f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Navigation,
                        contentDescription = null,
                        tint = EmeraldPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Voice Destination Input",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Phase 17 • Spoken Walking Destination",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                    )
                }

                if (confirmedDestination != null) {
                    Text(
                        text = "SET",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black,
                        modifier = Modifier
                            .background(EmeraldPrimary, RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Body based on UI State
            when (val state = uiState) {
                is DestinationUiState.Idle -> {
                    IdleView(
                        confirmedDestination = confirmedDestination,
                        onMicClick = {
                            viewModel.onMicrophoneClicked(
                                hasPermission = hasMicrophonePermission,
                                onRequestPermission = onRequestPermission
                            )
                        },
                        onClearClick = { viewModel.clearDestination() }
                    )
                }

                is DestinationUiState.RequestingPermission -> {
                    RequestingPermissionView()
                }

                is DestinationUiState.Listening -> {
                    ListeningView(
                        state = state,
                        onCancelClick = { viewModel.cancelListening() }
                    )
                }

                is DestinationUiState.Recognized -> {
                    RecognizedView(
                        state = state,
                        onConfirm = { viewModel.confirmDestination(state.parsedDestination) },
                        onRetry = { viewModel.retryListening() },
                        onCancel = { viewModel.cancelListening() }
                    )
                }

                is DestinationUiState.Confirmed -> {
                    ConfirmedView(
                        destination = state.destination,
                        onChangeDestination = {
                            viewModel.onMicrophoneClicked(
                                hasPermission = hasMicrophonePermission,
                                onRequestPermission = onRequestPermission
                            )
                        },
                        onClear = { viewModel.clearDestination() }
                    )
                }

                is DestinationUiState.Error -> {
                    ErrorView(
                        state = state,
                        onRetry = {
                            if (state.isPermissionError) {
                                onRequestPermission()
                            } else {
                                viewModel.retryListening()
                            }
                        },
                        onDismiss = { viewModel.cancelListening() }
                    )
                }
            }
        }
    }
}

@Composable
private fun IdleView(
    confirmedDestination: String?,
    onMicClick: () -> Unit,
    onClearClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        if (confirmedDestination.isNullOrBlank()) {
            Text(
                text = "Where would you like to go?",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Tap the microphone and say:\n\"Navigate to [Destination]\" or \"Take me to [Destination]\"",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                textAlign = TextAlign.Center,
                lineHeight = 18.sp
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Large High-Contrast Microphone Button
            Button(
                onClick = onMicClick,
                modifier = Modifier
                    .size(76.dp)
                    .semantics {
                        role = Role.Button
                        contentDescription = "Speak Destination Microphone Button"
                        stateDescription = "Ready to listen. Tap to speak destination."
                    },
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = EmeraldPrimary,
                    contentColor = Color.Black
                ),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = null,
                    modifier = Modifier.size(38.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Tap to Speak",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
        } else {
            // Already confirmed destination in memory
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(EmeraldDark.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                    .border(1.dp, EmeraldPrimary.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                    .padding(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.LocationOn,
                    contentDescription = null,
                    tint = EmeraldPrimary,
                    modifier = Modifier.size(28.dp)
                )

                Spacer(modifier = Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Confirmed Destination:",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    Text(
                        text = confirmedDestination,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                IconButton(
                    onClick = onClearClick,
                    modifier = Modifier.semantics { contentDescription = "Clear destination" }
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Clear",
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedButton(
                onClick = onMicClick,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(imageVector = Icons.Default.Mic, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Change Destination by Voice", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun RequestingPermissionView() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
    ) {
        CircularProgressIndicator(
            color = EmeraldPrimary,
            strokeWidth = 3.dp,
            modifier = Modifier.size(36.dp)
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = "Requesting microphone permission...",
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun ListeningView(
    state: DestinationUiState.Listening,
    onCancelClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = "Listening for Destination...",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = EmeraldPrimary,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = if (state.partialText.isNotBlank()) "\"${state.partialText}\"" else "Please speak clearly into the microphone now",
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Pulsing / Active Mic Button
        Box(
            modifier = Modifier
                .size(76.dp)
                .background(ColorClose.copy(alpha = 0.2f), CircleShape)
                .border(2.dp, ColorClose, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Mic,
                contentDescription = "Microphone is listening",
                tint = ColorClose,
                modifier = Modifier.size(36.dp)
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        Button(
            onClick = onCancelClick,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
            ),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = "Cancel Voice Recognition" }
        ) {
            Icon(imageVector = Icons.Default.Close, contentDescription = null)
            Spacer(modifier = Modifier.width(6.dp))
            Text("Cancel Listening", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun RecognizedView(
    state: DestinationUiState.Recognized,
    onConfirm: () -> Unit,
    onRetry: () -> Unit,
    onCancel: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = "Destination Recognized",
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = SapphireSecondary
        )

        Spacer(modifier = Modifier.height(6.dp))

        // Large high-contrast display of recognized destination
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(EmeraldDark.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                .border(2.dp, EmeraldPrimary, RoundedCornerShape(12.dp))
                .padding(14.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = state.parsedDestination,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = EmeraldPrimary,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Spoken phrase: \"${state.rawText}\"",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Large Primary Confirmation Button
        Button(
            onClick = onConfirm,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .semantics {
                    role = Role.Button
                    contentDescription = "Confirm Destination: ${state.parsedDestination}"
                },
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = EmeraldPrimary,
                contentColor = Color.Black
            )
        ) {
            Icon(imageVector = Icons.Default.Check, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Confirm Destination", fontSize = 17.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Secondary Actions (Retry and Cancel)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = onRetry,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(imageVector = Icons.Default.Refresh, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Retry", fontWeight = FontWeight.SemiBold)
            }

            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(imageVector = Icons.Default.Close, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Cancel", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun ConfirmedView(
    destination: String,
    onChangeDestination: () -> Unit,
    onClear: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .background(EmeraldDark.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
                .border(2.dp, EmeraldPrimary, RoundedCornerShape(12.dp))
                .padding(14.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = EmeraldPrimary,
                modifier = Modifier.size(28.dp)
            )

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Confirmed Walking Destination:",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = EmeraldPrimary
                )
                Text(
                    text = destination,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Destination saved in memory. Ready for walking navigation in Phase 18.",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = onChangeDestination,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            ) {
                Icon(imageVector = Icons.Default.Mic, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Change", fontWeight = FontWeight.Bold)
            }

            OutlinedButton(
                onClick = onClear,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(imageVector = Icons.Default.Close, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Clear", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun ErrorView(
    state: DestinationUiState.Error,
    onRetry: () -> Unit,
    onDismiss: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .background(ColorVeryClose.copy(alpha = 0.15f), RoundedCornerShape(10.dp))
                .border(1.dp, ColorVeryClose.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                .padding(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = ColorVeryClose,
                modifier = Modifier.size(24.dp)
            )

            Spacer(modifier = Modifier.width(10.dp))

            Text(
                text = state.message,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface,
                lineHeight = 18.sp,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = onRetry,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (state.isPermissionError) SapphireSecondary else EmeraldPrimary,
                    contentColor = Color.Black
                ),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(
                    imageVector = if (state.isPermissionError) Icons.Default.Mic else Icons.Default.Refresh,
                    contentDescription = null
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (state.isPermissionError) "Grant Permission" else "Try Again",
                    fontWeight = FontWeight.Bold
                )
            }

            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Dismiss", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
