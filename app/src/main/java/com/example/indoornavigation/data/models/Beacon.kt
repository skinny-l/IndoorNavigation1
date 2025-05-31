package com.example.indoornavigation.data.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Model representing a detected Bluetooth beacon
 */
@Parcelize
data class Beacon(
    val id: String,
    val name: String = "Unknown",
    val uuid: String = id,
    val rssi: Int = -100,
    val distance: Double = 0.0,
    val timestamp: Long = 0,
    val position: Position? = null,
    val signalStrength: String = "weak"  // Can be "strong", "medium", or "weak"
) : Parcelable