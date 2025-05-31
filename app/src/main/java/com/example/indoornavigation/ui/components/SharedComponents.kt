package com.example.indoornavigation.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.indoornavigation.data.BeaconInfo

/**
 * Shared UI components used across multiple screens
 */

@Composable
fun SharedScanningControls(
    isScanning: Boolean,
    onStartScan: () -> Unit,
    onStopScan: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = 4.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Beacon Scanning:",
                    style = MaterialTheme.typography.h6,
                    fontWeight = FontWeight.Bold
                )

                Button(
                    onClick = if (isScanning) onStopScan else onStartScan,
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = if (isScanning) Color.Red else Color.Blue
                    )
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = if (isScanning) "Stop Scanning" else "Start Scanning")
                    }
                }
            }

            if (isScanning) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                )
            }
        }
    }
}

@Composable
fun SharedBeaconListItem(
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
                    text = "UUID: ${beacon.uuid.takeLast(8)}...",
                    style = MaterialTheme.typography.caption
                )
                Text(
                    text = "RSSI: ${beacon.rssi} dBm, Distance: ${formatFloat(beacon.distance, 1)}m",
                    style = MaterialTheme.typography.caption
                )
            }

            Button(
                onClick = if (isConnected) onDisconnectClick else onConnectClick,
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = if (isConnected) Color.Red else Color.Green
                ),
                modifier = Modifier.width(120.dp)
            ) {
                Text(
                    text = if (isConnected) "Deselect" else "Select",
                    color = Color.White
                )
            }
        }
    }
}

/**
 * Format a float to a specified number of decimal places
 */
fun formatFloat(value: Float, digits: Int): String {
    return "%.${digits}f".format(value)
}