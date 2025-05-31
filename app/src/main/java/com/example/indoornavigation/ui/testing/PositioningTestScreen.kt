package com.example.indoornavigation.ui.testing

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Button
import androidx.compose.material.Card
import androidx.compose.material.Divider
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Slider
import androidx.compose.material.Switch
import androidx.compose.material.Tab
import androidx.compose.material.TabRow
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.indoornavigation.data.models.PositioningState
import com.example.indoornavigation.data.models.PositioningSources
import com.example.indoornavigation.data.models.TestBeacon
import com.example.indoornavigation.data.models.TestPosition
import com.example.indoornavigation.data.models.TestSensor
import com.example.indoornavigation.data.models.TestWiFi
import com.example.indoornavigation.utils.format
import com.example.indoornavigation.viewmodel.PositioningTestViewModel
import kotlin.math.abs

@Composable
fun PositioningTestScreen(
    viewModel: PositioningTestViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    
    LaunchedEffect(Unit) {
        viewModel.startPositioning()
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Positioning Test",
                style = MaterialTheme.typography.h4,
                fontWeight = FontWeight.Bold
            )
            
            Switch(
                checked = uiState.isScanning,
                onCheckedChange = { 
                    if (it) viewModel.startPositioning() 
                    else viewModel.stopPositioning() 
                }
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Add positioning visualization
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = 4.dp
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Position Visualization",
                    style = MaterialTheme.typography.h6,
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Floor selector
                var selectedFloor = remember { mutableStateOf(uiState.currentPosition.floor) }
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Floor: ${selectedFloor.value}")
                    
                    Row {
                        Button(
                            onClick = { 
                                selectedFloor.value = (selectedFloor.value - 1).coerceAtLeast(0)
                            },
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Text("-")
                        }
                        
                        Button(
                            onClick = { 
                                selectedFloor.value = selectedFloor.value + 1
                            }
                        ) {
                            Text("+")
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // 2D position visualization
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                        .border(1.dp, Color.Gray)
                        .clip(MaterialTheme.shapes.medium)
                ) {
                    Canvas(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        val canvasWidth = size.width
                        val canvasHeight = size.height
                        
                        // Draw grid
                        val gridSize = 10
                        val cellWidth = canvasWidth / gridSize
                        val cellHeight = canvasHeight / gridSize
                        
                        // Draw grid lines
                        for (i in 0..gridSize) {
                            // Vertical lines
                            drawLine(
                                color = Color.LightGray,
                                start = Offset(i * cellWidth, 0f),
                                end = Offset(i * cellWidth, canvasHeight),
                                strokeWidth = 1f
                            )
                            
                            // Horizontal lines
                            drawLine(
                                color = Color.LightGray,
                                start = Offset(0f, i * cellHeight),
                                end = Offset(canvasWidth, i * cellHeight),
                                strokeWidth = 1f
                            )
                        }
                        
                        // Draw reference coordinates
                        drawCircle(
                            color = Color.Black,
                            radius = 4f,
                            center = Offset(0f, canvasHeight)
                        )
                        
                        // Scale factor (assuming position values range from 0 to 100)
                        val scaleX = canvasWidth / 100f
                        val scaleY = canvasHeight / 100f
                        
                        // Draw positions only if they're on the current floor
                        if (uiState.fusedPosition?.floor == selectedFloor.value) {
                            uiState.fusedPosition?.let {
                                drawCircle(
                                    color = Color.Blue,
                                    radius = 8f,
                                    center = Offset(it.x.toFloat() * scaleX, canvasHeight - it.y.toFloat() * scaleY),
                                    alpha = 0.7f
                                )
                            }
                        }
                        
                        if (uiState.blePosition?.floor == selectedFloor.value) {
                            uiState.blePosition?.let {
                                drawCircle(
                                    color = Color.Green,
                                    radius = 8f,
                                    center = Offset(it.x.toFloat() * scaleX, canvasHeight - it.y.toFloat() * scaleY),
                                    alpha = 0.7f
                                )
                            }
                        }
                        
                        if (uiState.wifiPosition?.floor == selectedFloor.value) {
                            uiState.wifiPosition?.let {
                                drawCircle(
                                    color = Color.Red,
                                    radius = 8f,
                                    center = Offset(it.x.toFloat() * scaleX, canvasHeight - it.y.toFloat() * scaleY),
                                    alpha = 0.7f
                                )
                            }
                        }
                        
                        if (uiState.deadReckoningPosition?.floor == selectedFloor.value) {
                            uiState.deadReckoningPosition?.let {
                                drawCircle(
                                    color = Color.Magenta,
                                    radius = 8f,
                                    center = Offset(it.x.toFloat() * scaleX, canvasHeight - it.y.toFloat() * scaleY),
                                    alpha = 0.7f
                                )
                            }
                        }
                        
                        // Draw current position with accuracy circle
                        if (uiState.currentPosition.floor == selectedFloor.value) {
                            val position = uiState.currentPosition
                            val x = position.x.toFloat() * scaleX
                            val y = canvasHeight - position.y.toFloat() * scaleY
                            val radius = position.accuracy.toFloat() * ((scaleX + scaleY) / 2)
                            
                            // Draw accuracy circle
                            drawCircle(
                                color = Color.Black,
                                radius = radius,
                                center = Offset(x, y),
                                alpha = 0.1f
                            )
                            
                            // Draw position dot
                            drawCircle(
                                color = Color.Black,
                                radius = 10f,
                                center = Offset(x, y)
                            )
                        }
                    }
                    
                    // Position legend
                    Column(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .background(Color.Black, shape = MaterialTheme.shapes.small)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Current", fontSize = 12.sp)
                        }
                        
                        Spacer(modifier = Modifier.height(4.dp))
                        
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .background(Color.Blue, shape = MaterialTheme.shapes.small)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Fused", fontSize = 12.sp)
                        }
                        
                        Spacer(modifier = Modifier.height(4.dp))
                        
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .background(Color.Green, shape = MaterialTheme.shapes.small)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("BLE", fontSize = 12.sp)
                        }
                        
                        Spacer(modifier = Modifier.height(4.dp))
                        
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .background(Color.Red, shape = MaterialTheme.shapes.small)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("WiFi", fontSize = 12.sp)
                        }
                        
                        Spacer(modifier = Modifier.height(4.dp))
                        
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .background(Color.Magenta, shape = MaterialTheme.shapes.small)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("DR", fontSize = 12.sp)
                        }
                    }
                    
                    // Coordinate display
                    Text(
                        text = "Scale: 0-100 meters",
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(8.dp),
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Position coordinates display
                Text(
                    text = "Current: (${uiState.currentPosition.x.format(2)}, ${uiState.currentPosition.y.format(2)}) " +
                           "Floor: ${uiState.currentPosition.floor}, " +
                           "Accuracy: ${uiState.currentPosition.accuracy.format(2)}m",
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Current Position
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = 4.dp
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Current Position",
                    style = MaterialTheme.typography.h6,
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                PositionDisplay("Current", uiState.currentPosition, Color.Black)
                uiState.fusedPosition?.let { PositionDisplay("Fused", it, Color.Blue) }
                uiState.blePosition?.let { PositionDisplay("BLE", it, Color.Green) }
                uiState.wifiPosition?.let { PositionDisplay("WiFi", it, Color.Red) }
                uiState.deadReckoningPosition?.let { PositionDisplay("Dead Reckoning", it, Color.Magenta) }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Position Sources
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = 4.dp
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Position Sources",
                    style = MaterialTheme.typography.h6,
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                SourceToggle("BLE", PositioningSources.BLE, uiState, viewModel)
                SourceToggle("WiFi", PositioningSources.WIFI, uiState, viewModel)
                SourceToggle("Dead Reckoning", PositioningSources.DEAD_RECKONING, uiState, viewModel)
                SourceToggle("Sensor Fusion", PositioningSources.SENSOR_FUSION, uiState, viewModel)
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Add scan interval slider
        Spacer(modifier = Modifier.height(16.dp))
        
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = 4.dp
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Scan Settings",
                    style = MaterialTheme.typography.h6,
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                var scanInterval by remember { mutableStateOf(1f) }
                
                Text("Scan Interval: ${scanInterval.toInt()} seconds")
                
                Slider(
                    value = scanInterval,
                    onValueChange = { scanInterval = it },
                    valueRange = 1f..10f,
                    steps = 9,
                    modifier = Modifier.fillMaxWidth()
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Button(
                        onClick = { viewModel.setScanInterval(scanInterval.toLong() * 1000) }
                    ) {
                        Text("Apply Interval")
                    }
                    
                    Button(
                        onClick = { viewModel.forceRescan() }
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Force Rescan")
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Force Rescan")
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Tab layout for beacon, wifi, and sensor data
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(340.dp),
            elevation = 4.dp
        ) {
            Column {
                var selectedTab by remember { mutableStateOf(0) }
                
                TabRow(
                    selectedTabIndex = selectedTab,
                    backgroundColor = MaterialTheme.colors.surface,
                    contentColor = MaterialTheme.colors.primary
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Beacons (${uiState.detectedBeacons.size})") }
                    )
                    
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("WiFi (${uiState.detectedWiFi.size})") }
                    )
                    
                    Tab(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        text = { Text("Sensors") }
                    )
                }
                
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    when (selectedTab) {
                        0 -> {
                            // Beacons tab
                            if (uiState.detectedBeacons.isNotEmpty()) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(16.dp)
                                        .verticalScroll(rememberScrollState())
                                ) {
                                    uiState.detectedBeacons.forEach { beacon ->
                                        BeaconDisplay(beacon)
                                        Divider(modifier = Modifier.padding(vertical = 4.dp))
                                    }
                                }
                            } else {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("No beacons detected")
                                }
                            }
                        }
                        
                        1 -> {
                            // WiFi tab
                            if (uiState.detectedWiFi.isNotEmpty()) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(16.dp)
                                        .verticalScroll(rememberScrollState())
                                ) {
                                    uiState.detectedWiFi.forEach { wifi ->
                                        WiFiDisplay(wifi)
                                        Divider(modifier = Modifier.padding(vertical = 4.dp))
                                    }
                                }
                            } else {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("No WiFi APs detected")
                                }
                            }
                        }
                        
                        2 -> {
                            // Sensors tab
                            if (uiState.sensorData.isNotEmpty()) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(16.dp)
                                        .verticalScroll(rememberScrollState())
                                ) {
                                    uiState.sensorData.forEach { (type, data) ->
                                        SensorDisplay(type, data)
                                        Divider(modifier = Modifier.padding(vertical = 8.dp))
                                    }
                                }
                            } else {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("No sensor data available")
                                }
                            }
                        }
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Action buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Button(
                onClick = { viewModel.resetPosition() },
                modifier = Modifier.weight(1f)
            ) {
                Text("Reset Position")
            }
            
            Spacer(modifier = Modifier.width(8.dp))
            
            Button(
                onClick = { viewModel.saveTestData() },
                modifier = Modifier.weight(1f)
            ) {
                Text("Save Test Data")
            }
        }
        
        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
