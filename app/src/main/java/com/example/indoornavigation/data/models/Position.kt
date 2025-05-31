package com.example.indoornavigation.data.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Represents a position in the building
 */
@Parcelize
data class Position(
    val x: Double,
    val y: Double,
    val floor: Int
) : Parcelable {
    
    /**
     * Calculate distance to another position (in 2D, same floor)
     */
    fun distanceTo(other: Position): Double {
        if (this.floor != other.floor) {
            return Double.MAX_VALUE
        }
        
        val dx = this.x - other.x
        val dy = this.y - other.y
        
        return Math.sqrt(dx * dx + dy * dy)
    }
    
    /**
     * Check if two positions are on the same floor
     */
    fun isSameFloor(other: Position): Boolean {
        return this.floor == other.floor
    }
    
    /**
     * Create a string representation of the position
     */
    override fun toString(): String {
        return "Position(x=$x, y=$y, floor=$floor)"
    }
}