package com.example.indoornavigation.data.repositories

import android.content.Context
import com.example.indoornavigation.data.models.POI
import com.example.indoornavigation.data.models.Position
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Repository for managing Points of Interest (POIs)
 */
class POIRepository(private val context: Context) {
    
    // In-memory cache of POIs
    private val pois = mutableMapOf<String, POI>()
    
    init {
        // Initialize with some sample POIs
        loadSamplePOIs()
    }
    
    /**
     * Get a POI by ID
     */
    suspend fun getPOIById(id: String): POI? = withContext(Dispatchers.IO) {
        pois[id]
    }
    
    /**
     * Get all POIs
     */
    suspend fun getAllPOIs(): List<POI> = withContext(Dispatchers.IO) {
        pois.values.toList()
    }
    
    /**
     * Get POIs on a specific floor
     */
    suspend fun getPOIsForFloor(floor: Int): List<POI> = withContext(Dispatchers.IO) {
        pois.values.filter { it.position.floor == floor }
    }
    
    /**
     * Get POIs of a specific type
     */
    suspend fun getPOIsOfType(type: POI.POIType): List<POI> = withContext(Dispatchers.IO) {
        pois.values.filter { it.type == type }
    }
    
    /**
     * Add or update a POI
     */
    suspend fun savePOI(poi: POI): Boolean = withContext(Dispatchers.IO) {
        try {
            pois[poi.id] = poi
            true
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Delete a POI
     */
    suspend fun deletePOI(id: String): Boolean = withContext(Dispatchers.IO) {
        try {
            pois.remove(id)
            true
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Find nearest POI to a position
     */
    suspend fun findNearestPOI(position: Position, maxDistance: Double = Double.MAX_VALUE): POI? = withContext(Dispatchers.IO) {
        pois.values
            .filter { it.position.floor == position.floor }
            .minByOrNull { it.position.distanceTo(position) }
            ?.takeIf { it.position.distanceTo(position) <= maxDistance }
    }
    
    /**
     * Find POIs within a certain distance
     */
    suspend fun findPOIsWithinDistance(position: Position, distance: Double): List<POI> = withContext(Dispatchers.IO) {
        pois.values
            .filter { 
                it.position.floor == position.floor && 
                it.position.distanceTo(position) <= distance 
            }
    }
    
    /**
     * Load sample POIs for demonstration
     */
    private fun loadSamplePOIs() {
        val samplePOIs = listOf(
            POI(
                id = "lobby",
                name = "Lobby",
                description = "Main entrance lobby",
                position = Position(5.0, 5.0, 0),
                type = POI.POIType.ENTRANCE
            ),
            POI(
                id = "cafeteria",
                name = "Cafeteria",
                description = "Main cafeteria",
                position = Position(30.0, 15.0, 0),
                type = POI.POIType.FOOD
            ),
            POI(
                id = "conference1",
                name = "Conference Room 1",
                description = "Small conference room",
                position = Position(20.0, 25.0, 0),
                type = POI.POIType.ROOM
            ),
            POI(
                id = "elevator1",
                name = "Main Elevator",
                description = "Main elevator",
                position = Position(10.0, 20.0, 0),
                type = POI.POIType.ELEVATOR
            ),
            POI(
                id = "restroom1",
                name = "Restroom Floor 1",
                description = "Men's and women's restrooms",
                position = Position(15.0, 30.0, 0),
                type = POI.POIType.RESTROOM
            ),
            POI(
                id = "office101",
                name = "Office 101",
                description = "Executive office",
                position = Position(25.0, 15.0, 1),
                type = POI.POIType.ROOM
            )
        )
        
        // Add samples to the map
        for (poi in samplePOIs) {
            pois[poi.id] = poi
        }
    }
}