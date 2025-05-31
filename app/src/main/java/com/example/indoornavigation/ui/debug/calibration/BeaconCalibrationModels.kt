package com.example.indoornavigation.ui.debug.calibration

data class BeaconDevice(
    val id: String,           // MAC address or UUID
    val name: String,         // User-friendly name
    var rssi: Int,            // Current RSSI value
    var isConnected: Boolean = false
)

data class RssiMeasurement(
    val distance: Double,
    val rssi: Int
)