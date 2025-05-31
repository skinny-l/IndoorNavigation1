package com.example.indoornavigation.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.indoornavigation.data.BeaconInfo
import com.example.indoornavigation.data.PositionData
import com.example.indoornavigation.ui.components.SharedBeaconListItem
import com.example.indoornavigation.ui.components.SharedScanningControls
import com.example.indoornavigation.ui.components.formatFloat
import com.example.indoornavigation.viewmodel.FusionTestViewModel

/**
 * Test screen that lets users select beacons and see their position using all methods combined
 * through sensor fusion for maximum accuracy
 */
@Composable
fun FusionTestScreen(
    viewModel: FusionTestViewModel
) {
    val uiState by viewModel.uiState.collectAsState()
    val fusedPosition by viewModel.fusedPosition.collectAsState()
    val wifiPosition by viewModel.wifiPosition.collectAsState()
    val deadReckoningPosition by viewModel.deadReckoningPosition.collectAsState()
    var isScanning by remember { mutableStateOf(false) }

    // Store position history for trail
    val positionHistory = remember { mutableStateListOf<PositionData>() }
    
    // Add position to history when it changes
    LaunchedEffect(fusedPosition) {
        fusedPosition?.let { newPos ->
            // Only add valid positions
            if (!newPos.x.isNaN() && !newPos.y.isNaN() && 
                !newPos.x.isInfinite() && !newPos.y.isInfinite()) {
                
                // Add position but limit history length
                positionHistory.add(newPos)
                if (positionHistory.size > 20) {
                    positionHistory.removeAt(0)
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Header with title and controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Fusion Positioning Test",
                style = MaterialTheme.typography.h4,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Scanning Controls
        SharedScanningControls(
            isScanning = isScanning,
            onStartScan = {
                isScanning = true
                viewModel.startScanning()
            },
            onStopScan = {
                isScanning = false
                viewModel.stopScanning()
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Beacon List
        Text(
            text = "Available Beacons:",
            style = MaterialTheme.typography.h6,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Show all available beacons
        val availableBeacons = uiState.availableBeacons

        if (availableBeacons.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = 2.dp
            ) {
                Column(
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "No beacons detected",
                        style = MaterialTheme.typography.body1
                    )
                    Text(
                        text = "Please start scanning to detect nearby beacons",
                        style = MaterialTheme.typography.body2,
                        color = Color.Gray
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
            ) {
                items(availableBeacons) { beacon ->
                    SharedBeaconListItem(
                        beacon = beacon,
                        isConnected = beacon.isConnected,
                        onConnectClick = { viewModel.connectBeacon(beacon.id) },
                        onDisconnectClick = { viewModel.disconnectBeacon(beacon.id) }
                    )
                }
            }
        }

        // Connected beacons counter
        Card(
            modifier = Modifier.fillMaxWidth(),
            backgroundColor = if (uiState.connectedBeacons.size >= 3) Color(0xFFE8F5E9) else Color(0xFFFFEBEE),
            elevation = 2.dp
        ) {
            Row(
                modifier = Modifier
                    .padding(8.dp)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Selected Beacons: ${uiState.connectedBeacons.size}",
                    fontWeight = FontWeight.Bold
                )

                if (uiState.connectedBeacons.size >= 3) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = "Sufficient beacons",
                        tint = Color.Green
                    )
                } else {
                    Text(
                        text = "Need at least 3 beacons",
                        color = Color.Red,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Position Display and Visualization
        EnhancedFloorPlanCard(
            bounds = uiState.floorPlanBounds,
            beacons = uiState.connectedBeacons,
            fusedPosition = fusedPosition,
            blePosition = uiState.userPosition,
            wifiPosition = wifiPosition,
            drPosition = deadReckoningPosition,
            positionHistory = positionHistory
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Path Loss Exponent Control
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = 4.dp
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Path Loss Exponent: ${formatFloat(uiState.pathLossExponent, 1)}",
                    style = MaterialTheme.typography.body1
                )
                
                Slider(
                    value = uiState.pathLossExponent,
                    onValueChange = { viewModel.updatePathLossExponent(it) },
                    valueRange = 1.5f..4.0f,
                    steps = 4
                )

                Text(
                    text = "Lower values (1.5-2.0) for open spaces, higher values (3.0-4.0) for environments with obstacles",
                    style = MaterialTheme.typography.caption,
                    color = Color.Gray
                )
            }
        }
    }
}





@Composable
fun EnhancedFloorPlanCard(
    bounds: Pair<Float, Float>,
    beacons: List<BeaconInfo>,
    fusedPosition: PositionData?,
    blePosition: PositionData?,
    wifiPosition: PositionData?,
    drPosition: PositionData?,
    positionHistory: List<PositionData>
) {
    val showBeaconLabels = remember { mutableStateOf(true) }
    val showRssiValues = remember { mutableStateOf(false) }
    val showTrailHistory = remember { mutableStateOf(true) }
    val showAllMethods = remember { mutableStateOf(true) }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = 4.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Floor Plan Map:",
                style = MaterialTheme.typography.h6,
                fontWeight = FontWeight.Bold
            )
            
            // Control options
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = showBeaconLabels.value,
                        onCheckedChange = { showBeaconLabels.value = it }
                    )
                    Text("Labels")
                }
                
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = showRssiValues.value,
                        onCheckedChange = { showRssiValues.value = it }
                    )
                    Text("RSSI")
                }
                
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = showTrailHistory.value,
                        onCheckedChange = { showTrailHistory.value = it }
                    )
                    Text("Trail")
                }
                
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = showAllMethods.value,
                        onCheckedChange = { showAllMethods.value = it }
                    )
                    Text("All Methods")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
                    .background(Color.LightGray)
            ) {
                val width = size.width
                val height = size.height
                val scaleX = width / bounds.first
                val scaleY = height / bounds.second
                
                // Draw position history trail if enabled
                if (showTrailHistory.value && positionHistory.size > 1) {
                    val path = Path()
                    var isFirstPoint = true
                    
                    positionHistory.forEach { pos ->
                        val px = pos.x * scaleX
                        val py = pos.y * scaleY
                        
                        if (isFirstPoint) {
                            path.moveTo(px, py)
                            isFirstPoint = false
                        } else {
                            path.lineTo(px, py)
                        }
                    }
                    
                    drawPath(
                        path = path,
                        color = Color.Green.copy(alpha = 0.7f),
                        style = Stroke(width = 3f)
                    )
                }

                // Draw beacons
                beacons.forEachIndexed { index, beacon ->
                    beacon.position?.let { pos ->
                        val beaconX = pos.first * scaleX
                        val beaconY = pos.second * scaleY
                        val beaconCenter = Offset(beaconX, beaconY)
                        
                        // Draw beacon circle
                        drawCircle(
                            color = Color.Blue,
                            radius = 20f,
                            center = beaconCenter
                        )
                        
                        // Draw beacon range circle
                        drawCircle(
                            color = Color.Blue.copy(alpha = 0.2f),
                            radius = beacon.distance * scaleX,
                            center = beaconCenter,
                            style = Stroke(width = 2f)
                        )
                        
                        // Draw beacon label background
                        drawCircle(
                            color = Color.White,
                            radius = 15f,
                            center = beaconCenter
                        )
                        
                        // Draw beacon number
                        drawCircle(
                            color = Color.Black,
                            radius = 8f,
                            center = beaconCenter
                        )
                        
                        // Show beacon labels if enabled
                        if (showBeaconLabels.value) {
                            // Draw a background for text
                            drawRect(
                                color = Color.White.copy(alpha = 0.7f),
                                topLeft = Offset(beaconX - 40f, beaconY - 40f),
                                size = androidx.compose.ui.geometry.Size(80f, 20f)
                            )
                            
                            // Display beacon number with circle
                            drawCircle(
                                color = Color(0xFF2196F3),
                                radius = 12f,
                                center = Offset(beaconX, beaconY - 30f)
                            )
                        }
                        
                        // Show RSSI values if enabled
                        if (showRssiValues.value) {
                            // Draw a background for RSSI text
                            drawRect(
                                color = Color.White.copy(alpha = 0.7f),
                                topLeft = Offset(beaconX - 40f, beaconY + 20f),
                                size = androidx.compose.ui.geometry.Size(80f, 20f)
                            )
                            
                            // Draw RSSI indicator
                            val rssiLevel = (beacon.rssi + 100) / 10f // normalize to approximately 0-10
                            drawRect(
                                color = when {
                                    beacon.rssi > -70 -> Color.Green
                                    beacon.rssi > -85 -> Color.Yellow
                                    else -> Color.Red
                                },
                                topLeft = Offset(beaconX - 20f, beaconY + 25f),
                                size = androidx.compose.ui.geometry.Size(rssiLevel * 4, 10f)
                            )
                        }
                    }
                }
                
                // If showing all methods, draw positions from each source
                if (showAllMethods.value) {
                    // Draw BLE position (green)
                    blePosition?.let { pos ->
                        if (!pos.x.isNaN() && !pos.y.isNaN() && 
                            !pos.x.isInfinite() && !pos.y.isInfinite()) {
                            drawCircle(
                                color = Color.Green,
                                radius = 10f,
                                center = Offset(pos.x * scaleX, pos.y * scaleY),
                                alpha = 0.7f
                            )
                        }
                    }
                    
                    // Draw WiFi position (cyan)
                    wifiPosition?.let { pos ->
                        if (!pos.x.isNaN() && !pos.y.isNaN() && 
                            !pos.x.isInfinite() && !pos.y.isInfinite()) {
                            drawCircle(
                                color = Color.Cyan,
                                radius = 10f,
                                center = Offset(pos.x * scaleX, pos.y * scaleY),
                                alpha = 0.7f
                            )
                        }
                    }
                    
                    // Draw Dead Reckoning position (magenta)
                    drPosition?.let { pos ->
                        if (!pos.x.isNaN() && !pos.y.isNaN() && 
                            !pos.x.isInfinite() && !pos.y.isInfinite()) {
                            drawCircle(
                                color = Color.Magenta,
                                radius = 10f,
                                center = Offset(pos.x * scaleX, pos.y * scaleY),
                                alpha = 0.7f
                            )
                        }
                    }
                }

                // Draw fused position (main user marker)
                fusedPosition?.let { pos ->
                    if (!pos.x.isNaN() && !pos.y.isNaN() && 
                        !pos.x.isInfinite() && !pos.y.isInfinite()) {
                        val centerX = pos.x * scaleX
                        val centerY = pos.y * scaleY
                        
                        // Draw user position dot
                        drawCircle(
                            color = Color.Red,
                            radius = 15f,
                            center = Offset(centerX, centerY)
                        )
                        
                        // Draw accuracy circle if accuracy is valid
                        if (!pos.accuracy.isNaN() && !pos.accuracy.isInfinite() && pos.accuracy > 0) {
                            drawCircle(
                                color = Color.Red.copy(alpha = 0.2f),
                                radius = pos.accuracy * scaleX,
                                center = Offset(centerX, centerY),
                                style = Stroke(width = 2f)
                            )
                        }
                    } else {
                        // Draw an X in the center if position is invalid
                        val centerX = width / 2
                        val centerY = height / 2
                        val size = 30f
                        
                        drawLine(
                            color = Color.Red,
                            start = Offset(centerX - size, centerY - size),
                            end = Offset(centerX + size, centerY + size),
                            strokeWidth = 5f
                        )
                        drawLine(
                            color = Color.Red,
                            start = Offset(centerX + size, centerY - size),
                            end = Offset(centerX - size, centerY + size),
                            strokeWidth = 5f
                        )
                    }
                }
            }
            
            // Legend
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Canvas(modifier = Modifier.size(12.dp)) {
                        drawCircle(color = Color.Blue)
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = "Beacon", fontSize = MaterialTheme.typography.caption.fontSize)
                }
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Canvas(modifier = Modifier.size(12.dp)) {
                        drawCircle(color = Color.Red)
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = "Fused", fontSize = MaterialTheme.typography.caption.fontSize)
                }
                
                if (showAllMethods.value) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Canvas(modifier = Modifier.size(12.dp)) {
                            drawCircle(color = Color.Green)
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = "BLE", fontSize = MaterialTheme.typography.caption.fontSize)
                    }
                    
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Canvas(modifier = Modifier.size(12.dp)) {
                            drawCircle(color = Color.Cyan)
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = "WiFi", fontSize = MaterialTheme.typography.caption.fontSize)
                    }
                    
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Canvas(modifier = Modifier.size(12.dp)) {
                            drawCircle(color = Color.Magenta)
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = "DR", fontSize = MaterialTheme.typography.caption.fontSize)
                    }
                }
            }
            
            // Position information
            fusedPosition?.let { pos ->
                if (!pos.x.isNaN() && !pos.y.isNaN() && 
                    !pos.x.isInfinite() && !pos.y.isInfinite()) {
                    Text(
                        text = "Position: (X: ${formatFloat(pos.x, 1)}m, Y: ${formatFloat(pos.y, 1)}m)",
                        style = MaterialTheme.typography.body1,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                    
                    Text(
                        text = "Accuracy: ${formatFloat(pos.accuracy, 1)} meters",
                        style = MaterialTheme.typography.body1
                    )
                }
            }
        }
    }
}

// Extension function
