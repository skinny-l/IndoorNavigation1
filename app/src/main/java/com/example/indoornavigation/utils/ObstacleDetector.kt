package com.example.indoornavigation.utils

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.example.indoornavigation.data.models.Position
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Detects furniture, temporary obstacles, and dynamic blockages
 * using camera, sensors, and crowdsourced data
 */
class ObstacleDetector(private val context: Context) {
    
    data class Obstacle(
        val id: String,
        val position: Position,
        val type: ObstacleType,
        val size: ObstacleSize,
        val confidence: Float,
        val lastUpdated: Long,
        val isTemporary: Boolean = false
    )
    
    enum class ObstacleType {
        FURNITURE_TABLE, FURNITURE_CHAIR, FURNITURE_DESK,
        TEMPORARY_BARRIER, CONSTRUCTION, CLEANING_EQUIPMENT,
        CROWD, CLOSED_DOOR, MAINTENANCE_WORK,
        OTHER
    }
    
    data class ObstacleSize(
        val width: Double,
        val height: Double,
        val depth: Double
    )
    
    private val _detectedObstacles = MutableStateFlow<List<Obstacle>>(emptyList())
    val detectedObstacles: StateFlow<List<Obstacle>> = _detectedObstacles.asStateFlow()
    
    private val crowdsourcedObstacles = mutableListOf<Obstacle>()
    
    /**
     * Scan for obstacles using multiple detection methods
     */
    suspend fun scanForObstacles(currentPosition: Position): List<Obstacle> {
        val obstacles = mutableListOf<Obstacle>()
        
        try {
            // Method 1: Camera-based detection
            obstacles.addAll(detectVisualObstacles(currentPosition))
            
            // Method 2: Proximity sensor detection
            obstacles.addAll(detectProximityObstacles(currentPosition))
            
            // Method 3: Load crowdsourced obstacles
            obstacles.addAll(getCrowdsourcedObstacles(currentPosition))
            
            // Method 4: AI-based pattern recognition
            obstacles.addAll(detectPatternObstacles(currentPosition))
            
            // Update obstacle list
            _detectedObstacles.value = obstacles
            
            Log.d("ObstacleDetector", "Detected ${obstacles.size} obstacles")
            
        } catch (e: Exception) {
            Log.e("ObstacleDetector", "Error detecting obstacles: ${e.message}")
        }
        
        return obstacles
    }
    
    /**
     * Detect obstacles using camera image analysis
     */
    private fun detectVisualObstacles(position: Position): List<Obstacle> {
        val obstacles = mutableListOf<Obstacle>()
        
        // Simulated camera-based detection
        // In real implementation, this would use:
        // - Camera2 API or CameraX
        // - TensorFlow Lite for object detection
        // - OpenCV for image processing
        
        // Example: Detect common furniture patterns
        if (isNearOfficeArea(position)) {
            obstacles.add(Obstacle(
                id = "desk_${System.currentTimeMillis()}",
                position = Position(position.x + 2.0, position.y + 1.0, position.floor),
                type = ObstacleType.FURNITURE_DESK,
                size = ObstacleSize(1.5, 0.8, 0.7),
                confidence = 0.8f,
                lastUpdated = System.currentTimeMillis()
            ))
        }
        
        if (isNearCommonArea(position)) {
            obstacles.add(Obstacle(
                id = "table_${System.currentTimeMillis()}",
                position = Position(position.x + 1.0, position.y + 2.0, position.floor),
                type = ObstacleType.FURNITURE_TABLE,
                size = ObstacleSize(2.0, 1.0, 0.8),
                confidence = 0.7f,
                lastUpdated = System.currentTimeMillis()
            ))
        }
        
        return obstacles
    }
    
    /**
     * Detect obstacles using proximity sensors (if available)
     */
    private fun detectProximityObstacles(position: Position): List<Obstacle> {
        val obstacles = mutableListOf<Obstacle>()
        
        // Simulated proximity detection
        // In real implementation, this would use:
        // - Proximity sensors
        // - Ultrasonic sensors (if available)
        // - LiDAR (on supported devices)
        // - Bluetooth proximity detection
        
        // Example: Detect unexpected proximity signals
        val proximityValue = getSimulatedProximity()
        if (proximityValue < 1.0) { // Very close obstacle
            obstacles.add(Obstacle(
                id = "proximity_${System.currentTimeMillis()}",
                position = Position(position.x + 0.5, position.y, position.floor),
                type = ObstacleType.OTHER,
                size = ObstacleSize(0.5, 0.5, 0.5),
                confidence = 0.6f,
                lastUpdated = System.currentTimeMillis(),
                isTemporary = true
            ))
        }
        
        return obstacles
    }
    
    /**
     * Get crowdsourced obstacle data from other users
     */
    private fun getCrowdsourcedObstacles(position: Position): List<Obstacle> {
        val nearbyObstacles = mutableListOf<Obstacle>()
        
        // Filter crowdsourced obstacles by proximity and freshness
        crowdsourcedObstacles.forEach { obstacle ->
            val distance = obstacle.position.distanceTo(position)
            val age = System.currentTimeMillis() - obstacle.lastUpdated
            
            // Include if within 10 meters and less than 1 hour old
            if (distance <= 10.0 && age < 3600000) {
                nearbyObstacles.add(obstacle)
            }
        }
        
        return nearbyObstacles
    }
    
