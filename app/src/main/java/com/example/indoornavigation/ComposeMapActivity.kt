package com.example.indoornavigation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.indoornavigation.ui.map.FloorPlanWithDot
import com.example.indoornavigation.ui.map.FloorPlanWithMovingDot
import com.example.indoornavigation.ui.theme.IndoorNavigationTheme
import kotlinx.coroutines.delay

/**
 * Activity that demonstrates the Jetpack Compose implementation of a floor plan with a red dot
 * Use this as an alternative to the traditional view-based approach
 */
class ComposeMapActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            IndoorNavigationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colors.background
                ) {
                    MainScreenWithControls()
                }
            }
        }
    }
}

/**
 * Main screen that displays a floor plan with a moving dot
 * The dot position is updated every 2 seconds for demonstration
 */
@Composable
fun MainScreen() {
    // State for the dot position
    var dotX by remember { mutableStateOf(400f) }
    var dotY by remember { mutableStateOf(600f) }

    // Simulate updating position every few seconds
    LaunchedEffect(Unit) {
        while (true) {
            delay(2000) // Update every 2 seconds
            
            // Move dot randomly but stay within reasonable bounds
            dotX += (-20..20).random()
            dotY += (-20..20).random()
            
            // Keep within bounds of the map (approximate)
            dotX = dotX.coerceIn(100f, 700f)
            dotY = dotY.coerceIn(100f, 700f)
        }
    }

    // Display the floor plan with moving dot
    FloorPlanWithMovingDot(dotX, dotY)
}

/**
 * Enhanced main screen with manual controls to move the dot
 */
@Composable
fun MainScreenWithControls() {
    // State for the dot position
    var dotX by remember { mutableStateOf(400f) }
    var dotY by remember { mutableStateOf(600f) }
    var autoMove by remember { mutableStateOf(false) }
    
    // Automatically move the dot if auto-move is enabled
    LaunchedEffect(autoMove) {
        while (autoMove) {
            delay(500) // Update more frequently for smoother movement
            
            // Move dot randomly but stay within reasonable bounds
            dotX += (-10..10).random()
            dotY += (-10..10).random()
            
            // Keep within bounds of the map (approximate)
            dotX = dotX.coerceIn(100f, 700f)
            dotY = dotY.coerceIn(100f, 700f)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // The map with moving dot takes most of the space
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(500.dp)
        ) {
            FloorPlanWithMovingDot(dotX, dotY)
        }
        
        // Controls at the bottom
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                "Position: (${dotX.toInt()}, ${dotY.toInt()})",
                style = MaterialTheme.typography.subtitle1
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Movement controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = { dotY -= 20f },
                    modifier = Modifier.fillMaxWidth(0.33f)
                ) {
                    Text("↑")
                }
                
                Button(
                    onClick = { autoMove = !autoMove },
                    modifier = Modifier.fillMaxWidth(0.5f)
                ) {
                    Text(if (autoMove) "Stop" else "Auto")
                }
            }
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = { dotX -= 20f },
                    modifier = Modifier.fillMaxWidth(0.33f)
                ) {
                    Text("←")
                }
                
                Button(
                    onClick = { dotY += 20f },
                    modifier = Modifier.fillMaxWidth(0.33f)
                ) {
                    Text("↓")
                }
                
                Button(
                    onClick = { dotX += 20f },
                    modifier = Modifier.fillMaxWidth(0.33f)
                ) {
                    Text("→")
                }
            }
        }
    }
}

@Composable
fun FloorPlanMap() {
    Box(modifier = Modifier.fillMaxSize()) {
        FloorPlanWithDot(
            userPosition = Offset(400f, 600f)
        )
    }
}

@Preview(showBackground = true)
@Composable
fun FloorPlanPreview() {
    IndoorNavigationTheme {
        MainScreenWithControls()
    }
}
