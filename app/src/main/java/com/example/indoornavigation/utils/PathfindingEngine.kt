package com.example.indoornavigation.utils

import com.example.indoornavigation.data.models.NavNode
import com.example.indoornavigation.data.models.Path
import com.example.indoornavigation.data.models.Position
import kotlin.math.abs
import java.util.*

/**
 * A* pathfinding implementation for indoor navigation
 */
class PathfindingEngine(private val nodes: Map<String, NavNode>) {

    // Heuristic function: Euclidean distance for 2D, with floor penalty
    private fun heuristic(a: Position, b: Position): Double {
        // Calculate 2D distance
        val dx = a.x - b.x
        val dy = a.y - b.y
        val distance2D = Math.sqrt(dx * dx + dy * dy)
        
        // Add floor transition penalty
        val floorPenalty = if (a.floor != b.floor) 
            Math.abs(a.floor - b.floor) * 15.0 // 15 meters penalty per floor
        else 
            0.0
            
        return distance2D + floorPenalty
    }

    // Find closest node to a position
    private fun findClosestNode(position: Position): NavNode? {
        if (nodes.isEmpty()) return null
        
        return nodes.values.minByOrNull { 
            it.position.distanceTo(position)
        }
    }

    /**
     * Find a path between two positions using A* algorithm
     */
    fun findPath(start: Position, end: Position): Path? {
        // If nodes map is empty, fall back to direct path
        if (nodes.isEmpty()) {
            return Path(
                start = start,
                end = end,
                waypoints = listOf(start, end)
            )
        }
        
        // Find closest nodes to start and end positions
        val startNode = findClosestNode(start) ?: return null
        val endNode = findClosestNode(end) ?: return null
        
        // A* algorithm implementation
        val openSet = PriorityQueue<NodeRecord>(compareBy { it.fScore })
        val closedSet = mutableSetOf<String>()
        val cameFrom = mutableMapOf<String, String>()
        
        // Keep track of gScores (cost from start to current)
        val gScore = mutableMapOf<String, Double>()
        nodes.keys.forEach { gScore[it] = Double.MAX_VALUE }
        gScore[startNode.id] = 0.0
        
        // Keep track of fScores (gScore + heuristic to end)
        val fScore = mutableMapOf<String, Double>()
        nodes.keys.forEach { fScore[it] = Double.MAX_VALUE }
        fScore[startNode.id] = heuristic(startNode.position, endNode.position)
        
        // Add start node to open set
        openSet.add(NodeRecord(startNode.id, fScore[startNode.id]!!))
        
        // Main A* loop
        while (openSet.isNotEmpty()) {
            // Get node with lowest fScore
            val current = openSet.poll().nodeId
            
            // If we reached the goal, reconstruct and return the path
            if (current == endNode.id) {
                return reconstructPath(cameFrom, startNode.id, endNode.id, start, end)
            }
            
            closedSet.add(current)
            
            // Process each neighbor
            nodes[current]?.connections?.forEach { neighborId ->
                // Skip if already evaluated
                if (closedSet.contains(neighborId)) return@forEach
                
                val neighbor = nodes[neighborId] ?: return@forEach
                
                // Calculate tentative gScore
                val tentativeGScore = gScore[current]!! + 
                    nodes[current]!!.position.distanceTo(neighbor.position)
                
                // If this path is better than any previous one, record it
                if (tentativeGScore < gScore[neighborId]!!) {
                    cameFrom[neighborId] = current
                    gScore[neighborId] = tentativeGScore
                    fScore[neighborId] = tentativeGScore + 
                        heuristic(neighbor.position, endNode.position)
                    
                    // Add to open set if not already in it
                    if (openSet.none { it.nodeId == neighborId }) {
                        openSet.add(NodeRecord(neighborId, fScore[neighborId]!!))
                    }
                }
            }
        }
        
        // If we get here, no path was found
        return null
    }
    
    // Helper class for priority queue
    private data class NodeRecord(val nodeId: String, val fScore: Double)
    
    // Reconstruct path from A* result
    private fun reconstructPath(
        cameFrom: Map<String, String>, 
        startId: String,
        endId: String,
        startPosition: Position,
        endPosition: Position
    ): Path {
        val nodeIds = mutableListOf<String>()
        var current = endId
        
        // Rebuild the chain of nodes
        while (current != startId) {
            nodeIds.add(0, current)
            current = cameFrom[current] ?: break
        }
        nodeIds.add(0, startId)
        
        // Convert to positions
        val waypoints = mutableListOf<Position>()
        waypoints.add(startPosition) // Start with actual start position
        
        // Add intermediate nodes
        for (i in 0 until nodeIds.size) {
            val nodeId = nodeIds[i]
            val nodePosition = nodes[nodeId]?.position ?: continue
            
            // Skip if too close to previous waypoint to avoid redundancy
            if (waypoints.isNotEmpty() && 
                waypoints.last().distanceTo(nodePosition) < 1.0) {
                continue
            }
            
            waypoints.add(nodePosition)
        }
        
        // Ensure end position is added
        if (waypoints.last().distanceTo(endPosition) > 0.1) {
            waypoints.add(endPosition)
        }
        
        return Path(startPosition, endPosition, waypoints)
    }
}