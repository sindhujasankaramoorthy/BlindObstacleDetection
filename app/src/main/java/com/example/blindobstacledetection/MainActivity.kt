package com.example.blindobstacledetection

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.blindobstacledetection.ui.theme.BlindObstacleDetectionTheme

class MainActivity : ComponentActivity() {

    private val cameraPermission =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            if (isGranted) {
                // Camera permission granted
            }
        }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            BlindObstacleDetectionTheme {
                HomeScreen(
                    onStartClick = {
                        cameraPermission.launch(
                            Manifest.permission.CAMERA
                        )
                    }
                )
            }
        }
    }
}


@Composable
fun HomeScreen(
    onStartClick: () -> Unit
) {

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),

        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {

        Text(
            text = "Blind Obstacle Detection",
            fontSize = 28.sp
        )

        Spacer(
            modifier = Modifier.height(20.dp)
        )

        Text(
            text = "🦯",
            fontSize = 70.sp
        )

        Spacer(
            modifier = Modifier.height(20.dp)
        )

        Text(
            text = "Helping visually impaired navigate safely",
            fontSize = 18.sp
        )


        Spacer(
            modifier = Modifier.height(40.dp)
        )


        Button(
            onClick = {
                onStartClick()
            }
        ) {
            Text("Start Detection")
        }


        Spacer(
            modifier = Modifier.height(30.dp)
        )


        Text(
            text = "Status : Ready"
        )
    }
}