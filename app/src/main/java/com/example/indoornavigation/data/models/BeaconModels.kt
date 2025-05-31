package com.example.indoornavigation.data.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Model representing a beacon managed by the user
 */
@Parcelize
data class ManagedBeacon(
    val id: String,
    val uuid: String,
    val x: Double,
    val y: Double,
    val floor: Int,
    val name: String = "",
    val lastRssi: Int = 0,
    val lastDistance: Double = 0.0,
    val lastSeen: Long = 0
) : Parcelable

/**
 * Status of the trilateration positioning system
 */
data class TrilaterationStatus(
    val position: Position? = null,
    val accuracy: Double = 0.0,
    val activeBeacons: Int = 0,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Result of a trilateration calculation
 */
data class TrilaterationResult(
    val position: Position,
    val accuracy: Double,
    val usedBeacons: List<ManagedBeacon>,
    val timestamp: Long = System.currentTimeMillis()
)