package com.example.indoornavigation.utils

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.example.indoornavigation.data.models.NavNode
import com.example.indoornavigation.data.models.Position
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Advanced Navigation System that integrates all solutions to overcome current limitations
 * Provides pixel-perfect, obstacle-aware, automatically generated navigation
 */
class AdvancedNavigationSystem(private val context: Context) {
    
    // Component systems
    private val floorPlanAnalyzer = FloorPlanAnalyzer()
    private val obstacleDetector = ObstacleDetector(context)
    private val meshGenerator = AutoNavigationMeshGenerator()
    
    // Current navigation state
    private val _navigationMesh = MutableStateFlow<Map<String, NavNode>>(emptyMap())
    val navigationMesh: StateFlow<Map<String, NavNode>> = _navigationMesh.asStateFlow()
    
    private val _currentObstacles = MutableStateFlow<List<ObstacleDetector.Obstacle>>(emptyList())
    val currentObstacles: StateFlow<List<ObstacleDetector.Obstacle>> = _currentObstacles.asStateFlow()
    
    private var isInitialized = false
    
    /**
     * Initialize the advanced navigation system with building floor plan
     */
    suspend fun initialize(
        floorPlanBitmap: Bitmap,
        buildingWidth: Double,
        buildingHeight: Double
    ): Boolean {
        
        try {
            Log.d("AdvancedNav", "Initializing advanced navigation system...")
            
            // Step 1: Generate automatic navigation mesh from floor plan
            val meshResult = meshGenerator.generateNavigationMesh(
                floorPlanBitmap, buildingWidth, buildingHeight
            )
            
            if (meshResult.confidence < 0.7f) {
                Log.w("AdvancedNav", "Low confidence mesh generated: ${meshResult.confidence}")
            }
            
            _navigationMesh.value = meshResult.nodes
            
            // Step 2: Initialize obstacle detection
            // This will be updated continuously during navigation
            
            isInitialized = true
            Log.d("AdvancedNav", "Advanced navigation system initialized with ${meshResult.nodes.size} nodes")
            
            return true
            
        } catch (e: Exception) {
            Log.e("AdvancedNav", "Failed to initialize advanced navigation: ${e.message}")
            return false
        }
    }
    
    /**
     * Calculate pixel-perfect route with obstacle avoidance
     */
    suspend fun calculateAdvancedRoute(
        start: Position,
        end: Position,
        avoidObstacles: Boolean = true
    ): AdvancedRoute? {
        
        if (!isInitialized) {
            Log.e("AdvancedNav", "System not initialized")
            return null
        }
        
        try {
            // Step 1: Update obstacle detection
            if (avoidObstacles) {
                val obstacles = obstacleDetector.scanForObstacles(start)
                _currentObstacles.value = obstacles
            }
            
            // Step 2: Find path using enhanced navigation mesh
            val basicPath = findPathThroughMesh(start, end)
            
            if (basicPath == null) {
                Log.w("AdvancedNav", "No basic path found")
                return null
            }
            
            // Step 3: Apply obstacle avoidance if enabled
            val finalPath = if (avoidObstacles) {
                applyObstacleAvoidance(basicPath)
            } else {
                basicPath
            }
            
            // Step 4: Enhance path with pixel-perfect waypoints
            val enhancedPath = enhancePathPrecision(finalPath)
            
            // Step 5: Generate detailed navigation instructions
            val instructions = generateDetailedInstructions(enhancedPath)
            
            return AdvancedRoute(
                waypoints = enhancedPath,
                instructions = instructions,
                totalDistance = calculateTotalDistance(enhancedPath),
                estimatedTime = calculateEstimatedTime(enhancedPath),
                confidence = calculateRouteConfidence(enhancedPath)
            )
            
        } catch (e: Exception) {
            Log.e("AdvancedNav", "Error calculating advanced route: ${e.message}")
            return null
        }
    }
    
