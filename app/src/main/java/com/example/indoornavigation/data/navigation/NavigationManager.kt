package com.example.indoornavigation.data.navigation

import android.content.Context
import com.example.indoornavigation.data.models.NavigationInstruction
import com.example.indoornavigation.data.models.Path
import com.example.indoornavigation.data.models.Position
import com.example.indoornavigation.utils.PathfindingEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.lang.Math

/**
 * Manages the navigation state and instructions
 */
class NavigationManager(private val context: Context) {

    // Current instruction
    private val _currentInstruction = MutableStateFlow<NavigationInstruction?>(null)
    val currentInstruction: StateFlow<NavigationInstruction?> = _currentInstruction.asStateFlow()
    
    // Navigation progress
    private val _navigationProgress = MutableStateFlow(0)
    val navigationProgress: StateFlow<Int> = _navigationProgress.asStateFlow()
    
    // Reached destination
    private val _reachedDestination = MutableStateFlow(false)
    val reachedDestination: StateFlow<Boolean> = _reachedDestination.asStateFlow()
    
    // Current navigation path
    private var currentPath: Path? = null
    
    // Navigation graph manager
    private val navGraphManager = NavGraphManager()
    
    // Pathfinding engine
    private lateinit var pathfindingEngine: PathfindingEngine
    
    // Last known position
    private var lastPosition: Position? = null
    
    // Next waypoint index
    private var currentWaypointIndex = 0
    
    // Whether navigation is active
    private var navigating = false
    
    // Navigation settings
    private var voiceGuidanceEnabled = true
    private var hapticFeedbackEnabled = true
    private var accessibleRoutesEnabled = false
    private var distanceUnit = DistanceUnit.METERS
    
    // Navigation path options
    private val _availablePaths = MutableStateFlow<List<Path>>(emptyList())
    val availablePaths: StateFlow<List<Path>> = _availablePaths.asStateFlow()
    
    // Current waypoint flow
    private val _currentWaypoint = MutableStateFlow<Position?>(null)
    val currentWaypoint: StateFlow<Position?> = _currentWaypoint.asStateFlow()
    
    // Destination flow
    private val _destination = MutableStateFlow<Position?>(null)
    val destination: StateFlow<Position?> = _destination.asStateFlow()

    init {
        // Initialize pathfinding engine with empty node map
        pathfindingEngine = PathfindingEngine(emptyMap())
    }

    /**
     * Load a navigation graph from JSON
     * @return true if successful
     */
    fun loadNavigationGraph(jsonString: String): Boolean {
        val success = navGraphManager.importFromJson(jsonString)
        if (success) {
            // Update pathfinding engine with new graph
            pathfindingEngine = PathfindingEngine(navGraphManager.getAllNodes())
        }
        return success
    }
    
    /**
     * Create a test grid navigation graph
     * Useful for testing navigation without a real map
     */
    fun createTestNavigationGraph(rows: Int, cols: Int, floor: Int, width: Double, height: Double) {
        navGraphManager.createGridGraph(rows, cols, floor, width, height)
        pathfindingEngine = PathfindingEngine(navGraphManager.getAllNodes())
    }

    /**
     * Calculate a path between two positions
     * @return The calculated path or null if no path is possible
     */
    fun calculatePath(start: Position, end: Position): Path? {
        return pathfindingEngine.findPath(start, end)
    }
    
    /**
     * Calculate multiple path options between two positions
     * For example, shortest path, wheelchair accessible path, etc.
     */
    fun calculatePathOptions(start: Position, end: Position) {
        val paths = mutableListOf<Path>()
        
        // Add default path
        pathfindingEngine.findPath(start, end)?.let { paths.add(it) }
        
        // Update available paths
        _availablePaths.value = paths
    }

    /**
     * Start navigation along a path
     */
    fun startNavigation(path: Path) {
        currentPath = path
        navigating = true
        _reachedDestination.value = false
        _navigationProgress.value = 0
        currentWaypointIndex = 0
        _destination.value = path.end
        
        // Set first waypoint
        updateCurrentWaypoint()
        
        // Set initial instruction
        _currentInstruction.value = NavigationInstruction(
            type = NavigationInstruction.InstructionType.START,
            distanceMeters = 0f,
            direction = NavigationInstruction.Direction.FORWARD,
            text = "Start navigation",
            nodeId = "start"
        )
    }
    
    /**
     * Start navigation to a destination
     * @param start Starting position (if null, uses last known position)
     * @param end Destination position
     * @return true if navigation started successfully
     */
    fun navigateTo(start: Position?, end: Position): Boolean {
        // Use provided start or last known position
        val startPos = start ?: lastPosition ?: return false
        
        // Calculate path
        val path = calculatePath(startPos, end) ?: return false
        
        // Start navigation
        startNavigation(path)
        return true
    }
    
