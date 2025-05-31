package com.example.indoornavigation.data.models

/**
 * Data class representing a building entrance with detailed information
 */
data class BuildingEntrance(
    val id: String,
    val name: String,
    val description: String,
    val position: Position,
    val isAccessible: Boolean,
    val hasElevator: Boolean,
    val isOpen24Hours: Boolean,
    val openingHours: String,
    val features: List<String>
)