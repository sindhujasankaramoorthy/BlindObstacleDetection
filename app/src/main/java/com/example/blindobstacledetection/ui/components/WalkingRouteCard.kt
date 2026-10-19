package com.example.blindobstacledetection.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AltRoute
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.TurnLeft
import androidx.compose.material.icons.filled.TurnRight
import androidx.compose.material.icons.filled.TurnSharpLeft
import androidx.compose.material.icons.filled.TurnSharpRight
import androidx.compose.material.icons.filled.TurnSlightLeft
import androidx.compose.material.icons.filled.TurnSlightRight
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Warning
import com.example.blindobstacledetection.navigation.models.NavigationState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.blindobstacledetection.navigation.NavigationConfig
import com.example.blindobstacledetection.navigation.models.RouteCoordinates
import com.example.blindobstacledetection.navigation.models.RouteStep
import com.example.blindobstacledetection.navigation.models.RouteUiState
import com.example.blindobstacledetection.navigation.models.WalkingRoute
import com.example.blindobstacledetection.ui.theme.ColorClose
import com.example.blindobstacledetection.ui.theme.ColorVeryClose
import com.example.blindobstacledetection.ui.theme.EmeraldDark
import com.example.blindobstacledetection.ui.theme.EmeraldPrimary
import com.example.blindobstacledetection.ui.theme.SapphireSecondary
import com.example.blindobstacledetection.viewmodel.DestinationViewModel

/**
 * Accessible Jetpack Compose card for pedestrian walking route planning (Phase 19).
 *
 * Displays:
 * - Confirmed destination and current GPS readiness
 * - API key configuration modal / notice
 * - Plan Walking Route action button
 * - Loading indicator with stage messages
 * - Total walking distance and estimated duration
 * - Ordered list of navigation steps with maneuver icons and distances
 * - Canvas route overview mini-map
 * - Intent to view in system map application
 * - Clear and retry controls
 */
