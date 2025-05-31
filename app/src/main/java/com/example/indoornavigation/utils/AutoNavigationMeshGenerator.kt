package com.example.indoornavigation.utils

import android.graphics.Bitmap
import android.util.Log
import com.example.indoornavigation.data.models.NavNode
import com.example.indoornavigation.data.models.Position
import kotlin.math.sqrt
import kotlin.math.pow

/**
 * Automatically generates navigation mesh from floor plan images
 * Eliminates need for manual node placement
 */
class AutoNavigationMeshGenerator {
    
    data class NavigationMesh(
        val nodes: Map<String, NavNode>,
        val connections: List<Pair<String, String>>,
        val confidence: Float
    )
    
    private val floorPlanAnalyzer = FloorPlanAnalyzer()
    
    /**
     * Generate complete navigation mesh automatically from floor plan
     */
    fun generateNavigationMesh(
        floorPlanBitmap: Bitmap,
        buildingWidth: Double,
        buildingHeight: Double
    ): NavigationMesh {
        
        Log.d("AutoNavMesh", "Starting automatic navigation mesh generation...")
        
        // Step 1: Analyze floor plan to detect walls and navigable areas
        val analysis = floorPlanAnalyzer.analyzeFloorPlan(floorPlanBitmap)
        
        // Step 2: Generate nodes in navigable areas
        val generatedNodes = generateNodesFromAreas(analysis.navigableAreas, buildingWidth, buildingHeight)
        
        // Step 3: Create connections between nodes
        val connections = generateConnections(generatedNodes, analysis.walls)
        
        // Step 4: Optimize mesh for performance
        val optimizedMesh = optimizeMesh(generatedNodes, connections)
        
        // Step 5: Validate mesh quality
        val confidence = validateMeshQuality(optimizedMesh, analysis)
        
        Log.d("AutoNavMesh", "Generated mesh with ${optimizedMesh.size} nodes, confidence: $confidence")
        
        return NavigationMesh(
            nodes = optimizedMesh,
            connections = connections,
            confidence = confidence
        )
    }
    
    /**
     * Generate navigation nodes from detected navigable areas
     */
    private fun generateNodesFromAreas(
        areas: List<FloorPlanAnalyzer.NavigableArea>,
        buildingWidth: Double,
        buildingHeight: Double
    ): Map<String, NavNode> {
        
        val nodes = mutableMapOf<String, NavNode>()
        var nodeIndex = 0
        
        areas.forEach { area ->
            // Convert pixel coordinates to real-world coordinates
            val realX = (area.centerX / 1000.0) * buildingWidth // Assuming 1000px image width
            val realY = (area.centerY / 800.0) * buildingHeight // Assuming 800px image height
            
            when (area.type) {
                FloorPlanAnalyzer.AreaType.CORRIDOR -> {
                    // Generate multiple nodes along corridor
                    val nodeCount = maxOf(3, (maxOf(area.width, area.height) / 50).toInt())
                    
                    if (area.width > area.height) {
                        // Horizontal corridor
                        for (i in 0 until nodeCount) {
                            val progress = i.toDouble() / (nodeCount - 1)
                            val nodeX = realX - (area.width / 2000.0 * buildingWidth) + 
                                       (progress * area.width / 1000.0 * buildingWidth)
                            
                            val nodeId = "auto_corridor_h_${nodeIndex++}"
                            nodes[nodeId] = NavNode(nodeId, Position(nodeX, realY, 0))
                        }
                    } else {
                        // Vertical corridor
                        for (i in 0 until nodeCount) {
                            val progress = i.toDouble() / (nodeCount - 1)
                            val nodeY = realY - (area.height / 1600.0 * buildingHeight) + 
                                       (progress * area.height / 800.0 * buildingHeight)
                            
                            val nodeId = "auto_corridor_v_${nodeIndex++}"
                            nodes[nodeId] = NavNode(nodeId, Position(realX, nodeY, 0))
                        }
                    }
                }
                
                FloorPlanAnalyzer.AreaType.INTERSECTION -> {
                    // Single node at intersection center
                    val nodeId = "auto_intersection_${nodeIndex++}"
                    nodes[nodeId] = NavNode(nodeId, Position(realX, realY, 0))
                }
                
                FloorPlanAnalyzer.AreaType.ROOM -> {
                    // Node at room entrance/center
                    val nodeId = "auto_room_${nodeIndex++}"
                    nodes[nodeId] = NavNode(nodeId, Position(realX, realY, 0))
                }
                
                FloorPlanAnalyzer.AreaType.STAIRWAY -> {
                    // Stairway access point
                    val nodeId = "auto_stairs_${nodeIndex++}"
                    nodes[nodeId] = NavNode(nodeId, Position(realX, realY, 0))
                }
                
                FloorPlanAnalyzer.AreaType.ELEVATOR_AREA -> {
                    // Elevator access point
                    val nodeId = "auto_elevator_${nodeIndex++}"
                    nodes[nodeId] = NavNode(nodeId, Position(realX, realY, 0))
                }
            }
        }
        
        return nodes
    }
    
