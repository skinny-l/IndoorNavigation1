package com.example.indoornavigation.data.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Represents a Bluetooth Low Energy beacon
 * @deprecated Use the Beacon class from Beacon.kt instead
 * @see com.example.indoornavigation.data.models.Beacon
 */
@Deprecated("Use the Beacon class from Beacon.kt instead", 
    replaceWith = ReplaceWith("com.example.indoornavigation.data.models.Beacon"))
private data class _DeprecatedBeacon(
    val id: String,
    val name: String,
    val rssi: Int,
    val distance: Double,
    val timestamp: Long
)

/**
 * Represents a Point of Interest (POI) in the building
 */
@Parcelize
data class PointOfInterest(
    val id: String,
    val name: String,
    val description: String,
    val position: Position,
    val category: String
) : Parcelable

/**
 * Represents a path between two positions
 */
@Parcelize
data class Path(
    val start: Position,
    val end: Position,
    val waypoints: List<Position> = emptyList()
) : Parcelable

/**
 * Represents a node in the navigation graph
 */
@Parcelize
data class NavNode(
    val id: String,
    val position: Position,
    val connections: MutableList<String> = mutableListOf()
) : Parcelable

/**
 * Represents a floor in the building
 */
@Parcelize
data class Floor(
    val level: Int,
    val name: String,
    val imageUrl: String,
    val width: Double,
    val height: Double
) : Parcelable

/**
 * Represents a FloorPlan with metadata
 */
@Parcelize
data class FloorPlan(
    val id: String,
    val floorLevel: Int,     // 0 = ground floor, 1 = first floor, etc.
    val name: String,        // Display name (e.g., "Ground Floor", "First Floor")
    val imageResId: Int,     // Resource ID for the image (R.drawable.floor_g)
    val width: Double,       // Real-world width in meters
    val height: Double,      // Real-world height in meters
    val pois: List<PointOfInterest> = emptyList(),  // Points of interest on this floor
    val isActive: Boolean = true    // Whether this floor is currently accessible
) : Parcelable

/**
 * Represents a building with multiple floors
 */
@Parcelize
data class Building(
    val id: String,
    val name: String,
    val floors: List<Floor>,
    val defaultFloor: Int
) : Parcelable

/**
 * Represents a Wi-Fi access point
 */
@Parcelize
data class WifiAccessPoint(
    val id: String,          // BSSID
    val ssid: String,        // Network name
    val rssi: Int,           // Signal strength in dBm
    val frequency: Int,      // Frequency in MHz
    val distance: Double,    // Estimated distance in meters
    val timestamp: Long      // Detection timestamp
) : Parcelable