package com.example.indoornavigation.data.navigation

import com.example.indoornavigation.data.models.FloorTransitionType
import com.example.indoornavigation.data.models.NavNodeEnhanced
import com.example.indoornavigation.data.models.Position
import java.util.*
import kotlin.math.sqrt

/**
 * Enhanced navigation service that supports advanced pathfinding features:
 * - Obstacles avoidance
 * - Floor transitions
 * - Accessibility preferences
 */
class EnhancedNavigationService(private val navGraph: Map<String, NavNodeEnhanced>) {

    companion object {
        // Cost factors for different transition types
        private const val FLOOR_TRANSITION_COST_FACTOR = 5.0f // Base cost multiplier for floor transitions
        private const val STAIRS_COST_FACTOR = 1.0f
        private const val ELEVATOR_COST_FACTOR = 1.5f // Slightly higher cost due to waiting time
        private const val ESCALATOR_COST_FACTOR = 1.2f
        
        // Accessible route settings
        private const val ACCESSIBLE_FLOOR_TRANSITION_COST_FACTOR = 10.0f // Higher cost for floor transitions when accessibility needed
    }
    
    // Navigation preferences
    private var preferAccessibleRoutes = false
    private var preferredFloorTransitionType: FloorTransitionType? = null
    
    /**
     * Find a path between two nodes using A* algorithm
     * @param startNodeId The ID of the starting node
     * @param endNodeId The ID of the destination node
     * @return A list of nodes representing the path, or empty list if no path found
     */
    fun findPath(startNodeId: String, endNodeId: String): List<NavNodeEnhanced> {
        val startNode = navGraph[startNodeId] ?: return emptyList()
        val endNode = navGraph[endNodeId] ?: return emptyList()
        
        // Nodes to be evaluated
        val openSet = PriorityQueue<PathNode> { a, b -> 
            a.fScore.compareTo(b.fScore)
        }
        
        // Initialize start node
        openSet.add(PathNode(startNodeId, 0f, estimateDistance(startNode, endNode)))
        
        // Keep track of where we came from for each node
        val cameFrom = mutableMapOf<String, String>()
        
        // Cost from start to each node
        val gScore = mutableMapOf<String, Float>().apply {
            put(startNodeId, 0f)
        }
        
        while (openSet.isNotEmpty()) {
            val current = openSet.poll()
            
            // Reached the goal
            if (current?.nodeId == endNodeId) {
                return reconstructPath(cameFrom, current.nodeId!!)
            }
            
            // Get the current node
            val currentNodeId = current?.nodeId ?: continue
            val currentNode = navGraph[currentNodeId] ?: continue
            
            // Skip non-traversable nodes (except start and end)
            if (!currentNode.isTraversable && currentNodeId != startNodeId && currentNodeId != endNodeId) {
                continue
            }
            
            // Check all connected nodes
            for (connection in currentNode.connections) {
                val neighbor = navGraph[connection.targetNodeId] ?: continue
                
                // Skip non-traversable neighbors (except destination)
                if (!neighbor.isTraversable && connection.targetNodeId != endNodeId) {
                    continue
                }
                
                // Calculate cost to this neighbor
                val transitionCost = calculateTransitionCost(connection)
                
                // Calculate tentative gScore
                val tentativeGScore = gScore.getOrDefault(currentNodeId, Float.POSITIVE_INFINITY) + 
                                      connection.distance * transitionCost
                
                // This path is better than any previous one
                if (tentativeGScore < gScore.getOrDefault(connection.targetNodeId, Float.POSITIVE_INFINITY)) {
                    // Record the best path
                    cameFrom[connection.targetNodeId] = currentNodeId
                    gScore[connection.targetNodeId] = tentativeGScore
                    
                    // Add to openSet with fScore (gScore + heuristic)
                    val fScore = tentativeGScore + estimateDistance(neighbor, endNode)
                    openSet.add(PathNode(connection.targetNodeId, tentativeGScore, fScore))
                }
            }
        }
        
        // No path found
        return emptyList()
    }
    
    /**
     * Calculate the cost factor for a transition between floors
     */
    private fun calculateTransitionCost(connection: com.example.indoornavigation.data.models.NavNodeConnection): Float {
        // Regular connections have base cost
        if (!connection.isFloorTransition) {
            return 1.0f
        }
        
        val baseCost = if (preferAccessibleRoutes) {
            ACCESSIBLE_FLOOR_TRANSITION_COST_FACTOR
        } else {
            FLOOR_TRANSITION_COST_FACTOR
        }
        
        // If we have a preferred transition type and this matches, reduce cost
        if (preferredFloorTransitionType != null && connection.transitionType == preferredFloorTransitionType) {
            return baseCost * 0.8f
        }
        
        // Otherwise, apply cost based on transition type
        return baseCost * when (connection.transitionType) {
            FloorTransitionType.STAIRS -> STAIRS_COST_FACTOR
            FloorTransitionType.ELEVATOR -> ELEVATOR_COST_FACTOR
            FloorTransitionType.ESCALATOR -> ESCALATOR_COST_FACTOR
            null -> 1.5f // Unknown transition type
        }
    }
    
    /**
     * Set preference for accessible routes
     */
    fun setAccessibleRoutes(enabled: Boolean) {
        preferAccessibleRoutes = enabled
    }
    
    /**
     * Set preferred floor transition type
     */
    fun setPreferredFloorTransitionType(type: FloorTransitionType?) {
        preferredFloorTransitionType = type
    }
    
    /**
     * Estimate distance between two nodes (heuristic for A*)
     */
    private fun estimateDistance(from: NavNodeEnhanced, to: NavNodeEnhanced): Float {
        // Calculate Euclidean distance in 2D
        val dx = to.position.x - from.position.x
        val dy = to.position.y - from.position.y
        val distance2D = sqrt(dx*dx + dy*dy).toFloat()
        
        // Add penalty for floor transitions
        val floorDistance = Math.abs(to.floorId - from.floorId) * 10f
        
        return distance2D + floorDistance
    }
    
    /**
     * Reconstruct the path from the cameFrom map
     */
    private fun reconstructPath(cameFrom: Map<String, String>, current: String): List<NavNodeEnhanced> {
        val totalPath = mutableListOf<NavNodeEnhanced>()
        var currentId = current
        
        while (cameFrom.containsKey(currentId)) {
            navGraph[currentId]?.let { totalPath.add(0, it) }
            currentId = cameFrom[currentId]!!
        }
        
        // Add the start node
        navGraph[currentId]?.let { totalPath.add(0, it) }
        
        return totalPath
    }
    
    /**
     * Get positions from a path of nodes
     */
    fun getPositionsFromPath(path: List<NavNodeEnhanced>): List<Position> {
        return path.map { it.position }
    }
    
    /**
     * Find closest node to a position
     */
    fun findClosestNode(position: Position): NavNodeEnhanced? {
        return navGraph.values.minByOrNull {
            val dx = it.position.x - position.x
            val dy = it.position.y - position.y
            val dFloor = if (it.floorId == position.floor) 0 else 100 // Heavy penalty for different floors
            sqrt(dx*dx + dy*dy) + dFloor
        }
    }
    
    /**
     * Helper class for A* algorithm node tracking
     */
    private data class PathNode(
        val nodeId: String,
        val gScore: Float, // Cost from start to this node
        val fScore: Float  // gScore + heuristic to goal
    )
}