@Composable
fun WalkingRouteCard(
    viewModel: DestinationViewModel,
    modifier: Modifier = Modifier,
    onOpenLiveCamera: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val confirmedDestination by viewModel.confirmedDestination.collectAsState()
    val currentLocation by viewModel.currentLocation.collectAsState()
    val routeState by viewModel.routeState.collectAsState()
    val currentRoute by viewModel.currentRoute.collectAsState()
    val navigationState by viewModel.navigationState.collectAsState()
    val isNavigationMuted by viewModel.isNavigationMuted.collectAsState()

    var showApiKeyDialog by remember { mutableStateOf(false) }
    var apiKeyInput by remember { mutableStateOf("") }

    val hasDestination = !confirmedDestination.isNullOrBlank()
    val hasLocation = currentLocation != null
    val isReadyToPlan = hasDestination && hasLocation
    val isLoading = routeState is RouteUiState.Loading

    GlassCard(
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = "Walking Route Planning Card"
            },
        backgroundColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
        contentPadding = 18.dp
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {

            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(
                            color = SapphireSecondary.copy(alpha = 0.15f),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.DirectionsWalk,
                        contentDescription = "Walking Route Planning",
                        tint = SapphireSecondary,
                        modifier = Modifier.size(26.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Pedestrian Walking Route",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Phase 19 Navigation Planning",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                    )
                }

                // API Key quick configure button
                OutlinedButton(
                    onClick = {
                        apiKeyInput = NavigationConfig.apiKey
                        showApiKeyDialog = true
                    },
                    modifier = Modifier
                        .height(36.dp)
                        .semantics {
                            role = Role.Button
                            contentDescription = if (NavigationConfig.isConfigured) {
                                "Routing API Key Configured. Tap to edit."
                            } else {
                                "Routing API Key Missing. Tap to configure."
                            }
                        },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = if (NavigationConfig.isConfigured) EmeraldPrimary else ColorVeryClose
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Key,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (NavigationConfig.isConfigured) "Key Set" else "Set Key",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Prerequisites Readiness Row
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = MaterialTheme.colorScheme.background.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(12.dp)
                    )
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Destination item
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (hasDestination) Icons.Default.Check else Icons.Default.Warning,
                        contentDescription = null,
                        tint = if (hasDestination) EmeraldPrimary else ColorClose,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (hasDestination) {
                            "Destination: $confirmedDestination"
                        } else {
                            "Destination: Not set (Speak destination above)"
                        },
                        fontSize = 13.sp,
                        fontWeight = if (hasDestination) FontWeight.Medium else FontWeight.Normal,
                        color = if (hasDestination) MaterialTheme.colorScheme.onSurface else ColorClose
                    )
                }

                // Location item
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (hasLocation) Icons.Default.Check else Icons.Default.Warning,
                        contentDescription = null,
                        tint = if (hasLocation) EmeraldPrimary else ColorClose,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (hasLocation) {
                            "Current GPS: Ready (±${currentLocation?.accuracyMeters?.toInt() ?: 10}m)"
                        } else {
                            "Current GPS: Not acquired (Tap Get Location above)"
                        },
                        fontSize = 13.sp,
                        fontWeight = if (hasLocation) FontWeight.Medium else FontWeight.Normal,
                        color = if (hasLocation) MaterialTheme.colorScheme.onSurface else ColorClose
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // State: Missing API Key Banner
            if (routeState is RouteUiState.MissingApiKey) {
                val missingKeyMsg = (routeState as RouteUiState.MissingApiKey).message
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = ColorVeryClose.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(12.dp)
                        )
                        .border(
                            width = 1.dp,
                            color = ColorVeryClose.copy(alpha = 0.4f),
                            shape = RoundedCornerShape(12.dp)
                        )
                        .padding(14.dp)
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "API Key Warning",
                                tint = ColorVeryClose,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Routing API Key Required",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = ColorVeryClose
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = missingKeyMsg,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Button(
                            onClick = {
                                apiKeyInput = NavigationConfig.apiKey
                                showApiKeyDialog = true
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ColorVeryClose,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(text = "Enter API Key", fontSize = 13.sp)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
            }

            // State: Error Banner
            if (routeState is RouteUiState.Error) {
                val error = routeState as RouteUiState.Error
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = ColorClose.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(12.dp)
                        )
                        .border(
                            width = 1.dp,
                            color = ColorClose.copy(alpha = 0.4f),
                            shape = RoundedCornerShape(12.dp)
                        )
                        .padding(14.dp)
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "Route Error",
                                tint = ColorClose,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Route Planning Error",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = ColorClose
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = error.message,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            if (error.canRetry) {
                                Button(
                                    onClick = { viewModel.retryRoutePlanning() },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = SapphireSecondary,
                                        contentColor = Color.White
                                    ),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(text = "Retry", fontSize = 13.sp)
                                }
                            }
                            OutlinedButton(
                                onClick = { viewModel.clearRoute() },
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(text = "Dismiss", fontSize = 13.sp)
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
            }

            // State: Loading Indicator
            AnimatedVisibility(
                visible = isLoading,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                val stage = (routeState as? RouteUiState.Loading)?.stage ?: "Calculating route..."
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = SapphireSecondary.copy(alpha = 0.1f),
                            shape = RoundedCornerShape(12.dp)
                        )
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(36.dp),
                        color = SapphireSecondary,
                        strokeWidth = 3.dp
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = stage,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = { viewModel.cancelRoutePlanning() },
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Cancel,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = "Cancel", fontSize = 13.sp)
                    }
                }
            }

            if (!isLoading) {
                // Primary Action Button: Plan Walking Route
                Button(
                    onClick = { viewModel.planWalkingRoute() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .semantics {
                            role = Role.Button
                            contentDescription = when {
                                !hasDestination -> "Plan Walking Route disabled. Confirm destination first."
                                !hasLocation -> "Plan Walking Route disabled. Acquire current location first."
                                else -> "Plan Walking Route to $confirmedDestination"
                            }
                        },
                    enabled = isReadyToPlan,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = EmeraldPrimary,
                        contentColor = Color.Black,
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.DirectionsWalk,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (currentRoute != null) "Recalculate Walking Route" else "Plan Walking Route",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // State: Route Success Display
            val route = currentRoute
            if (route != null && !isLoading) {
                Spacer(modifier = Modifier.height(18.dp))

                // Phase 20 Turn-by-Turn Navigation Status and Controls
                when (val nav = navigationState) {
                    is NavigationState.Navigating -> {
                        ActiveNavigationSection(
                            navigating = nav,
                            isMuted = isNavigationMuted,
                            onPause = { viewModel.pauseNavigation() },
                            onStop = { viewModel.stopNavigation() },
                            onToggleMute = { viewModel.toggleNavigationMute() },
                            onRecalculate = { viewModel.recalculateRoute() },
                            onOpenLiveCamera = onOpenLiveCamera
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                    }
                    is NavigationState.Paused -> {
                        PausedNavigationSection(
                            paused = nav,
                            onResume = { viewModel.resumeNavigation() },
                            onStop = { viewModel.stopNavigation() }
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                    }
                    is NavigationState.Completed -> {
                        CompletedNavigationSection(
                            completed = nav,
                            onDismiss = { viewModel.stopNavigation() }
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                    }
                    else -> {
                        // Route Ready - Show Primary Start Navigation Button
                        Button(
                            onClick = { viewModel.startNavigation() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .semantics {
                                    role = Role.Button
                                    contentDescription = "Start Walking Navigation to ${route.destinationName}"
                                },
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = EmeraldPrimary,
                                contentColor = Color.Black
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.DirectionsWalk,
                                contentDescription = null,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Start Walking Navigation",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "🔊 Voice turn-by-turn guidance will announce each step. Real-time camera obstacle warnings always take immediate priority.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                            lineHeight = 16.sp
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                    }
                }

                // Route Summary Card
                RouteSummarySection(
                    route = route,
                    onOpenInMaps = {
                        val uri = Uri.parse("geo:${route.destinationLatitude},${route.destinationLongitude}?q=${Uri.encode(route.destinationName)}")
                        val mapIntent = Intent(Intent.ACTION_VIEW, uri)
                        mapIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        try {
                            context.startActivity(mapIntent)
                        } catch (_: Exception) {
                            // Fallback to web browser OSM
                            val webUri = Uri.parse("https://www.openstreetmap.org/directions?engine=fossgis_osrm_foot&route=${route.originLatitude}%2C${route.originLongitude}%3B${route.destinationLatitude}%2C${route.destinationLongitude}")
                            context.startActivity(Intent(Intent.ACTION_VIEW, webUri).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK })
                        }
                    },
                    onClearRoute = { viewModel.clearRoute() }
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Route Steps List with current active step highlighted
                val currentStepIdx = (navigationState as? NavigationState.Navigating)?.currentStepIndex ?: -1
                RouteStepsSection(steps = route.steps, currentStepIndex = currentStepIdx)

                Spacer(modifier = Modifier.height(14.dp))

                // Visual Mini Route Canvas (Accessible geometric path representation)
                if (route.geometry.isNotEmpty()) {
                    RoutePathCanvas(
                        coordinates = route.geometry,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                }

                // Assistive disclaimer
                Text(
                    text = "⚠️ Pedestrian route estimates are assistive only. Always prioritize live camera obstacle warnings and cane/guide assistance. Road conditions, construction, and sidewalk accessibility may vary.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                    lineHeight = 15.sp
                )
            }
        }
    }

    // API Key Input Dialog
    if (showApiKeyDialog) {
        AlertDialog(
            onDismissRequest = { showApiKeyDialog = false },
            title = {
                Text(text = "OpenRouteService API Key", fontWeight = FontWeight.Bold)
            },
            text = {
                Column {
                    Text(
                        text = "Enter your free OpenRouteService API key to calculate pedestrian routes. You can obtain a free key at openrouteservice.org.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = apiKeyInput,
                        onValueChange = { apiKeyInput = it },
                        label = { Text("API Key") },
                        placeholder = { Text("Paste 64-character token...") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.setApiKey(apiKeyInput.trim())
                        showApiKeyDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary, contentColor = Color.Black)
                ) {
                    Text("Save Key")
                }
            },
            dismissButton = {
                TextButton(onClick = { showApiKeyDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

/**
 * Route summary section showing distance, duration, and action buttons.
 */
@Composable
private fun RouteSummarySection(
    route: WalkingRoute,
    onOpenInMaps: () -> Unit,
    onClearRoute: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = EmeraldDark.copy(alpha = 0.7f),
                shape = RoundedCornerShape(14.dp)
            )
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = route.destinationName,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = "Walking path calculated (${route.provider})",
                    fontSize = 11.sp,
                    color = Color.White.copy(alpha = 0.75f)
                )
            }

            IconButtonWithDescription(
                icon = Icons.Default.Clear,
                description = "Clear calculated route",
                tint = Color.White.copy(alpha = 0.8f),
                onClick = onClearRoute
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Metrics Chips Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Distance Chip
            Box(
                modifier = Modifier
                    .weight(1f)
                    .background(
                        color = Color.Black.copy(alpha = 0.25f),
                        shape = RoundedCornerShape(10.dp)
                    )
                    .padding(vertical = 10.dp, horizontal = 12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Straighten,
                        contentDescription = "Total Distance",
                        tint = EmeraldPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "Distance",
                            fontSize = 11.sp,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                        Text(
                            text = route.formattedDistance,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }

            // Duration Chip
            Box(
                modifier = Modifier
                    .weight(1f)
                    .background(
                        color = Color.Black.copy(alpha = 0.25f),
                        shape = RoundedCornerShape(10.dp)
                    )
                    .padding(vertical = 10.dp, horizontal = 12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Timer,
                        contentDescription = "Estimated Duration",
                        tint = EmeraldPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "Walking Time",
                            fontSize = 11.sp,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                        Text(
                            text = route.formattedDuration,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Open in external map button
        OutlinedButton(
            onClick = onOpenInMaps,
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .semantics {
                    role = Role.Button
                    contentDescription = "Open route in system map app"
                },
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = Color.White
            )
        ) {
            Icon(
                imageVector = Icons.Default.Map,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = "View in Map App", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

/**
 * Ordered list of turn-by-turn navigation steps.
 */
@Composable
private fun RouteStepsSection(steps: List<RouteStep>, currentStepIndex: Int = -1) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.background.copy(alpha = 0.5f),
                shape = RoundedCornerShape(14.dp)
            )
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Walking Directions (${steps.size} steps)",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Step-by-step",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        steps.forEachIndexed { index, step ->
            RouteStepItem(
                step = step,
                isLast = index == steps.lastIndex,
                isCurrent = index == currentStepIndex
            )
        }
    }
}

/**
 * Individual step item with direction icon, instruction, distance, and current-step highlight.
 */
@Composable
private fun RouteStepItem(step: RouteStep, isLast: Boolean, isCurrent: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = if (isCurrent) EmeraldPrimary.copy(alpha = 0.12f) else Color.Transparent,
                shape = RoundedCornerShape(8.dp)
            )
            .then(
                if (isCurrent) Modifier.border(1.dp, EmeraldPrimary.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                else Modifier
            )
            .padding(horizontal = 6.dp, vertical = 6.dp)
            .semantics {
                contentDescription = if (isCurrent) "Current step: ${step.accessibleText}" else step.accessibleText
            },
        verticalAlignment = Alignment.Top
    ) {
        // Step number badge & icon
        Box(
            modifier = Modifier
                .size(32.dp)
                .background(
                    color = when {
                        isCurrent -> EmeraldPrimary
                        isLast -> EmeraldPrimary.copy(alpha = 0.8f)
                        else -> SapphireSecondary.copy(alpha = 0.2f)
                    },
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = getManeuverIcon(step.maneuverType),
                contentDescription = step.maneuverName,
                tint = if (isCurrent || isLast) Color.Black else SapphireSecondary,
                modifier = Modifier.size(18.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = step.instruction,
                fontSize = 14.sp,
                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                color = if (isCurrent) EmeraldPrimary else MaterialTheme.colorScheme.onSurface
            )
            if (step.streetName.isNotBlank() && !step.instruction.contains(step.streetName, ignoreCase = true)) {
                Text(
                    text = "on ${step.streetName}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Distance & duration for step
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = step.formattedDistance,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = if (isCurrent) EmeraldPrimary else SapphireSecondary
            )
            Text(
                text = step.formattedDuration,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
        }
    }
}

/**
 * Custom Canvas drawing an accessible geometric path preview of the route coordinates.
 * Completely self-contained without requiring heavy external map SDKs.
 */
@Composable
private fun RoutePathCanvas(
    coordinates: List<RouteCoordinates>,
    modifier: Modifier = Modifier
) {
    if (coordinates.size < 2) return

    val minLat = coordinates.minOf { it.latitude }
    val maxLat = coordinates.maxOf { it.latitude }
    val minLon = coordinates.minOf { it.longitude }
    val maxLon = coordinates.maxOf { it.longitude }

    val latSpan = (maxLat - minLat).coerceAtLeast(0.0001)
    val lonSpan = (maxLon - minLon).coerceAtLeast(0.0001)

    Box(
        modifier = modifier
            .background(
                color = Color.Black.copy(alpha = 0.35f),
                shape = RoundedCornerShape(12.dp)
            )
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f),
                shape = RoundedCornerShape(12.dp)
            )
            .padding(12.dp)
            .semantics {
                contentDescription = "Visual route geometry layout showing origin, destination, and turns."
            }
    ) {
        Canvas(modifier = Modifier.fillMaxWidth().height(100.dp)) {
            val width = size.width
            val height = size.height

            val path = Path()
            coordinates.forEachIndexed { index, coord ->
                val x = (((coord.longitude - minLon) / lonSpan) * (width - 40f) + 20f).toFloat()
                // Invert latitude so north is up
                val y = (((maxLat - coord.latitude) / latSpan) * (height - 40f) + 20f).toFloat()

                if (index == 0) {
                    path.moveTo(x, y)
                } else {
                    path.lineTo(x, y)
                }
            }

            // Draw route line
            drawPath(
                path = path,
                color = SapphireSecondary,
                style = Stroke(
                    width = 6f,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )

            // Draw start point (Blue)
            val startCoord = coordinates.first()
            val startX = (((startCoord.longitude - minLon) / lonSpan) * (width - 40f) + 20f).toFloat()
            val startY = (((maxLat - startCoord.latitude) / latSpan) * (height - 40f) + 20f).toFloat()
            drawCircle(color = SapphireSecondary, radius = 9f, center = Offset(startX, startY))
            drawCircle(color = Color.White, radius = 4f, center = Offset(startX, startY))

            // Draw destination point (Emerald Green)
            val endCoord = coordinates.last()
            val endX = (((endCoord.longitude - minLon) / lonSpan) * (width - 40f) + 20f).toFloat()
            val endY = (((maxLat - endCoord.latitude) / latSpan) * (height - 40f) + 20f).toFloat()
            drawCircle(color = EmeraldPrimary, radius = 10f, center = Offset(endX, endY))
            drawCircle(color = Color.Black, radius = 4f, center = Offset(endX, endY))
        }
    }
}

/**
 * Returns an appropriate directional icon for the maneuver type.
 */
private fun getManeuverIcon(maneuverType: Int): ImageVector {
    return when (maneuverType) {
        0 -> Icons.Default.TurnLeft
        1 -> Icons.Default.TurnRight
        2 -> Icons.Default.TurnSharpLeft
        3 -> Icons.Default.TurnSharpRight
        4 -> Icons.Default.TurnSlightLeft
        5 -> Icons.Default.TurnSlightRight
        6 -> Icons.Default.NearMe
        7, 8 -> Icons.Default.AltRoute
        9 -> Icons.Default.Refresh
        10 -> Icons.Default.Check
        11 -> Icons.Default.DirectionsWalk
        12 -> Icons.Default.TurnLeft
        13 -> Icons.Default.TurnRight
        else -> Icons.Default.DirectionsWalk
    }
}

/**
 * Helper icon button with semantics.
 */
@Composable
private fun IconButtonWithDescription(
    icon: ImageVector,
    description: String,
    tint: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .background(Color.White.copy(alpha = 0.15f), shape = CircleShape)
            .padding(4.dp)
            .semantics {
                role = Role.Button
                contentDescription = description
            },
        contentAlignment = Alignment.Center
    ) {
        Button(
            onClick = onClick,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.Transparent,
                contentColor = tint
            ),
            shape = CircleShape,
            modifier = Modifier.size(32.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

/**
 * Active navigation panel showing current step, distance, deviation alerts, and controls.
 */
@Composable
private fun ActiveNavigationSection(
    navigating: NavigationState.Navigating,
    isMuted: Boolean,
    onPause: () -> Unit,
    onStop: () -> Unit,
    onToggleMute: () -> Unit,
    onRecalculate: () -> Unit,
    onOpenLiveCamera: (() -> Unit)? = null
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                shape = RoundedCornerShape(16.dp)
            )
            .border(
                width = 2.dp,
                color = if (navigating.isOffRoute) ColorVeryClose else EmeraldPrimary,
                shape = RoundedCornerShape(16.dp)
            )
            .padding(16.dp)
    ) {
        // Status Row: "NAVIGATION ACTIVE" + Step count
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(
                            if (navigating.isOffRoute) ColorVeryClose else EmeraldPrimary,
                            CircleShape
                        )
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (navigating.isOffRoute) "DEVIATION DETECTED" else "NAVIGATION ACTIVE",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (navigating.isOffRoute) ColorVeryClose else EmeraldPrimary
                )
            }

            Text(
                text = "Step ${navigating.currentStepIndex + 1} of ${navigating.route.steps.size}",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Main Turn / Maneuver Banner
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    color = Color.Black.copy(alpha = 0.35f),
                    shape = RoundedCornerShape(12.dp)
                )
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .background(EmeraldPrimary, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = getManeuverIcon(navigating.currentStep.maneuverType),
                    contentDescription = navigating.currentStep.maneuverName,
                    tint = Color.Black,
                    modifier = Modifier.size(26.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = navigating.currentStep.instruction,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (navigating.distanceToNextManeuverMeters > 0) {
                        "in ${navigating.distanceToNextManeuverMeters.toInt()}m • ${navigating.remainingDistanceMeters.toInt()}m total remaining"
                    } else {
                        "${navigating.remainingDistanceMeters.toInt()}m total remaining"
                    },
                    fontSize = 13.sp,
                    color = EmeraldPrimary,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        // Off-route Deviation Alert (if applicable)
        if (navigating.isOffRoute) {
            Spacer(modifier = Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = ColorVeryClose.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(10.dp)
                    )
                    .border(1.dp, ColorVeryClose.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                    .padding(10.dp)
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Off route deviation",
                            tint = ColorVeryClose,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "You are ~${navigating.offRouteDistanceMeters.toInt()}m off path",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = ColorVeryClose
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Button(
                        onClick = onRecalculate,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = SapphireSecondary,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth().height(38.dp)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = "Recalculate Route", fontSize = 13.sp)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Navigation Action Controls Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Pause Button
            OutlinedButton(
                onClick = onPause,
                modifier = Modifier
                    .weight(1f)
                    .height(46.dp)
                    .semantics {
                        role = Role.Button
                        contentDescription = "Pause Walking Navigation"
                    },
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Default.Pause, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(text = "Pause", fontSize = 13.sp)
            }

            // Stop Button
            OutlinedButton(
                onClick = onStop,
                modifier = Modifier
                    .weight(1f)
                    .height(46.dp)
                    .semantics {
                        role = Role.Button
                        contentDescription = "Stop Walking Navigation"
                    },
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = ColorVeryClose)
            ) {
                Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(text = "Stop", fontSize = 13.sp)
            }

            // Mute / Unmute Navigation Voice
            OutlinedButton(
                onClick = onToggleMute,
                modifier = Modifier
                    .weight(1.2f)
                    .height(46.dp)
                    .semantics {
                        role = Role.Button
                        contentDescription = if (isMuted) {
                            "Unmute navigation voice. Obstacle detection is active."
                        } else {
                            "Mute navigation voice. Obstacle alerts remain active."
                        }
                    },
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = if (isMuted) ColorVeryClose else EmeraldPrimary
                )
            ) {
                Icon(
                    imageVector = if (isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (isMuted) "Unmute Nav" else "Mute Nav",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        if (onOpenLiveCamera != null) {
            Spacer(modifier = Modifier.height(10.dp))
            Button(
                onClick = onOpenLiveCamera,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .semantics {
                        role = Role.Button
                        contentDescription = "Switch to Live Camera View with Obstacle Detection"
                    },
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = EmeraldPrimary.copy(alpha = 0.2f),
                    contentColor = EmeraldPrimary
                )
            ) {
                Icon(Icons.Default.Videocam, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "View Live Camera Obstacle HUD", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/**
 * Navigation paused panel with resume and stop options.
 */
@Composable
private fun PausedNavigationSection(
    paused: NavigationState.Paused,
    onResume: () -> Unit,
    onStop: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = SapphireSecondary.copy(alpha = 0.15f),
                shape = RoundedCornerShape(14.dp)
            )
            .border(1.dp, SapphireSecondary.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Pause, contentDescription = "Navigation Paused", tint = SapphireSecondary)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Navigation Paused (Step ${paused.currentStepIndex + 1})",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = onResume,
                modifier = Modifier.weight(1f).height(44.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary, contentColor = Color.Black)
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = "Resume", fontWeight = FontWeight.Bold)
            }
            OutlinedButton(
                onClick = onStop,
                modifier = Modifier.weight(1f).height(44.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = ColorVeryClose)
            ) {
                Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = "Stop")
            }
        }
    }
}

/**
 * Arrival celebration panel when user has completed navigation.
 */
@Composable
private fun CompletedNavigationSection(
    completed: NavigationState.Completed,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = EmeraldPrimary.copy(alpha = 0.2f),
                shape = RoundedCornerShape(14.dp)
            )
            .border(2.dp, EmeraldPrimary, RoundedCornerShape(14.dp))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.Check,
            contentDescription = "Destination Reached",
            tint = EmeraldPrimary,
            modifier = Modifier.size(36.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "🎉 Destination Reached!",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = completed.destinationName,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
        )
        Spacer(modifier = Modifier.height(12.dp))
        Button(
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth().height(46.dp),
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary, contentColor = Color.Black)
        ) {
            Text(text = "Finish Navigation", fontWeight = FontWeight.Bold)
        }
    }
}
