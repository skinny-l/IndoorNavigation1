package com.example.indoornavigation.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.indoornavigation.data.models.ManagedBeacon
import java.util.*

/**
 * Screen for managing beacons (add, edit, delete)
 */
@Composable
fun BeaconManagementScreen(
    beacons: List<ManagedBeacon>,
    onAddBeacon: (ManagedBeacon) -> Unit,
    onDeleteBeacon: (String) -> Unit
) {
    var showAddBeaconDialog by remember { mutableStateOf(false) }
    var beaconToEdit by remember { mutableStateOf<ManagedBeacon?>(null) }

    Column(modifier = Modifier.fillMaxWidth()) {
        // Header
        Text(
            text = "Beacon Management",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        
        // Beacons list
        if (beacons.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No beacons added yet. Add a beacon to start positioning.",
                    color = Color.Gray
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                items(beacons) { beacon ->
                    BeaconListItem(
                        beacon = beacon,
                        onEditClick = { beaconToEdit = beacon },
                        onDeleteClick = { onDeleteBeacon(beacon.id) }
                    )
                }
            }
        }
        
        // Add beacon button
        Button(
            onClick = { showAddBeaconDialog = true },
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            colors = ButtonDefaults.buttonColors(backgroundColor = MaterialTheme.colors.primary)
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Add",
                modifier = Modifier.padding(end = 8.dp)
            )
            Text(text = "Add Beacon")
        }
    }
    
    // Add/Edit beacon dialog
    if (showAddBeaconDialog || beaconToEdit != null) {
        BeaconDialog(
            beacon = beaconToEdit,
            onDismiss = {
                showAddBeaconDialog = false
                beaconToEdit = null
            },
            onSave = { beacon ->
                onAddBeacon(beacon)
                showAddBeaconDialog = false
                beaconToEdit = null
            }
        )
    }
}

@Composable
fun BeaconListItem(
    beacon: ManagedBeacon,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        elevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Beacon indicator
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .background(Color.Red, RoundedCornerShape(6.dp))
            )
            
            // Beacon details
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp)
            ) {
                Text(
                    text = beacon.name.ifEmpty { "Unnamed Beacon" },
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "UUID: ${beacon.uuid}",
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Position: (${beacon.x}, ${beacon.y}, Floor ${beacon.floor})",
                    fontSize = 12.sp
                )
            }
            
            // Action buttons
            Row {
                IconButton(onClick = onEditClick) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit",
                        tint = MaterialTheme.colors.primary
                    )
                }
                
                IconButton(onClick = onDeleteClick) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = Color.Red
                    )
                }
            }
        }
    }
}

@Composable
fun BeaconDialog(
    beacon: ManagedBeacon?,
    onDismiss: () -> Unit,
    onSave: (ManagedBeacon) -> Unit
) {
    val isNewBeacon = beacon == null
    val title = if (isNewBeacon) "Add New Beacon" else "Edit Beacon"
    
    // Form state
    var name by remember { mutableStateOf(beacon?.name ?: "") }
    var uuid by remember { mutableStateOf(beacon?.uuid ?: UUID.randomUUID().toString()) }
    var xText by remember { mutableStateOf(beacon?.x?.toString() ?: "") }
    var yText by remember { mutableStateOf(beacon?.y?.toString() ?: "") }
    var floorText by remember { mutableStateOf(beacon?.floor?.toString() ?: "0") }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            ) {
                // Name field
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Beacon Name") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                )
                
                // UUID field
                OutlinedTextField(
                    value = uuid,
                    onValueChange = { uuid = it },
                    label = { Text("UUID") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    enabled = isNewBeacon // Can only set UUID for new beacons
                )
                
                // X position
                OutlinedTextField(
                    value = xText,
                    onValueChange = { xText = it },
                    label = { Text("X position (meters)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                )
                
                // Y position
                OutlinedTextField(
                    value = yText,
                    onValueChange = { yText = it },
                    label = { Text("Y position (meters)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                )
                
                // Floor number
                OutlinedTextField(
                    value = floorText,
                    onValueChange = { floorText = it },
                    label = { Text("Floor number (0 = ground)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    // Try to parse values, default to 0.0 if invalid
                    val x = xText.toDoubleOrNull() ?: 0.0
                    val y = yText.toDoubleOrNull() ?: 0.0
                    val floor = floorText.toIntOrNull() ?: 0
                    
                    // Create or update beacon
                    val newBeacon = if (isNewBeacon) {
                        ManagedBeacon(
                            id = UUID.randomUUID().toString(),
                            uuid = uuid,
                            name = name,
                            x = x,
                            y = y,
                            floor = floor
                        )
                    } else {
                        beacon!!.copy(
                            name = name,
                            x = x,
                            y = y,
                            floor = floor
                        )
                    }
                    
                    onSave(newBeacon)
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}