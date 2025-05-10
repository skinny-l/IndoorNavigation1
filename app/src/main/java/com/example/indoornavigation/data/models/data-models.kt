package com.example.indoornavigation.data.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Represents a position in 3D space (x, y, floor)
 */
@Parcelize
data class Position(
    val x: Double,
    val y: Double,
    val floor: Int
) : Parcelable

/**
 * Represents a Bluetooth Low Energy beacon
 */
data class Beacon(
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
 * Represents a building with multiple floors
 */
@Parcelize
data class Building(
    val id: String,
    val name: String,
    val floors: List<Floor>,
    val defaultFloor: Int
) : Parcelable