    /**
     * Generate connections between nodes based on line-of-sight and proximity
     */
    private fun generateConnections(
        nodes: Map<String, NavNode>,
        walls: List<FloorPlanAnalyzer.WallSegment>
    ): List<Pair<String, String>> {
        
        val connections = mutableListOf<Pair<String, String>>()
        val nodeList = nodes.values.toList()
        
        // Connect nodes that have clear line of sight
        for (i in nodeList.indices) {
            for (j in i + 1 until nodeList.size) {
                val node1 = nodeList[i]
                val node2 = nodeList[j]
                
                val distance = node1.position.distanceTo(node2.position)
                
                // Only connect nearby nodes
                if (distance <= 10.0) { // Max connection distance
                    
                    // Check if path is blocked by walls
                    if (floorPlanAnalyzer.checkPathClearance(node1.position, node2.position)) {
                        connections.add(Pair(node1.id, node2.id))
                        
                        // Add bidirectional connection
                        node1.connections.add(node2.id)
                        node2.connections.add(node1.id)
                    }
                }
            }
        }
        
        return connections
    }
    
    /**
     * Optimize mesh by removing redundant nodes and improving connectivity
     */
    private fun optimizeMesh(
        nodes: Map<String, NavNode>,
        connections: List<Pair<String, String>>
    ): Map<String, NavNode> {
        
        val optimizedNodes = nodes.toMutableMap()
        
        // Remove isolated nodes (no connections)
        val connectedNodeIds = connections.flatMap { listOf(it.first, it.second) }.toSet()
        val isolatedNodes = nodes.filter { it.key !in connectedNodeIds }
        
        isolatedNodes.forEach { (nodeId, _) ->
            optimizedNodes.remove(nodeId)
            Log.d("AutoNavMesh", "Removed isolated node: $nodeId")
        }
        
        // Remove redundant nodes (too close to others with same connections)
        val redundantNodes = findRedundantNodes(optimizedNodes)
        redundantNodes.forEach { nodeId ->
            optimizedNodes.remove(nodeId)
            Log.d("AutoNavMesh", "Removed redundant node: $nodeId")
        }
        
        // Ensure minimum connectivity
        ensureMinimumConnectivity(optimizedNodes)
        
        return optimizedNodes
    }
    
    /**
     * Find nodes that are redundant (too close to others)
     */
    private fun findRedundantNodes(nodes: Map<String, NavNode>): List<String> {
        val redundant = mutableListOf<String>()
        val nodeList = nodes.values.toList()
        
        for (i in nodeList.indices) {
            for (j in i + 1 until nodeList.size) {
                val node1 = nodeList[i]
                val node2 = nodeList[j]
                
                val distance = node1.position.distanceTo(node2.position)
                
                // If nodes are too close (less than 2 meters)
                if (distance < 2.0) {
                    // Remove the one with fewer connections
                    if (node1.connections.size <= node2.connections.size) {
                        redundant.add(node1.id)
                    } else {
                        redundant.add(node2.id)
                    }
                }
            }
        }
        
        return redundant.distinct()
    }
    
    /**
     * Ensure minimum connectivity between all nodes
     */
    private fun ensureMinimumConnectivity(nodes: MutableMap<String, NavNode>) {
        // Find disconnected components
        val components = findConnectedComponents(nodes)
        
        // If there are multiple components, connect them
        if (components.size > 1) {
            connectComponents(components, nodes)
        }
    }
    
    /**
     * Find connected components in the graph
     */
    private fun findConnectedComponents(nodes: Map<String, NavNode>): List<Set<String>> {
        val visited = mutableSetOf<String>()
        val components = mutableListOf<Set<String>>()
        
        nodes.keys.forEach { nodeId ->
            if (nodeId !in visited) {
                val component = mutableSetOf<String>()
                exploreComponent(nodeId, nodes, visited, component)
                components.add(component)
            }
        }
        
        return components
    }
    
