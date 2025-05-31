package com.example.indoornavigation.ui.map

import com.example.indoornavigation.R
import com.example.indoornavigation.data.models.NavNode
import com.example.indoornavigation.data.models.NavNodeEnhanced
import com.example.indoornavigation.data.navigation.FloorTransition
import com.example.indoornavigation.data.navigation.TransitionDirection

/**
 * Manager for handling floor switching and multi-floor navigation
 */
class FloorSwitchManager(
    private val floorPlanView: FloorPlanView,
    private val floorSelectorView: FloorSelectorView
) {
    private var currentFloor = 0
    private var currentPath: List<NavNodeEnhanced> = emptyList()
    private var floorPlans = mapOf<Int, Int>() // Map of floorId to resource ID
    
    init {
        // Default floor plans - should be customized for real application
        floorPlans = mapOf(
            0 to R.drawable.ground_floor,
            1 to R.drawable.ground_floor,  // Replace with actual first floor
            2 to R.drawable.ground_floor   // Replace with actual second floor
        )
    }
    
    /**
     * Set available floors for the building
     * @param floors List of floor IDs
     * @param defaultFloor Initial floor to display
     */
    fun setAvailableFloors(floors: List<Int>, defaultFloor: Int = 0) {
        // Setup floor selector
        floorSelectorView.setupFloors(floors)
        
        // Set listener for floor selection
        floorSelectorView.setOnFloorSelectedListener { floorId ->
            switchFloor(floorId)
        }
        
        // Switch to default floor
        switchFloor(defaultFloor)
    }
    
    /**
     * Set custom floor plans
     * @param floorResourceMap Map of floor IDs to drawable resource IDs
     */
    fun setFloorPlans(floorResourceMap: Map<Int, Int>) {
        floorPlans = floorResourceMap
        
        // Update current view if needed
        if (floorPlans.containsKey(currentFloor)) {
            floorPlanView.setFloorPlan(currentFloor)
        }
    }
    
    /**
     * Set the current navigation path
     * @param path List of navigation nodes representing the path
     */
    fun setNavigationPath(path: List<NavNodeEnhanced>) {
        currentPath = path
        
        // Get all floors in the path
        val floorsInPath = path.map { it.floorId }.distinct().sorted()
        
        // Update floor selector with these floors
        if (floorsInPath.isNotEmpty()) {
            floorSelectorView.setupFloors(floorsInPath)
        }
        
        // Update visualization on current floor
        updateFloorVisualization()
    }
    
    /**
     * Switch to a different floor
     * @param floorId ID of the floor to switch to
     */
    fun switchFloor(floorId: Int) {
        currentFloor = floorId
        
        // Load correct floor plan for this floor
        val resourceId = floorPlans[floorId] ?: R.drawable.ground_floor
        floorPlanView.setFloorPlan(floorId)
        
        // Update visualization
        updateFloorVisualization()
        
        // Update floor selector UI
        floorSelectorView.setSelectedFloor(floorId)
    }
    
    /**
     * Update the visualization for the current floor
     */
    private fun updateFloorVisualization() {
        // Filter path nodes for current floor
        val currentFloorPath = currentPath.filter { it.floorId == currentFloor }
        
        // Create standard NavNode list for compatibility with FloorPlanView
        val standardNodes = currentFloorPath.map { enhancedNode ->
            NavNode(
                id = enhancedNode.id,
                position = enhancedNode.position,
                connections = enhancedNode.connections.map { it.targetNodeId }.toMutableList()
            )
        }
        
        // Find transition points (stairs/elevators)
        val floorTransitions = findFloorTransitionPoints(currentPath, currentFloor)
        
        // Create position markers for transitions
        val transitionMarkers = floorTransitions.map { transition ->
            // Select icon based on transition direction
            val iconResId = when (transition.direction) {
                TransitionDirection.ENTRY -> R.drawable.ic_location_marker // Should be entrance icon
                TransitionDirection.EXIT -> R.drawable.ic_location_marker   // Should be exit icon
            }
            
            // Create marker
            FloorPlanView.Marker(
                position = transition.position,
                bitmap = getBitmapFromResource(iconResId),
                title = "Floor ${transition.connectedFloorId}",
                floor = currentFloor
            )
        }
        
        // Update view with current floor path and transitions
        floorPlanView.setNavigationNodes(standardNodes)
        
        // Clear and add new markers
        floorPlanView.clearMarkers()
        transitionMarkers.forEach { floorPlanView.addMarker(it) }
        
        // Convert path nodes to positions for route display
        val pathPositions = currentFloorPath.map { it.position }
        if (pathPositions.isNotEmpty()) {
            floorPlanView.setRoute(pathPositions)
        } else {
            floorPlanView.clearRoute()
        }
    }
    
    /**
     * Find floor transition points in the path
     */
    private fun findFloorTransitionPoints(
        path: List<NavNodeEnhanced>, 
        floorId: Int
    ): List<FloorTransition> {
        val transitions = mutableListOf<FloorTransition>()
        
        // Find nodes where floor changes
        for (i in 0 until path.size - 1) {
            val current = path[i]
            val next = path[i+1]
            
            if (current.floorId == floorId && next.floorId != floorId) {
                // Exit from current floor
                transitions.add(
                    FloorTransition(
                        position = current.position,
                        connectedFloorId = next.floorId,
                        direction = TransitionDirection.EXIT
                    )
                )
            } else if (current.floorId != floorId && next.floorId == floorId) {
                // Entry to current floor
                transitions.add(
                    FloorTransition(
                        position = next.position,
                        connectedFloorId = current.floorId,
                        direction = TransitionDirection.ENTRY
                    )
                )
            }
        }
        
        return transitions
    }
    
    /**
     * Get a bitmap from a drawable resource ID
     */
    private fun getBitmapFromResource(resourceId: Int): android.graphics.Bitmap? {
        val drawable = androidx.core.content.ContextCompat.getDrawable(
            floorPlanView.context, 
            resourceId
        )
        
        if (drawable is android.graphics.drawable.BitmapDrawable) {
            return drawable.bitmap
        } else if (drawable != null) {
            val bitmap = android.graphics.Bitmap.createBitmap(
                drawable.intrinsicWidth,
                drawable.intrinsicHeight,
                android.graphics.Bitmap.Config.ARGB_8888
            )
            val canvas = android.graphics.Canvas(bitmap)
            drawable.setBounds(0, 0, canvas.width, canvas.height)
            drawable.draw(canvas)
            return bitmap
        }
        
        return null
    }
}