package com.example.indoornavigation.data.models

/**
 * Data class representing a location fingerprint with RSSI signatures
 */
data class Fingerprint(
    val id: String,
    val position: Position,
    val bleSignatures: Map<String, Int>,  // Map of beacon ID to RSSI
    val wifiSignatures: Map<String, Int>, // Map of BSSID to RSSI
    val timestamp: Long
)