fun SourceToggle(
    label: String,
    source: Int,
    uiState: PositioningState,
    viewModel: PositioningTestViewModel
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label)
        Switch(
            checked = uiState.activeSourcesMask and source != 0,
            onCheckedChange = { viewModel.toggleSource(source) }
        )
    }
}

@Composable
fun PositionDisplay(label: String, position: TestPosition, color: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Text(
            text = label,
            color = color,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(100.dp)
        )
        Text(
            text = "X: ${position.x.format(2)}, Y: ${position.y.format(2)}, " +
                   "Floor: ${position.floor}, Accuracy: ${position.accuracy.format(2)}m"
        )
    }
}

@Composable
fun BeaconDisplay(beacon: TestBeacon) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        // First row - ID and signal strength
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Use beacon name if available, otherwise use a friendly generated name from ID
            val displayName = when {
                beacon.name.isNotEmpty() -> beacon.name
                beacon.id.contains("-") -> beacon.id.substringAfterLast("-")
                beacon.id.contains(":") -> "Beacon-${beacon.id.takeLast(5)}"
                else -> beacon.id.take(12)
            }
            
            Text(
                text = displayName,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            
            Text(
                text = "RSSI: ${beacon.rssi}dBm",
                color = when {
                    beacon.rssi > -60 -> Color.Green
                    beacon.rssi > -80 -> Color(0xFFFF9800)  // Orange
                    else -> Color.Red
                }
            )
        }
        
        // Second row - Full ID and distance
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = beacon.id.take(16) + (if (beacon.id.length > 16) "..." else ""),
                style = MaterialTheme.typography.caption,
                color = Color.Gray,
                modifier = Modifier.weight(1f)
            )
            
            Text(
                text = "Distance: ${beacon.distance.format(2)}m",
                style = MaterialTheme.typography.caption
            )
        }
    }
}

@Composable
fun WiFiDisplay(wifi: TestWiFi) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
    ) {
        Text(
            text = wifi.ssid,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(100.dp)
        )
        Text(
            text = "RSSI: ${wifi.rssi}dBm, BSSID: ${wifi.bssid}"
        )
    }
}

@Composable
fun SensorDisplay(type: String, data: TestSensor) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Text(
            text = type.capitalize(),
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "X: ${data.values[0].format(3)}, Y: ${data.values[1].format(3)}, Z: ${data.values[2].format(3)}"
        )
    }
}

private fun String.capitalize(): String {
    return this.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
}