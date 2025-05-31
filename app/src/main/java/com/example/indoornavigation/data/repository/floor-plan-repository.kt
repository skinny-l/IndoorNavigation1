package com.example.indoornavigation.data.repository

import com.example.indoornavigation.R
import com.example.indoornavigation.data.models.FloorPlan
import com.example.indoornavigation.data.models.PointOfInterest
import com.example.indoornavigation.data.models.Position
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Repository for managing floor plans and POIs
 * Currently uses hardcoded data, but can be extended to use Firebase or a local database
 */
class FloorPlanRepository {
    
    // Available floor plans
    private val _floorPlans = MutableStateFlow<List<FloorPlan>>(emptyList())
    val floorPlans: Flow<List<FloorPlan>> = _floorPlans.asStateFlow()
    
    // Selected floor plan
    private val _currentFloorPlan = MutableStateFlow<FloorPlan?>(null)
    val currentFloorPlan: Flow<FloorPlan?> = _currentFloorPlan.asStateFlow()
    
    // Building dimensions in meters - represent the actual physical dimensions of the building
    // These should match the floor plan images' relative proportions
    private val buildingWidth = 100.0
    private val buildingHeight = 75.0
    
    init {
        loadFloorPlans()
    }
    
    /**
     * Load floor plans - in a real app, this would fetch from Firebase or a local database
     */
    private fun loadFloorPlans() {
        // Create ground floor POIs based on the provided floor plan image
        val groundFloorPois = listOf(
            // Example POIs - these will need to be adjusted to match the actual floor plan
            PointOfInterest(
                id = "main_entrance",
                name = "Main Entrance",
                description = "Building main entrance",
                position = Position(50.0, 10.0, 0),
                category = "entrance"
            ),
            PointOfInterest(
                id = "info_desk",
                name = "Information Desk",
                description = "Help and information",
                position = Position(55.0, 20.0, 0),
                category = "service"
            ),
            PointOfInterest(
                id = "elevator",
                name = "Elevator",
                description = "Main elevator",
                position = Position(60.0, 30.0, 0),
                category = "elevator"
            ),
            PointOfInterest(
                id = "restroom",
                name = "Restrooms",
                description = "Public restrooms",
                position = Position(70.0, 40.0, 0),
                category = "restroom"
            )
        )
        
        // Only include the ground floor for now
        val floorPlans = listOf(
            FloorPlan(
                id = "ground_floor",
                floorLevel = 0,
                name = "Ground Floor",
                imageResId = R.drawable.floor_plan_ground,
                width = buildingWidth,
                height = buildingHeight,
                pois = groundFloorPois
            )
        )
        
        _floorPlans.value = floorPlans
        _currentFloorPlan.value = floorPlans.firstOrNull { it.floorLevel == 0 } // Ground floor only
    }
    
    /**
     * Set the current floor plan by floor level
     */
    fun setCurrentFloor(floorLevel: Int) {
        if (floorLevel != 0) {
            // We only support ground floor at this time
            _currentFloorPlan.value = _floorPlans.value.firstOrNull { it.floorLevel == 0 }
            return
        }
        
        val plan = _floorPlans.value.firstOrNull { it.floorLevel == floorLevel }
        if (plan != null) {
            _currentFloorPlan.value = plan
        }
    }
    
    /**
     * Get the POIs for the current floor
     */
    fun getPoisForCurrentFloor(): List<PointOfInterest> {
        return _currentFloorPlan.value?.pois ?: emptyList()
    }
    
    /**
     * Search for a POI across all floors
     */
    fun searchPoi(query: String): List<PointOfInterest> {
        val allPois = _floorPlans.value.flatMap { it.pois }
        
        return allPois.filter { poi ->
            poi.name.contains(query, ignoreCase = true) ||
            poi.description.contains(query, ignoreCase = true) ||
            poi.category.contains(query, ignoreCase = true)
        }
    }
    
    /**
     * Get a POI by its ID
     */
    fun getPoiById(id: String): PointOfInterest? {
        val allPois = _floorPlans.value.flatMap { it.pois }
        return allPois.firstOrNull { it.id == id }
    }
    
    /**
     * Get floor plan by floor level
     */
    fun getFloorPlanByLevel(floorLevel: Int): FloorPlan? {
        return if (floorLevel == 0) {
            _floorPlans.value.firstOrNull { it.floorLevel == floorLevel }
        } else {
            null
        }
    }
}