    /**
     * Update the current waypoint based on progress
     */
    private fun updateCurrentWaypoint() {
        val path = currentPath ?: return
        
        if (path.waypoints.isNotEmpty() && currentWaypointIndex < path.waypoints.size) {
            _currentWaypoint.value = path.waypoints[currentWaypointIndex]
        }
    }
    
    /**
     * Stop navigation
     */
    fun stopNavigation() {
        navigating = false
        currentPath = null
        _currentInstruction.value = null
        _navigationProgress.value = 0
        _reachedDestination.value = false
        _currentWaypoint.value = null
        _destination.value = null
        currentWaypointIndex = 0
    }
    
    /**
     * Update the current position during navigation
     */
    fun updatePosition(position: Position) {
        // Update last known position
        lastPosition = position
        
        if (!navigating || currentPath == null) return
        
        currentPath?.let { path ->
            // Check proximity to current waypoint
            val currentWaypoint = _currentWaypoint.value
            if (currentWaypoint != null) {
                val distanceToWaypoint = calculateDistance(position, currentWaypoint)
                
                // If within 3 meters of waypoint, advance to next waypoint
                if (distanceToWaypoint < 3.0) {
                    advanceToNextWaypoint()
                }
            }
            
            // Simple progress calculation based on distance to end
            val distanceToEnd = calculateDistance(position, path.end)
            val totalDistance = calculatePathLength(path)
            
            if (totalDistance > 0) {
                val progress = ((1 - (distanceToEnd / totalDistance)) * 100).toInt()
                    .coerceIn(0, 100)
                _navigationProgress.value = progress
                
                // Check if we've reached the destination (within 2 meters)
                if (distanceToEnd < 2.0) {
                    _reachedDestination.value = true
                    _currentInstruction.value = NavigationInstruction(
                        type = NavigationInstruction.InstructionType.DESTINATION,
                        distanceMeters = 0f,
                        direction = NavigationInstruction.Direction.FORWARD,
                        text = "You have reached your destination",
                        nodeId = "destination"
                    )
                }
                // Otherwise, update instructions based on current position and waypoint
                else {
                    updateNavigationInstructions(position)
                }
            }
        }
    }
    
    /**
     * Advance to the next waypoint and update instructions
     */
    private fun advanceToNextWaypoint() {
        currentWaypointIndex++
        updateCurrentWaypoint()
        
        // If we've reached the end of waypoints, currentWaypoint will be null
        // This is handled in updateNavigationInstructions
    }
    
    /**
     * Update navigation instructions based on user position and next waypoint
     */
    private fun updateNavigationInstructions(position: Position) {
        val waypoint = _currentWaypoint.value ?: return
        val path = currentPath ?: return
        
        // Calculate distance to waypoint
        val distance = calculateDistance(position, waypoint)
        
        // Get next waypoint if available, for direction calculation
        val nextWaypoint = if (currentWaypointIndex < path.waypoints.size - 1) {
            path.waypoints[currentWaypointIndex + 1]
        } else null
        
        // Calculate direction
        val direction = calculateDirection(position, waypoint, nextWaypoint)
        
        // Generate instruction text
        val instructionText = generateInstructionText(distance, direction, waypoint, nextWaypoint)
        
        // Create and emit instruction
        val instruction = NavigationInstruction(
            type = NavigationInstruction.InstructionType.TURN,
            distanceMeters = distance.toFloat(),
            direction = direction,
            text = instructionText,
            nodeId = "waypoint_${currentWaypointIndex}"
        )
        
        _currentInstruction.value = instruction
    }
    
    /**
     * Calculate direction from current position to waypoint
     */
    private fun calculateDirection(
        position: Position, 
        waypoint: Position,
        nextWaypoint: Position?
    ): NavigationInstruction.Direction {
        // Vector from current position to waypoint
        val dx = waypoint.x - position.x
        val dy = waypoint.y - position.y
        
        // For now, simplify to 8 compass directions
        val angle = Math.toDegrees(Math.atan2(dy, dx)).toFloat()
        
        // Convert angle to direction
        return when {
            angle >= -22.5 && angle < 22.5 -> NavigationInstruction.Direction.RIGHT
            angle >= 22.5 && angle < 67.5 -> NavigationInstruction.Direction.SLIGHT_RIGHT
            angle >= 67.5 && angle < 112.5 -> NavigationInstruction.Direction.FORWARD
            angle >= 112.5 && angle < 157.5 -> NavigationInstruction.Direction.SLIGHT_LEFT
            angle >= 157.5 || angle < -157.5 -> NavigationInstruction.Direction.LEFT
            angle >= -157.5 && angle < -112.5 -> NavigationInstruction.Direction.SLIGHT_LEFT
            angle >= -112.5 && angle < -67.5 -> NavigationInstruction.Direction.TURN_AROUND
            else -> NavigationInstruction.Direction.SLIGHT_RIGHT
        }
    }
    
