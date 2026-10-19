package com.example.blindobstacledetection

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.blindobstacledetection.ui.screens.AboutScreen
import com.example.blindobstacledetection.ui.screens.CameraScreen
import com.example.blindobstacledetection.ui.screens.HistoryScreen
import com.example.blindobstacledetection.ui.screens.HomeScreen
import com.example.blindobstacledetection.ui.screens.SettingsScreen
import com.example.blindobstacledetection.ui.screens.SplashScreen
import com.example.blindobstacledetection.ui.theme.BlindObstacleDetectionTheme
import com.example.blindobstacledetection.viewmodel.DetectionViewModel
import com.example.blindobstacledetection.viewmodel.DestinationViewModel
import com.example.blindobstacledetection.viewmodel.HistoryViewModel
import com.example.blindobstacledetection.viewmodel.MainViewModel
import com.example.blindobstacledetection.viewmodel.SettingsViewModel

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val settingsViewModel: SettingsViewModel = viewModel()
            val mainViewModel: MainViewModel = viewModel()
            val detectionViewModel: DetectionViewModel = viewModel()
            val historyViewModel: HistoryViewModel = viewModel()
            val destinationViewModel: DestinationViewModel = viewModel()

            val settingsState by settingsViewModel.settingsState.collectAsState()

            BlindObstacleDetectionTheme(themeSetting = settingsState.theme) {
                val navController = rememberNavController()

                NavHost(
                    navController = navController,
                    startDestination = "splash"
                ) {
                    composable("splash") {
                        SplashScreen(
                            navController = navController,
                            mainViewModel = mainViewModel
                        )
                    }

                    composable("home") {
                        HomeScreen(
                            navController = navController,
                            mainViewModel = mainViewModel,
                            destinationViewModel = destinationViewModel
                        )
                    }

                    composable("camera") {
                        CameraScreen(
                            navController = navController,
                            viewModel = detectionViewModel,
                            destinationViewModel = destinationViewModel
                        )
                    }

                    composable("history") {
                        HistoryScreen(
                            navController = navController,
                            viewModel = historyViewModel
                        )
                    }

                    composable("settings") {
                        SettingsScreen(
                            navController = navController,
                            viewModel = settingsViewModel
                        )
                    }

                    composable("about") {
                        AboutScreen(
                            navController = navController
                        )
                    }
                }
            }
        }
    }
}