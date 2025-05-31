package com.example.indoornavigation.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.indoornavigation.data.BeaconInfo
import com.example.indoornavigation.data.PositionData
import com.example.indoornavigation.data.PositioningMethod
import com.example.indoornavigation.data.TrilaterationState
import com.example.indoornavigation.viewmodel.TrilaterationViewModel
import kotlinx.coroutines.flow.StateFlow

/**
 * A common interface for both ViewModel types
 */
interface TrilaterationViewModelInterface {
    val uiState: StateFlow<TrilaterationState>
    fun connectBeacon(beaconId: String)
    fun disconnectBeacon(beaconId: String)
    fun startScanning()
    fun stopScanning()
    fun updatePathLossExponent(value: Float)
    fun setPositioningMethod(method: PositioningMethod)
}

@Composable
fun TrilaterationTestScreen(
    viewModel: TrilaterationViewModelInterface
) {
    val uiState by viewModel.uiState.collectAsState()
    var isScanning by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Header
        Text(
            text = "Trilateration Testing",
            style = MaterialTheme.typography.h4,
            fontWeight = FontWeight.Bold
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Beacon List
        BeaconListCard(
            availableBeacons = uiState.availableBeacons,
            onConnectClick = { viewModel.connectBeacon(it) },
            onDisconnectClick = { viewModel.disconnectBeacon(it) }
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Position and Accuracy Display (only if at least 3 beacons are connected)
        if (uiState.connectedBeacons.size >= 3) {
            PositionCard(
                userPosition = uiState.userPosition,
                connectedBeacons = uiState.connectedBeacons
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Floor Plan Visualization
            FloorPlanCard(
                bounds = uiState.floorPlanBounds,
                beacons = uiState.connectedBeacons,
                userPosition = uiState.userPosition
            )
        } else {
            // Card with instructions
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = 4.dp
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Select at least 3 beacons to start trilateration",
                        style = MaterialTheme.typography.h6,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = "${uiState.connectedBeacons.size}/3 beacons selected",
                        style = MaterialTheme.typography.body1
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Controls
        ControlsCard(
            isScanning = uiState.isScanning,
            pathLossExponent = uiState.pathLossExponent,
            onStartScanning = { viewModel.startScanning() },
            onStopScanning = { viewModel.stopScanning() },
            onPathLossChange = { viewModel.updatePathLossExponent(it) },
            currentMethod = uiState.positioningMethod,
            onMethodChange = { viewModel.setPositioningMethod(it) }
        )
    }
}

@Composable
fun BeaconListCard(
    availableBeacons: List<BeaconInfo>,
    onConnectClick: (String) -> Unit,
    onDisconnectClick: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = 4.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Beacon List:",
                style = MaterialTheme.typography.h6,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            if (availableBeacons.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "No beacons detected. Start scanning to find beacons.")
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                ) {
                    items(availableBeacons) { beacon ->
                        BeaconListItem(
                            beacon = beacon,
                            isConnected = beacon.isConnected,
                            onConnectClick = { onConnectClick(beacon.id) },
                            onDisconnectClick = { onDisconnectClick(beacon.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun BeaconListItem(
    beacon: BeaconInfo,
    isConnected: Boolean,
    onConnectClick: () -> Unit,
    onDisconnectClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .border(
                width = if (isConnected) 2.dp else 0.dp,
                color = if (isConnected) Color.Green else Color.Transparent
            ),
        elevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = beacon.name,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "RSSI: ${beacon.rssi} dBm, Distance: ${beacon.distance.format(1)}m",
                    style = MaterialTheme.typography.caption
                )
            }
            
            Button(
                onClick = if (isConnected) onDisconnectClick else onConnectClick,
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = if (isConnected) Color.Red else Color.Green
                )
            ) {
                Text(
                    text = if (isConnected) "Disconnect" else "Connect",
                    color = Color.White
                )
            }
        }
    }
}

@Composable
fun PositionCard(
    userPosition: PositionData?,
    connectedBeacons: List<BeaconInfo>
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = 4.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "User Position: ${userPosition?.let { "(X: ${it.x.format(1)}m, Y: ${it.y.format(1)}m)" } ?: "Not Available"}",
                style = MaterialTheme.typography.body1,
                fontWeight = FontWeight.Bold
            )
            
            Text(
                text = "Accuracy: ${userPosition?.accuracy?.format(1) ?: "N/A"} meters",
                style = MaterialTheme.typography.body1
            )
            
            // Display algorithm used
            userPosition?.let {
                Text(
                    text = "Algorithm: ${it.algorithm}",
                    style = MaterialTheme.typography.body1,
                    color = Color.Blue,
                    fontWeight = FontWeight.Bold
                )
                
                // Additional fusion information
                if (it.algorithm == "Sensor Fusion") {
                    Text(
                        text = "Combining multiple positioning methods for optimal accuracy",
                        style = MaterialTheme.typography.caption,
                        color = Color.Gray
                    )
                    
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        // Signal quality indicator
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            val signalQuality = when {
                                connectedBeacons.size >= 3 && connectedBeacons.all { it.rssi > -80 } -> "Good"
                                connectedBeacons.size >= 2 -> "Medium"
                                else -> "Poor"
                            }
                            
                            val signalColor = when(signalQuality) {
                                "Good" -> Color.Green
                                "Medium" -> Color(0xFFFF9800) // Orange
                                else -> Color.Red
                            }
                            
                            Text(
                                text = "Signal Quality",
                                style = MaterialTheme.typography.caption
                            )
                            
                            Text(
                                text = signalQuality,
                                color = signalColor,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        
                        // Update frequency
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "Update Rate",
                                style = MaterialTheme.typography.caption
                            )
                            
                            Text(
                                text = "1Hz",
                                fontWeight = FontWeight.Bold
                            )
                        }
                        
                        // Beacons used
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "Beacons Used",
                                style = MaterialTheme.typography.caption
                            )
                            
                            Text(
                                text = connectedBeacons.size.toString(),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            connectedBeacons.forEachIndexed { index, beacon ->
                Text(
                    text = "${beacon.name}: ${beacon.distance.format(1)}m",
                    style = MaterialTheme.typography.body2
                )
            }
        }
    }
}

@Composable
fun FloorPlanCard(
    bounds: Pair<Float, Float>,
    beacons: List<BeaconInfo>,
    userPosition: PositionData?
) {
    val positionHistory = remember { mutableStateListOf<PositionData>() }
    
    // Add position to history when it changes
    LaunchedEffect(userPosition) {
        userPosition?.let { newPos ->
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
                
                // Draw position history trail
                if (positionHistory.size > 1) {
                    val path = androidx.compose.ui.graphics.Path()
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
                beacons.forEach { beacon ->
                    beacon.position?.let { pos ->
                        val beaconX = pos.first * scaleX
                        val beaconY = pos.second * scaleY
                        
                        drawCircle(
                            color = Color.Blue,
                            radius = 20f,
                            center = Offset(beaconX, beaconY)
                        )
                        
                        // Draw beacon range circle
                        drawCircle(
                            color = Color.Blue.copy(alpha = 0.2f),
                            radius = beacon.distance * scaleX,
                            center = Offset(beaconX, beaconY),
                            style = Stroke(width = 2f)
                        )
                    }
                }
                
                // Draw user position
                userPosition?.let { pos ->
                    if (!pos.x.isNaN() && !pos.y.isNaN() && 
                        !pos.x.isInfinite() && !pos.y.isInfinite()) {
                        val centerX = pos.x * scaleX
                        val centerY = pos.y * scaleY
                        
                        drawCircle(
                            color = Color.Red,
                            radius = 15f,
                            center = Offset(centerX, centerY)
                        )
                        
                        if (!pos.accuracy.isNaN() && !pos.accuracy.isInfinite() && pos.accuracy > 0) {
                            drawCircle(
                                color = Color.Red.copy(alpha = 0.2f),
                                radius = pos.accuracy * scaleX,
                                center = Offset(centerX, centerY),
                                style = Stroke(width = 2f)
                            )
                        }
                    }
                }
            }
            
            // Legend
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Canvas(modifier = Modifier.size(12.dp)) {
                        drawCircle(color = Color.Blue)
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = "Beacon")
                    
                    Spacer(modifier = Modifier.width(16.dp))
                    
                    Canvas(modifier = Modifier.size(12.dp)) {
                        drawCircle(color = Color.Red)
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = "User")
                    
                    Spacer(modifier = Modifier.width(16.dp))
                    
                    Canvas(modifier = Modifier.size(12.dp)) {
                        drawCircle(
                            color = Color.Green.copy(alpha = 0.7f),
                            style = Stroke(width = 2f)
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = "Trail")
                }
            }
        }
    }
}

