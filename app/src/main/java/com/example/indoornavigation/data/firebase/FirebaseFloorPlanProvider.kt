package com.example.indoornavigation.data.firebase

import android.content.Context
import android.net.Uri
import com.example.indoornavigation.R
import com.example.indoornavigation.data.models.FloorPlan
import com.example.indoornavigation.data.models.PointOfInterest
import com.example.indoornavigation.data.models.Position
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Provider for loading floor plans and POIs from Firebase
 * This is a stub implementation that will be expanded in future versions
 */
class FirebaseFloorPlanProvider(private val context: Context) {
    
    // Firestore reference
    private val db = FirebaseFirestore.getInstance()
    
    // Floor plans loaded from Firebase
    private val _floorPlans = MutableStateFlow<List<FloorPlan>>(emptyList())
    val floorPlans: StateFlow<List<FloorPlan>> = _floorPlans.asStateFlow()
    
    // Loading state
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    // Error state
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    
    /**
     * Load floor plans from Firebase
     */
    suspend fun loadFloorPlans() = withContext(Dispatchers.IO) {
        _isLoading.value = true
        _error.value = null
        
        try {
            // Try to fetch from Firestore first
            val fetchedFloors = fetchFloorPlanMetadata()
            
            // If we have fetched floors, use them
            if (fetchedFloors.isNotEmpty()) {
                _floorPlans.value = fetchedFloors
            } else {
                // Otherwise, use local fallback data
                val floors = listOf(
                    createGroundFloor()
                )
                _floorPlans.value = floors
            }
        } catch (e: Exception) {
            _error.value = e.message
            
            // If Firebase fails, use local fallback data
            try {
                val floors = listOf(createGroundFloor())
                _floorPlans.value = floors
                _error.value = "Using local data: ${e.message}"
            } catch (fallbackError: Exception) {
                _error.value = "Failed to load floor plans: ${fallbackError.message}"
            }
        } finally {
            _isLoading.value = false
        }
    }
    
    /**
     * Fetch floor plan metadata from Firestore
     */
    private suspend fun fetchFloorPlanMetadata(): List<FloorPlan> {
        val floorPlans = mutableListOf<FloorPlan>()
        
        try {
            // Fetch floor plans from Firestore
            val floorPlanDocs = db.collection("floor_plans").get().await()
            
            for (document in floorPlanDocs.documents) {
                val id = document.id
                val floorLevel = document.getLong("floorLevel")?.toInt() ?: 0
                val name = document.getString("name") ?: "Unknown Floor"
                val width = document.getDouble("width") ?: 100.0
                val height = document.getDouble("height") ?: 75.0
                
                // For the image, we'd normally fetch from Firebase Storage
                // but for now, we'll use local drawables based on floor level
                val imageResId = when (floorLevel) {
                    0 -> R.drawable.ground_floor
                    1 -> R.drawable.ground_floor // Replace with actual first floor image
                    2 -> R.drawable.ground_floor // Replace with actual second floor image
                    else -> R.drawable.ground_floor
                }
                
                // Fetch POIs for this floor
                val pois = fetchPOIsForFloor(id, floorLevel)
                
                // Create FloorPlan object
                val floorPlan = FloorPlan(
                    id = id,
                    floorLevel = floorLevel,
                    name = name,
                    imageResId = imageResId,
                    width = width,
                    height = height,
                    pois = pois
                )
                
                floorPlans.add(floorPlan)
            }
            
            return floorPlans.sortedBy { it.floorLevel }
        } catch (e: Exception) {
            throw Exception("Failed to fetch floor plans: ${e.message}")
        }
    }
    
    /**
     * Fetch points of interest for a specific floor
     */
    private suspend fun fetchPOIsForFloor(floorId: String, floorLevel: Int): List<PointOfInterest> {
        val pois = mutableListOf<PointOfInterest>()
        
        try {
            // Fetch POIs from Firestore
            val poiDocs = db.collection("floor_plans").document(floorId)
                .collection("pois").get().await()
                
            for (document in poiDocs.documents) {
                val id = document.id
                val name = document.getString("name") ?: "Unknown POI"
                val description = document.getString("description") ?: ""
                val category = document.getString("category") ?: "default"
                
                // Get position
                val positionMap = document.get("position") as? Map<*, *>
                if (positionMap != null) {
                    val x = positionMap["x"] as? Double ?: 0.0
                    val y = positionMap["y"] as? Double ?: 0.0
                    
                    // Create POI object
                    val poi = PointOfInterest(
                        id = id,
                        name = name,
                        description = description,
                        position = Position(x, y, floorLevel),
                        category = category
                    )
                    
                    pois.add(poi)
                }
            }
            
            return pois
        } catch (e: Exception) {
            throw Exception("Failed to fetch POIs: ${e.message}")
        }
    }
    
    /**
     * Create ground floor plan (local fallback)
     */
    private fun createGroundFloor(): FloorPlan {
        // Start with empty POIs - will be populated from configuration mode
        val pois = emptyList<PointOfInterest>()
        
        return FloorPlan(
            id = "ground_floor",
            floorLevel = 0,
            name = "Ground Floor (G)",
            imageResId = R.drawable.ground_floor,
            width = 100.0,
            height = 75.0,
            pois = pois
        )
    }
}
