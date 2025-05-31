package com.example.indoornavigation.data.models

/**
 * Represents a WiFi fingerprint for indoor positioning
 * Contains signal strength mappings for a specific location
 */
data class WiFiFingerprint(
    val locationId: String,
    val signalMap: Map<String, Int>, // BSSID to RSSI mapping
    val timestamp: Long
)