@Composable
fun ControlsCard(
    isScanning: Boolean,
    pathLossExponent: Float,
    onStartScanning: () -> Unit,
    onStopScanning: () -> Unit,
    onPathLossChange: (Float) -> Unit,
    currentMethod: PositioningMethod,
    onMethodChange: (PositioningMethod) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = 4.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Scanning:",
                    style = MaterialTheme.typography.body1
                )
                
                Button(
                    onClick = {
                        if (isScanning) onStopScanning() else onStartScanning()
                    }
                ) {
                    Text(text = if (isScanning) "Stop" else "Start")
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Positioning method selector
            Text(
                text = "Positioning Method:",
                style = MaterialTheme.typography.body1,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            
            // Radio buttons for positioning methods
            Column(Modifier.fillMaxWidth()) {
                PositioningMethod.values().forEach { method ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = (method == currentMethod),
                                onClick = { onMethodChange(method) }
                            )
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = (method == currentMethod),
                            onClick = { onMethodChange(method) }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        
                        // Method name with recommended tag for fusion
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = method.name.replace('_', ' ').lowercase().capitalize(),
                                    style = MaterialTheme.typography.body2,
                                    fontWeight = if (method == PositioningMethod.FUSION) 
                                        FontWeight.Bold else FontWeight.Normal
                                )
                                
                                if (method == PositioningMethod.FUSION) {
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Card(
                                        backgroundColor = Color(0xFF4CAF50),
                                        contentColor = Color.White,
                                        modifier = Modifier.height(20.dp)
                                    ) {
                                        Text(
                                            text = "RECOMMENDED",
                                            style = MaterialTheme.typography.caption,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 4.dp)
                                        )
                                    }
                                }
                            }
                            
                            // Description for Fusion method
                            if (method == PositioningMethod.FUSION) {
                                Text(
                                    text = "Combines all methods for best accuracy",
                                    style = MaterialTheme.typography.caption,
                                    color = Color.Gray
                                )
                            }
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "Path Loss Exponent: ${pathLossExponent.format(1)}",
                style = MaterialTheme.typography.body1
            )
            
            Slider(
                value = pathLossExponent,
                onValueChange = onPathLossChange,
                valueRange = 1.5f..4.0f,
                steps = 5
            )
            
            Text(
                text = "Lower values (1.5-2.0) for open spaces, higher values (3.0-4.0) for environments with obstacles",
                style = MaterialTheme.typography.caption,
                color = Color.Gray
            )
        }
    }
}

// Extension function
fun Float.format(digits: Int) = "%.${digits}f".format(this)

// Extension function to capitalize the first letter of a string
fun String.capitalize() = replaceFirstChar { 
    if (it.isLowerCase()) it.titlecase() else it.toString() 
}