    /**
     * Find path through the automatically generated navigation mesh
     */
    private fun findPathThroughMesh(start: Position, end: Position): List<Position>? {
        val nodes = _navigationMesh.value
        if (nodes.isEmpty()) return null
        
        // Use enhanced A* pathfinding through the mesh
        val pathfindingEngine = PathfindingEngine(nodes)
        val path = pathfindingEngine.findPath(start, end)
        
        return path?.waypoints
    }
    
    /**
     * Apply obstacle avoidance to the path
     */
    private fun applyObstacleAvoidance(originalPath: List<Position>): List<Position> {
        val obstacles = _currentObstacles.value
        if (obstacles.isEmpty()) return originalPath
        
        val avoidancePath = mutableListOf<Position>()
        
        for (i in 0 until originalPath.size - 1) {
            val currentPoint = originalPath[i]
            val nextPoint = originalPath[i + 1]
            
            avoidancePath.add(currentPoint)
            
            // Check if segment is blocked by obstacles
            val isBlocked = obstacles.any { obstacle ->
                obstacleDetector.pathIntersectsObstacle(currentPoint, nextPoint, obstacle)
            }
            
            if (isBlocked) {
                // Get alternative path around obstacles
                val detourPath = obstacleDetector.getObstacleAvoidancePath(currentPoint, nextPoint)
                avoidancePath.addAll(detourPath.drop(1)) // Skip duplicate start point
            }
        }
        
        // Add final destination
        avoidancePath.add(originalPath.last())
        
        return avoidancePath
    }
    
    /**
     * Enhance path precision with pixel-perfect waypoints
     */
    private fun enhancePathPrecision(basicPath: List<Position>): List<Position> {
        val enhancedPath = mutableListOf<Position>()
        
        for (i in 0 until basicPath.size - 1) {
            val current = basicPath[i]
            val next = basicPath[i + 1]
            
            enhancedPath.add(current)
            
            // Add intermediate waypoints for long segments
            val distance = current.distanceTo(next)
            if (distance > 5.0) { // Add waypoints every 5 meters
                val segments = (distance / 2.5).toInt() // 2.5m segments
                
                for (j in 1 until segments) {
                    val progress = j.toDouble() / segments
                    val intermediatePoint = Position(
                        x = current.x + (next.x - current.x) * progress,
                        y = current.y + (next.y - current.y) * progress,
                        floor = current.floor
                    )
                    enhancedPath.add(intermediatePoint)
                }
            }
        }
        
        enhancedPath.add(basicPath.last())
        return enhancedPath
    }
    
    /**
     * Generate detailed turn-by-turn navigation instructions
     */
    private fun generateDetailedInstructions(path: List<Position>): List<NavigationInstruction> {
        val instructions = mutableListOf<NavigationInstruction>()
        
        if (path.size < 2) return instructions
        
        // Start instruction
        instructions.add(NavigationInstruction(
            type = InstructionType.START,
            description = "Start navigation from your current location",
            distance = 0.0,
            direction = Direction.STRAIGHT
        ))
        
        // Generate instructions for each turn/segment
        for (i in 1 until path.size - 1) {
            val prev = path[i - 1]
            val current = path[i]
            val next = path[i + 1]
            
            val instruction = analyzeMovement(prev, current, next)
            instruction?.let { instructions.add(it) }
        }
        
        // Destination instruction
        val finalDistance = if (path.size >= 2) {
            path[path.size - 2].distanceTo(path.last())
        } else 0.0
        
        instructions.add(NavigationInstruction(
            type = InstructionType.DESTINATION,
            description = "You have arrived at your destination",
            distance = finalDistance,
            direction = Direction.STRAIGHT
        ))
        
        return instructions
    }
    
