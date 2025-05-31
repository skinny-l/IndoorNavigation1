package com.example.indoornavigation.data.navigation

import com.example.indoornavigation.data.models.Position

/**
 * Direction of a floor transition
 */
enum class TransitionDirection {
    ENTRY,  // Entering this floor from another floor
    EXIT    // Exiting this floor to another floor
}

/**
 * Represents a transition point between floors in navigation
 */
data class FloorTransition(
    val position: Position,       // Position of the transition point
    val connectedFloorId: Int,    // ID of the connected floor
    val direction: TransitionDirection // Direction of the transition
)