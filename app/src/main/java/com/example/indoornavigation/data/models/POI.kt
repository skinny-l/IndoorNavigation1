package com.example.indoornavigation.data.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Represents a Point of Interest (POI) in the building
 */
@Parcelize
data class POI(
    val id: String,
    val name: String,
    val description: String,
    val position: Position,
    val type: POIType,
    val imageUrl: String? = null,
    val metadata: Map<String, String> = emptyMap()
) : Parcelable {
    
    /**
     * Types of POIs
     */
    enum class POIType {
        ROOM,       // Regular rooms
        RESTROOM,   // Restrooms
        ELEVATOR,   // Elevators
        STAIRWELL,  // Stairwells
        EXIT,       // Emergency exits
        ENTRANCE,   // Building entrances
        FOOD,       // Food areas, cafeterias
        INFO,       // Information points
        OFFICE,     // Offices
        CLASSROOM,  // Classrooms
        CUSTOM      // Custom POI type
    }
    
    /**
     * Check if the POI is accessible
     */
    fun isAccessible(): Boolean {
        return metadata["accessible"] == "true"
    }
    
    /**
     * Get floor name (if available in metadata)
     */
    fun getFloorName(): String {
        return metadata["floor_name"] ?: "Floor ${position.floor}"
    }
    
    /**
     * Get opening hours (if available in metadata)
     */
    fun getOpeningHours(): String? {
        return metadata["opening_hours"]
    }
    
    /**
     * Get contact information (if available in metadata)
     */
    fun getContactInfo(): String? {
        return metadata["contact"]
    }
}