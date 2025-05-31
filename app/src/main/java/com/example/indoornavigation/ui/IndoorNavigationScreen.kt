package com.example.indoornavigation.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.FloatingActionButton
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.indoornavigation.R
import com.example.indoornavigation.utils.format
import com.example.indoornavigation.viewmodel.IndoorNavigationViewModel

/**
 * The main screen for indoor navigation, showing a floor plan with the user's position
 * and managed beacons. Also allows for beacon management through a modal sheet.
 */
@Composable
fun IndoorNavigationScreen(
    viewModel: IndoorNavigationViewModel = viewModel()
) {
    val managedBeacons by viewModel.managedBeacons.collectAsState()
    val userPosition by viewModel.userPosition.collectAsState()
    val accuracy by viewModel.accuracy.collectAsState()
    
    var showBeaconManager by remember { mutableStateOf(false) }
    
    Box(modifier = Modifier.fillMaxSize()) {
        // Show floor plan with user position if available
        userPosition?.let { position ->
            FloorPlanVisualization(
                floorPlan = R.drawable.sample_floor_plan,
                userPosition = position,
                beacons = managedBeacons,
                accuracy = accuracy
            )
        } ?: run {
            // No position yet
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text("Waiting for position data...")
            }
        }
        
        // Accuracy indicator
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 16.dp)
                .background(Color.White.copy(alpha = 0.7f), RoundedCornerShape(8.dp))
                .padding(8.dp)
        ) {
            Text("Accuracy: ${accuracy.format(2)} meters")
        }
        
        // Beacon manager button
        FloatingActionButton(
            onClick = { showBeaconManager = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        ) {
            Icon(Icons.Default.Settings, "Beacon Manager")
        }
    }
    
    // Beacon management sheet
    if (showBeaconManager) {
        Modal(onDismiss = { showBeaconManager = false }) {
            BeaconManagementScreen(
                beacons = managedBeacons,
                onAddBeacon = viewModel::addBeacon,
                onDeleteBeacon = viewModel::deleteBeacon
            )
        }
    }
}