    /**
     * Generate instruction text based on direction and distance
     */
    private fun generateInstructionText(
        distance: Double,
        direction: NavigationInstruction.Direction,
        waypoint: Position,
        nextWaypoint: Position?
    ): String {
        val formattedDistance = formatDistance(distance)
        
        // If floor change is happening
        if (nextWaypoint != null && waypoint.floor != nextWaypoint.floor) {
            val action = if (waypoint.floor < nextWaypoint.floor) "go up" else "go down"
            val floorChange = Math.abs(nextWaypoint.floor - waypoint.floor)
            val floors = if (floorChange == 1) "floor" else "floors"
            
            return "In $formattedDistance, $action $floorChange $floors"
        }
        
        // Normal directional instruction
        val directionText = when (direction) {
            NavigationInstruction.Direction.FORWARD -> "Continue straight"
            NavigationInstruction.Direction.SLIGHT_RIGHT -> "Turn slight right" 
            NavigationInstruction.Direction.RIGHT -> "Turn right"
            NavigationInstruction.Direction.TURN_AROUND -> "Turn around"
            NavigationInstruction.Direction.LEFT -> "Turn left"
            NavigationInstruction.Direction.SLIGHT_LEFT -> "Turn slight left"
            NavigationInstruction.Direction.UP -> "Go up"
            NavigationInstruction.Direction.DOWN -> "Go down"
        }
        
        return "$directionText for $formattedDistance"
    }
    
    /**
     * Format distance based on unit preference
     */
    private fun formatDistance(distance: Double): String {
        return when (distanceUnit) {
            DistanceUnit.METERS -> {
                if (distance < 10) {
                    "${Math.round(distance)} meters"
                } else {
                    "${Math.round(distance / 10) * 10} meters"
                }
            }
            DistanceUnit.FEET -> {
                val feet = distance * 3.28084
                if (feet < 30) {
                    "${Math.round(feet)} feet"
                } else {
                    "${Math.round(feet / 10) * 10} feet"
                }
            }
        }
    }
    
    /**
     * Calculate total path length
     */
    private fun calculatePathLength(path: Path): Double {
        var totalLength = 0.0
        val waypoints = path.waypoints
        
        for (i in 0 until waypoints.size - 1) {
            totalLength += calculateDistance(waypoints[i], waypoints[i + 1])
        }
        
        return totalLength
    }
    
    /**
     * Calculate distance between two positions
     */
    private fun calculateDistance(p1: Position, p2: Position): Double {
        // If on different floors, add floor penalty
        val floorPenalty = if (p1.floor != p2.floor) {
            Math.abs(p1.floor - p2.floor) * 4.0
        } else {
            0.0
        }
        
        // Calculate 2D distance
        val dx = p2.x - p1.x
        val dy = p2.y - p1.y
        val distance = Math.sqrt(dx * dx + dy * dy)
        
        return distance + floorPenalty
    }
    
    /**
     * Check if navigation is active
     */
    fun isNavigating(): Boolean {
        return navigating
    }
    
    /**
     * Check if voice guidance is enabled
     */
    fun isVoiceGuidanceEnabled(): Boolean {
        return voiceGuidanceEnabled
    }
    
    /**
     * Set voice guidance enabled/disabled
     */
    fun setVoiceGuidance(enabled: Boolean) {
        voiceGuidanceEnabled = enabled
    }
    
    /**
     * Check if haptic feedback is enabled
     */
    fun isHapticFeedbackEnabled(): Boolean {
        return hapticFeedbackEnabled
    }
    
    /**
     * Set haptic feedback enabled/disabled
     */
    fun setHapticFeedback(enabled: Boolean) {
        hapticFeedbackEnabled = enabled
    }
    
    /**
     * Check if accessible routes are enabled
     */
    fun isAccessibleRoutesEnabled(): Boolean {
        return accessibleRoutesEnabled
    }
    
    /**
     * Set accessible routes enabled/disabled
     */
    fun setAccessibleRoutes(enabled: Boolean) {
        accessibleRoutesEnabled = enabled
    }
    
    /**
     * Set distance unit preference
     */
    fun setDistanceUnit(unit: DistanceUnit) {
        distanceUnit = unit
    }
    
    /**
     * Get the navigation graph manager
     */
    fun getNavGraphManager(): NavGraphManager {
        return navGraphManager
    }
    
    /**
     * Clean up resources when no longer needed
     */
    fun shutdown() {
        stopNavigation()
    }
    
    /**
     * Distance units for navigation
     */
    enum class DistanceUnit {
        METERS,
        FEET
    }
}