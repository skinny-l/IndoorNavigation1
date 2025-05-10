package com.example.indoornavigation.utils

import com.example.indoornavigation.data.models.NavNode
import com.example.indoornavigation.data.models.Path
import com.example.indoornavigation.data.models.Position
import java.util.*
import kotlin.collections.HashMap
import kotlin.math.sqrt

/**
 * Engine for calculating paths between points using A* algorithm
 */
class PathfindingEngine(private val nodes: Map<String, NavNode>) {

    /**
     * Calculate shortest path between two positions
     */
    fun findPath(start: Position, end: Position): Path? {
        // Find closest nodes to start and end positions
        val startNode = findClosestNode(start)
        val endNode = findClosestNode(end)
        
        if (startNode == null || endNode == null) {
            return null
        }
        
        // If start and end are on different floors, find path through stairs/elevator
        if (start.floor != end.floor) {
            return findMultiFloorPath(startNode, endNode, start, end)
        }
        
        // If on same floor, find direct path
        val nodePath = findPathBetweenNodes(startNode.id, endNode.id) ?: return null
        
        // Convert node IDs to positions
        val positionPath = nodePath.map { nodes[it]?.position ?: return null }
        
        // Create complete path with start and end positions
        val completePath = listOf(start) + positionPath + listOf(end)
        
        return Path(start, end, completePath)
    }
    
    /**
     * Find path between nodes on different floors
     */
    private fun findMultiFloorPath(
        startNode: NavNode,
        endNode: NavNode,
        startPos: Position,
        endPos: Position
    ): Path? {
        // Find floor transition nodes (stairs, elevators)
        val startFloorTransitions = nodes.values.filter { 
            it.position.floor == startPos.floor && 
            it.connections.any { connId -> nodes[connId]?.position?.floor != startPos.floor } 
        }
        
        val endFloorTransitions = nodes.values.filter { 
            it.position.floor == endPos.floor && 
            it.connections.any { connId -> nodes[connId]?.position?.floor != endPos.floor } 
        }
        
        // Find best path through floor transitions
        var bestPath: List<Position>? = null
        var bestDistance = Double.MAX_VALUE
        
        for (startTransition in startFloorTransitions) {
            // Find path from start to transition
            val pathToTransition = findPathBetweenNodes(startNode.id, startTransition.id) ?: continue
            
            for (endTransition in endFloorTransitions) {
                // Check if transitions are connected
                val connected = startTransition.connections.any { connId -> 
                    val connNode = nodes[connId]
                    connNode != null && connNode.connections.contains(endTransition.id) 
                }
                
                if (!connected) continue
                
                // Find path from transition to end
                val pathFromTransition = findPathBetweenNodes(endTransition.id, endNode.id) ?: continue
                
                // Convert to positions
                val posToTransition = pathToTransition.mapNotNull { nodes[it]?.position }
                val posFromTransition = pathFromTransition.mapNotNull { nodes[it]?.position }
                
                // Create complete path
                val completePath = listOf(startPos) + posToTransition + 
                        posFromTransition + listOf(endPos)
                
                // Calculate path distance
                val distance = calculatePathDistance(completePath)
                
                // Update best path if this one is shorter
                if (distance < bestDistance) {
                    bestDistance = distance
                    bestPath = completePath
                }
            }
        }
        
        // Return best path
        return bestPath?.let { Path(startPos, endPos, it) }
    }
    
    /**
     * Calculate path distance
     */
    private fun calculatePathDistance(path: List<Position>): Double {
        var distance = 0.0
        
        for (i in 0 until path.size - 1) {
            val p1 = path[i]
            val p2 = path[i + 1]
            
            // Calculate Euclidean distance
            val dx = p2.x - p1.x
            val dy = p2.y - p1.y
            
            // Add floor transition penalty
            val floorPenalty = if (p1.floor != p2.floor) 50.0 else 0.0
            
            distance += sqrt(dx * dx + dy * dy) + floorPenalty
        }
        
        return distance
    }
    