    /**
     * Explore connected component using DFS
     */
    private fun exploreComponent(
        nodeId: String,
        nodes: Map<String, NavNode>,
        visited: MutableSet<String>,
        component: MutableSet<String>
    ) {
        if (nodeId in visited) return
        
        visited.add(nodeId)
        component.add(nodeId)
        
        nodes[nodeId]?.connections?.forEach { connectedId ->
            exploreComponent(connectedId, nodes, visited, component)
        }
    }
    
    /**
     * Connect disconnected components
     */
    private fun connectComponents(
        components: List<Set<String>>,
        nodes: MutableMap<String, NavNode>
    ) {
        // Connect each component to the largest one
        val largestComponent = components.maxByOrNull { it.size } ?: return
        
        components.forEach { component ->
            if (component != largestComponent) {
                // Find closest nodes between components
                var minDistance = Double.MAX_VALUE
                var bestConnection: Pair<String, String>? = null
                
                component.forEach { nodeId1 ->
                    largestComponent.forEach { nodeId2 ->
                        val node1 = nodes[nodeId1] ?: return@forEach
                        val node2 = nodes[nodeId2] ?: return@forEach
                        
                        val distance = node1.position.distanceTo(node2.position)
                        if (distance < minDistance) {
                            minDistance = distance
                            bestConnection = Pair(nodeId1, nodeId2)
                        }
                    }
                }
                
                // Create connection
                bestConnection?.let { (id1, id2) ->
                    nodes[id1]?.connections?.add(id2)
                    nodes[id2]?.connections?.add(id1)
                    Log.d("AutoNavMesh", "Connected components: $id1 <-> $id2")
                }
            }
        }
    }
    
    /**
     * Validate the quality of generated mesh
     */
    private fun validateMeshQuality(
        nodes: Map<String, NavNode>,
        analysis: FloorPlanAnalyzer.FloorPlanAnalysisResult
    ): Float {
        
        var totalScore = 0.0f
        var maxScore = 0.0f
        
        // Coverage score: How well nodes cover navigable areas
        val coverageScore = calculateCoverageScore(nodes, analysis.navigableAreas)
        totalScore += coverageScore * 0.4f
        maxScore += 0.4f
        
        // Connectivity score: How well connected the graph is
        val connectivityScore = calculateConnectivityScore(nodes)
        totalScore += connectivityScore * 0.3f
        maxScore += 0.3f
        
        // Efficiency score: Reasonable number of nodes
        val efficiencyScore = calculateEfficiencyScore(nodes)
        totalScore += efficiencyScore * 0.3f
        maxScore += 0.3f
        
        return if (maxScore > 0) totalScore / maxScore else 0.0f
    }
    
    private fun calculateCoverageScore(
        nodes: Map<String, NavNode>,
        areas: List<FloorPlanAnalyzer.NavigableArea>
    ): Float {
        var coveredAreas = 0
        
        areas.forEach { area ->
            val nearbyNodes = nodes.values.count { node ->
                val distance = sqrt(
                    (node.position.x - area.centerX).pow(2.0) +
                    (node.position.y - area.centerY).pow(2.0)
                )
                distance <= 5.0 // Within 5 meters
            }
            
            if (nearbyNodes > 0) coveredAreas++
        }
        
        return if (areas.isNotEmpty()) coveredAreas.toFloat() / areas.size else 0.0f
    }
    
    private fun calculateConnectivityScore(nodes: Map<String, NavNode>): Float {
        if (nodes.isEmpty()) return 0.0f
        
        val components = findConnectedComponents(nodes)
        val avgConnections = nodes.values.map { it.connections.size }.average()
        
        // Prefer single connected component with good connectivity
        val componentScore = if (components.size == 1) 1.0f else 0.5f / components.size
        val connectionScore = (avgConnections / 4.0).toFloat().coerceAtMost(1.0f)
        
        return (componentScore + connectionScore) / 2.0f
    }
    
    private fun calculateEfficiencyScore(nodes: Map<String, NavNode>): Float {
        // Optimal range: 20-50 nodes for a typical building
        val nodeCount = nodes.size
        return when {
            nodeCount in 20..50 -> 1.0f
            nodeCount < 20 -> nodeCount / 20.0f
            else -> 50.0f / nodeCount
        }
    }
}