    /**
     * Detect obstacles using AI pattern recognition
     */
    private fun detectPatternObstacles(position: Position): List<Obstacle> {
        val obstacles = mutableListOf<Obstacle>()
        
        // Simulated AI detection based on patterns
        // In real implementation, this would use:
        // - Machine learning models
        // - Behavioral pattern analysis
        // - Historical obstacle data
        // - Time-based predictions
        
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        
        // Predict obstacles based on time patterns
        if (hour in 12..13) { // Lunch time
            if (isNearCafeteria(position)) {
                obstacles.add(Obstacle(
                    id = "crowd_lunch_${System.currentTimeMillis()}",
                    position = Position(position.x, position.y + 3.0, position.floor),
                    type = ObstacleType.CROWD,
                    size = ObstacleSize(5.0, 3.0, 0.0),
                    confidence = 0.9f,
                    lastUpdated = System.currentTimeMillis(),
                    isTemporary = true
                ))
            }
        }
        
        if (hour in 8..9 || hour in 17..18) { // Rush hours
            if (isNearElevator(position)) {
                obstacles.add(Obstacle(
                    id = "crowd_elevator_${System.currentTimeMillis()}",
                    position = Position(position.x + 1.0, position.y, position.floor),
                    type = ObstacleType.CROWD,
                    size = ObstacleSize(3.0, 2.0, 0.0),
                    confidence = 0.8f,
                    lastUpdated = System.currentTimeMillis(),
                    isTemporary = true
                ))
            }
        }
        
        return obstacles
    }
    
    /**
     * Report an obstacle (crowdsourcing)
     */
    fun reportObstacle(obstacle: Obstacle) {
        crowdsourcedObstacles.add(obstacle)
        
        // In real implementation, this would:
        // - Upload to server/Firebase
        // - Notify nearby users
        // - Update navigation recommendations
        
        Log.d("ObstacleDetector", "Obstacle reported: ${obstacle.type} at ${obstacle.position}")
    }
    
    /**
     * Check if a path is clear of obstacles
     */
    fun isPathClear(start: Position, end: Position): Boolean {
        val currentObstacles = _detectedObstacles.value
        
        for (obstacle in currentObstacles) {
            if (pathIntersectsObstacle(start, end, obstacle)) {
                return false
            }
        }
        
        return true
    }
    
    /**
     * Get alternative path avoiding obstacles
     */
    fun getObstacleAvoidancePath(start: Position, end: Position): List<Position> {
        val obstacles = _detectedObstacles.value
        val path = mutableListOf<Position>()
        
        // Simple obstacle avoidance - create waypoints around obstacles
        var currentPos = start
        
        while (currentPos.distanceTo(end) > 1.0) {
            val directPath = getDirectPath(currentPos, end)
            val blockedBy = obstacles.find { pathIntersectsObstacle(currentPos, end, it) }
            
            if (blockedBy != null) {
                // Create waypoint around obstacle
                val avoidancePoint = createAvoidanceWaypoint(currentPos, end, blockedBy)
                path.add(avoidancePoint)
                currentPos = avoidancePoint
            } else {
                // Direct path is clear
                path.add(end)
                break
            }
        }
        
        return path
    }
    
    // Helper methods
    private fun isNearOfficeArea(position: Position): Boolean {
        return position.x > 50 && position.y < 30 // Example office area
    }
    
    private fun isNearCommonArea(position: Position): Boolean {
        return position.x in 30.0..50.0 && position.y in 25.0..40.0
    }
    
    private fun isNearCafeteria(position: Position): Boolean {
        return position.x < 20 && position.y < 25 // Example cafeteria area
    }
    
    private fun isNearElevator(position: Position): Boolean {
        return position.distanceTo(Position(40.0, 45.0, 0)) < 5.0 // Near elevator
    }
    
    private fun getSimulatedProximity(): Float {
        return kotlin.random.Random.nextDouble(0.5, 5.0).toFloat()
    }
    
    /**
     * Check if a line path intersects with an obstacle
     */
    fun pathIntersectsObstacle(start: Position, end: Position, obstacle: Obstacle): Boolean {
        // Simple rectangle intersection check
        val pathMinX = minOf(start.x, end.x)
        val pathMaxX = maxOf(start.x, end.x)
        val pathMinY = minOf(start.y, end.y)
        val pathMaxY = maxOf(start.y, end.y)
        
        val obstacleMinX = obstacle.position.x - obstacle.size.width / 2
        val obstacleMaxX = obstacle.position.x + obstacle.size.width / 2
        val obstacleMinY = obstacle.position.y - obstacle.size.height / 2
        val obstacleMaxY = obstacle.position.y + obstacle.size.height / 2
        
        return !(pathMaxX < obstacleMinX || pathMinX > obstacleMaxX ||
                pathMaxY < obstacleMinY || pathMinY > obstacleMaxY)
    }
    
    private fun getDirectPath(start: Position, end: Position): List<Position> {
        return listOf(start, end)
    }
    
    private fun createAvoidanceWaypoint(start: Position, end: Position, obstacle: Obstacle): Position {
        // Create a waypoint that goes around the obstacle
        val obstacleCenter = obstacle.position
        val avoidanceDistance = maxOf(obstacle.size.width, obstacle.size.height) + 1.0
        
        // Choose side to avoid based on path direction
        val pathDirection = Position(end.x - start.x, end.y - start.y, 0)
        val avoidanceOffset = if (pathDirection.x > pathDirection.y) {
            Position(0.0, avoidanceDistance, 0) // Go around vertically
        } else {
            Position(avoidanceDistance, 0.0, 0) // Go around horizontally
        }
        
        return Position(
            obstacleCenter.x + avoidanceOffset.x,
            obstacleCenter.y + avoidanceOffset.y,
            start.floor
        )
    }
}