    /**
     * Analyze movement between three points to determine instruction
     */
    private fun analyzeMovement(
        prev: Position,
        current: Position,
        next: Position
    ): NavigationInstruction? {
        
        val distance = prev.distanceTo(current)
        if (distance < 1.0) return null // Skip very short segments
        
        // Calculate turn angle
        val angle1 = kotlin.math.atan2(current.y - prev.y, current.x - prev.x)
        val angle2 = kotlin.math.atan2(next.y - current.y, next.x - current.x)
        val turnAngle = Math.toDegrees(angle2 - angle1)
        
        val (direction, description) = when {
            kotlin.math.abs(turnAngle) < 15 -> {
                Direction.STRAIGHT to "Continue straight for ${String.format("%.0f", distance)}m"
            }
            turnAngle > 15 && turnAngle < 75 -> {
                Direction.SLIGHT_RIGHT to "Turn slightly right and continue for ${String.format("%.0f", distance)}m"
            }
            turnAngle >= 75 && turnAngle < 105 -> {
                Direction.RIGHT to "Turn right and continue for ${String.format("%.0f", distance)}m"
            }
            turnAngle >= 105 -> {
                Direction.SHARP_RIGHT to "Turn sharp right and continue for ${String.format("%.0f", distance)}m"
            }
            turnAngle < -15 && turnAngle > -75 -> {
                Direction.SLIGHT_LEFT to "Turn slightly left and continue for ${String.format("%.0f", distance)}m"
            }
            turnAngle <= -75 && turnAngle > -105 -> {
                Direction.LEFT to "Turn left and continue for ${String.format("%.0f", distance)}m"
            }
            turnAngle <= -105 -> {
                Direction.SHARP_LEFT to "Turn sharp left and continue for ${String.format("%.0f", distance)}m"
            }
            else -> Direction.STRAIGHT to "Continue for ${String.format("%.0f", distance)}m"
        }
        
        return NavigationInstruction(
            type = InstructionType.TURN,
            description = description,
            distance = distance,
            direction = direction
        )
    }
    
    /**
     * Calculate total route distance
     */
    private fun calculateTotalDistance(path: List<Position>): Double {
        var totalDistance = 0.0
        for (i in 0 until path.size - 1) {
            totalDistance += path[i].distanceTo(path[i + 1])
        }
        return totalDistance
    }
    
    /**
     * Calculate estimated travel time
     */
    private fun calculateEstimatedTime(path: List<Position>): Int {
        val distance = calculateTotalDistance(path)
        val walkingSpeed = 1.4 // meters per second
        return (distance / walkingSpeed / 60).toInt() // minutes
    }
    
    /**
     * Calculate route confidence score
     */
    private fun calculateRouteConfidence(path: List<Position>): Float {
        // Factors affecting confidence:
        // - Number of obstacles avoided
        // - Path complexity
        // - Navigation mesh quality
        
        val obstacles = _currentObstacles.value
        val obstacleCount = obstacles.size
        val pathComplexity = path.size
        
        var confidence = 1.0f
        
        // Reduce confidence based on obstacles
        confidence -= (obstacleCount * 0.1f).coerceAtMost(0.3f)
        
        // Reduce confidence for overly complex paths
        if (pathComplexity > 20) {
            confidence -= 0.1f
        }
        
        return confidence.coerceAtLeast(0.1f)
    }
    
    /**
     * Report an obstacle for crowdsourcing
     */
    fun reportObstacle(position: Position, type: ObstacleDetector.ObstacleType) {
        val obstacle = ObstacleDetector.Obstacle(
            id = "user_reported_${System.currentTimeMillis()}",
            position = position,
            type = type,
            size = ObstacleDetector.ObstacleSize(1.0, 1.0, 1.0),
            confidence = 0.8f,
            lastUpdated = System.currentTimeMillis(),
            isTemporary = true
        )
        
        obstacleDetector.reportObstacle(obstacle)
    }
    
    // Data classes
    data class AdvancedRoute(
        val waypoints: List<Position>,
        val instructions: List<NavigationInstruction>,
        val totalDistance: Double,
        val estimatedTime: Int,
        val confidence: Float
    )
    
    data class NavigationInstruction(
        val type: InstructionType,
        val description: String,
        val distance: Double,
        val direction: Direction
    )
    
    enum class InstructionType {
        START, TURN, DESTINATION, OBSTACLE_AVOIDANCE
    }
    
    enum class Direction {
        STRAIGHT, SLIGHT_LEFT, LEFT, SHARP_LEFT,
        SLIGHT_RIGHT, RIGHT, SHARP_RIGHT
    }
}