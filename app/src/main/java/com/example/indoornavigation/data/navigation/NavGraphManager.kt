package com.example.indoornavigation.data.navigation

import com.example.indoornavigation.data.models.NavNode
import com.example.indoornavigation.data.models.Position
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileWriter

/**
 * Manager class for handling navigation graph data
 */
class NavGraphManager {

    // In-memory store of navigation nodes
    private val navNodes = mutableMapOf<String, NavNode>()
    
    /**
     * Add a new navigation node to the graph
     * @return The created node
     */
    fun addNode(id: String, position: Position): NavNode {
        val node = NavNode(id, position)
        navNodes[id] = node
        return node
    }
    
    /**
     * Connect two nodes bidirectionally
     * @return true if the connection was created successfully
     */
    fun connectNodes(node1Id: String, node2Id: String): Boolean {
        val node1 = navNodes[node1Id] ?: return false
        val node2 = navNodes[node2Id] ?: return false
        
        // Add bidirectional connection
        if (!node1.connections.contains(node2Id)) {
            node1.connections.add(node2Id)
        }
        
        if (!node2.connections.contains(node1Id)) {
            node2.connections.add(node1Id)
        }
        
        return true
    }
    
    /**
     * Remove a connection between two nodes
     */
    fun removeConnection(node1Id: String, node2Id: String): Boolean {
        val node1 = navNodes[node1Id] ?: return false
        val node2 = navNodes[node2Id] ?: return false
        
        node1.connections.remove(node2Id)
        node2.connections.remove(node1Id)
        
        return true
    }
    
    /**
     * Remove a node and all its connections
     */
    fun removeNode(nodeId: String): Boolean {
        val node = navNodes[nodeId] ?: return false
        
        // Remove all connections to this node
        node.connections.forEach { connectedId ->
            navNodes[connectedId]?.connections?.remove(nodeId)
        }
        
        // Remove the node
        navNodes.remove(nodeId)
        
        return true
    }
    
    /**
     * Get all nodes in the navigation graph
     */
    fun getAllNodes(): Map<String, NavNode> {
        return navNodes.toMap()
    }
    
    /**
     * Get a specific node by ID
     */
    fun getNode(nodeId: String): NavNode? {
        return navNodes[nodeId]
    }
    
    /**
     * Clear the entire navigation graph
     */
    fun clearGraph() {
        navNodes.clear()
    }
    
    /**
     * Export the navigation graph to JSON
     * @return JSON string representation of the graph
     */
    fun exportToJson(): String {
        val jsonRoot = JSONObject()
        val nodesArray = JSONArray()
        
        // Add all nodes
        navNodes.values.forEach { node ->
            val nodeObj = JSONObject()
            nodeObj.put("id", node.id)
            nodeObj.put("x", node.position.x)
            nodeObj.put("y", node.position.y)
            nodeObj.put("floor", node.position.floor)
            
            // Add connections
            val connectionsArray = JSONArray()
            node.connections.forEach { connectionsArray.put(it) }
            nodeObj.put("connections", connectionsArray)
            
            nodesArray.put(nodeObj)
        }
        
        jsonRoot.put("nodes", nodesArray)
        return jsonRoot.toString(2) // Pretty print with 2-space indent
    }
    
    /**
     * Save the navigation graph to a file
     * @param file The file to save to
     * @return true if successful
     */
    suspend fun saveToFile(file: File): Boolean = withContext(Dispatchers.IO) {
        try {
            FileWriter(file).use { writer ->
                writer.write(exportToJson())
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
    
    /**
     * Import a navigation graph from JSON
     * @param jsonString The JSON string to import
     * @return true if successful
     */
    fun importFromJson(jsonString: String): Boolean {
        try {
            // Clear existing graph
            clearGraph()
            
            val jsonRoot = JSONObject(jsonString)
            val nodesArray = jsonRoot.getJSONArray("nodes")
            
            // First pass: Create all nodes
            for (i in 0 until nodesArray.length()) {
                val nodeObj = nodesArray.getJSONObject(i)
                val id = nodeObj.getString("id")
                val x = nodeObj.getDouble("x")
                val y = nodeObj.getDouble("y")
                val floor = nodeObj.getInt("floor")
                
                addNode(id, Position(x, y, floor))
            }
            
            // Second pass: Create connections
            for (i in 0 until nodesArray.length()) {
                val nodeObj = nodesArray.getJSONObject(i)
                val id = nodeObj.getString("id")
                val connectionsArray = nodeObj.getJSONArray("connections")
                
                // Get the node
                val node = navNodes[id] ?: continue
                
                // Add connections
                for (j in 0 until connectionsArray.length()) {
                    val connectedId = connectionsArray.getString(j)
                    if (navNodes.containsKey(connectedId) && !node.connections.contains(connectedId)) {
                        node.connections.add(connectedId)
                    }
                }
            }
            
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }
    
    /**
     * Load a navigation graph from a file
     * @param file The file to load from
     * @return true if successful
     */
    suspend fun loadFromFile(file: File): Boolean = withContext(Dispatchers.IO) {
        try {
            val jsonString = file.readText()
            importFromJson(jsonString)
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
    
    /**
     * Create a simple grid of navigation nodes
     * Useful for testing or initializing a basic navigation graph
     * @param rows Number of rows in the grid
     * @param cols Number of columns in the grid
     * @param floor The floor level
     * @param width Real-world width in meters
     * @param height Real-world height in meters
     */
    fun createGridGraph(rows: Int, cols: Int, floor: Int, width: Double, height: Double) {
        // Clear existing graph
        clearGraph()
        
        // Calculate spacing
        val rowSpacing = height / (rows - 1)
        val colSpacing = width / (cols - 1)
        
        // Create nodes
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                val id = "node_${floor}_${r}_${c}"
                val x = c * colSpacing
                val y = r * rowSpacing
                addNode(id, Position(x, y, floor))
            }
        }
        
        // Connect nodes
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                val currentId = "node_${floor}_${r}_${c}"
                
                // Connect to right neighbor
                if (c < cols - 1) {
                    val rightId = "node_${floor}_${r}_${c + 1}"
                    connectNodes(currentId, rightId)
                }
                
                // Connect to bottom neighbor
                if (r < rows - 1) {
                    val bottomId = "node_${floor}_${r + 1}_${c}"
                    connectNodes(currentId, bottomId)
                }
            }
        }
    }
    
    /**
     * Find the closest node to a given position
     * @param position The position to find the closest node to
     * @return The closest node, or null if no nodes exist
     */
    fun findClosestNode(position: Position): NavNode? {
        if (navNodes.isEmpty()) return null
        
        return navNodes.values.minByOrNull { 
            it.position.distanceTo(position)
        }
    }
}