    /**
     * Find closest node to a position
     */
    private fun findClosestNode(position: Position): NavNode? {
        var closestNode: NavNode? = null
        var minDistance = Double.MAX_VALUE
        
        // Find nodes on the same floor
        val floorNodes = nodes.values.filter { it.position.floor == position.floor }
        
        for (node in floorNodes) {
            val dx = node.position.x - position.x
            val dy = node.position.y - position.y
            val distance = sqrt(dx * dx + dy * dy)
            
            if (distance < minDistance) {
                minDistance = distance
                closestNode = node
            }
        }
        
        return closestNode
    }
    
    /**
     * Find path between two nodes using A* algorithm
     */
    private fun findPathBetweenNodes(startId: String, endId: String): List<String>? {
        // A* implementation
        val openSet = PriorityQueue<NodeWithCost> { a, b -> a.fScore.compareTo(b.fScore) }
        val closedSet = HashSet<String>()
        
        // Track scores and path
        val gScore = HashMap<String, Double>().apply { this[startId] = 0.0 }
        val fScore = HashMap<String, Double>().apply { 
            this[startId] = heuristicCost(startId, endId) 
        }
        val cameFrom = HashMap<String, String>()
        
        // Start with the start node
        openSet.add(NodeWithCost(startId, fScore[startId] ?: Double.MAX_VALUE))
        
        while (openSet.isNotEmpty()) {
            val current = openSet.poll().id
            
            // Found the path
            if (current == endId) {
                return reconstructPath(cameFrom, current)
            }
            
            closedSet.add(current)
            
            // Process neighbors
            nodes[current]?.connections?.forEach { neighbor ->
                if (closedSet.contains(neighbor)) return@forEach
                
                // Calculate tentative gScore
                val tentativeGScore = (gScore[current] ?: Double.MAX_VALUE) + 
                        distanceBetween(current, neighbor)
                
                // If this path is better
                if (tentativeGScore < (gScore[neighbor] ?: Double.MAX_VALUE)) {
                    // Record this path
                    cameFrom[neighbor] = current
                    gScore[neighbor] = tentativeGScore
                    fScore[neighbor] = tentativeGScore + heuristicCost(neighbor, endId)
                    
                    // Add to open set if not already there
                    if (openSet.none { it.id == neighbor }) {
                        openSet.add(NodeWithCost(neighbor, fScore[neighbor] ?: Double.MAX_VALUE))
                    }
                }
            }
        }
        
        // No path found
        return null
    }
    
    /**
     * Calculate heuristic cost between two nodes (Euclidean distance)
     */
    private fun heuristicCost(nodeId1: String, nodeId2: String): Double {
        val node1 = nodes[nodeId1]?.position ?: return Double.MAX_VALUE
        val node2 = nodes[nodeId2]?.position ?: return Double.MAX_VALUE
        
        val dx = node2.x - node1.x
        val dy = node2.y - node1.y
        
        // Add floor difference penalty
        val floorPenalty = if (node1.floor != node2.floor) 50.0 else 0.0
        
        return sqrt(dx * dx + dy * dy) + floorPenalty
    }
    
    /**
     * Calculate actual distance between two connected nodes
     */
    private fun distanceBetween(nodeId1: String, nodeId2: String): Double {
        val node1 = nodes[nodeId1]?.position ?: return Double.MAX_VALUE
        val node2 = nodes[nodeId2]?.position ?: return Double.MAX_VALUE
        
        val dx = node2.x - node1.x
        val dy = node2.y - node1.y
        
        // Add floor difference penalty
        val floorPenalty = if (node1.floor != node2.floor) 50.0 else 0.0
        
        return sqrt(dx * dx + dy * dy) + floorPenalty
    }
    
    /**
     * Reconstruct path from end to start
     */
    private fun reconstructPath(cameFrom: Map<String, String>, current: String): List<String> {
        val path = mutableListOf(current)
        var currentNode = current
        
        while (cameFrom.containsKey(currentNode)) {
            currentNode = cameFrom[currentNode]!!
            path.add(0, currentNode)
        }
        
        return path
    }
    
    /**
     * Helper class for A* algorithm
     */
    private data class NodeWithCost(val id: String, val fScore: Double)
}