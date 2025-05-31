package com.example.indoornavigation.utils

import android.content.Context
import com.example.indoornavigation.data.models.NavNode
import com.example.indoornavigation.data.models.Position
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileWriter

/**
 * Utility class for creating and managing floor plan overlays
 */
class MapOverlayTool(private val context: Context) {

    companion object {
        const val OVERLAY_FILE = "map_overlay.json"
    }

    /**
     * Points recorded during corridor tracing
     */
    private val corridorPoints = mutableListOf<Position>()
    
    /**
     * Nodes created from the corridor tracing
     */
    private val overlayNodes = mutableMapOf<String, NavNode>()
    
    /**
     * Currently active corridor name
     */
    private var activeCorridor: String? = null
    
    /**
     * Add a point to the current corridor
     */
    fun addCorridorPoint(position: Position) {
        if (activeCorridor != null) {
            corridorPoints.add(position)
            
            // Every point becomes a node for simplicity, but you may want to 
            // add logic to reduce the number of nodes, e.g., take one point every X pixels
            val nodeId = "node_${activeCorridor}_${corridorPoints.size}"
            val node = NavNode(nodeId, position, mutableListOf())
            
            // Connect to the previous node if one exists
            if (overlayNodes.isNotEmpty() && corridorPoints.size > 1) {
                val lastNodeId = "node_${activeCorridor}_${corridorPoints.size - 1}"
                val lastNode = overlayNodes[lastNodeId]
                
                if (lastNode != null) {
                    // Add bidirectional connection between nodes
                    lastNode.connections.add(nodeId)
                    node.connections.add(lastNodeId)
                }
            }
            
            overlayNodes[nodeId] = node
        }
    }
    
    /**
     * Start a new corridor trace
     */
    fun startCorridor(name: String) {
        // Finish any existing corridor
        finishCorridor()
        
        activeCorridor = name
    }
    
    /**
     * Finish the current corridor
     */
    fun finishCorridor() {
        activeCorridor = null
    }
    
    /**
     * Connect two nodes to create a path between different corridors
     */
    fun connectNodes(nodeId1: String, nodeId2: String) {
        val node1 = overlayNodes[nodeId1]
        val node2 = overlayNodes[nodeId2]
        
        if (node1 != null && node2 != null) {
            node1.connections.add(nodeId2)
            node2.connections.add(nodeId1)
        }
    }
    
    /**
     * Get a list of all overlay nodes
     */
    fun getOverlayNodes(): List<NavNode> {
        return overlayNodes.values.toList()
    }
    
    /**
     * Save the overlay to a file
     */
    suspend fun saveOverlay(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val file = File(context.filesDir, OVERLAY_FILE)
                val json = createOverlayJson()
                
                FileWriter(file).use { writer ->
                    writer.write(json.toString(2))
                }
                
                true
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }
    }
    
    /**
     * Load the overlay from a file
     */
    suspend fun loadOverlay(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val file = File(context.filesDir, OVERLAY_FILE)
                
                if (!file.exists()) {
                    return@withContext false
                }
                
                val jsonString = file.readText()
                parseOverlayJson(jsonString)
                
                true
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }
    }
    
    /**
     * Create a JSON representation of the overlay
     */
    private fun createOverlayJson(): JSONObject {
        val root = JSONObject()
        val nodesArray = JSONArray()
        
        // Add all nodes
        for (node in overlayNodes.values) {
            val nodeObject = JSONObject()
            nodeObject.put("id", node.id)
            nodeObject.put("x", node.position.x)
            nodeObject.put("y", node.position.y)
            nodeObject.put("floor", node.position.floor)
            
            val connectionsArray = JSONArray()
            for (connection in node.connections) {
                connectionsArray.put(connection)
            }
            
            nodeObject.put("connections", connectionsArray)
            nodesArray.put(nodeObject)
        }
        
        root.put("nodes", nodesArray)
        return root
    }
    
    /**
     * Parse a JSON representation of the overlay
     */
    private fun parseOverlayJson(jsonString: String) {
        overlayNodes.clear()
        
        val root = JSONObject(jsonString)
        val nodesArray = root.getJSONArray("nodes")
        
        // First pass: create all nodes
        for (i in 0 until nodesArray.length()) {
            val nodeObject = nodesArray.getJSONObject(i)
            val nodeId = nodeObject.getString("id")
            val nodeX = nodeObject.getDouble("x")
            val nodeY = nodeObject.getDouble("y")
            val nodeFloor = nodeObject.getInt("floor")
            
            val position = Position(nodeX, nodeY, nodeFloor)
            val node = NavNode(nodeId, position, mutableListOf())
            
            overlayNodes[nodeId] = node
        }
        
        // Second pass: set up connections between nodes
        for (i in 0 until nodesArray.length()) {
            val nodeObject = nodesArray.getJSONObject(i)
            val nodeId = nodeObject.getString("id")
            val connectionsArray = nodeObject.getJSONArray("connections")
            
            val node = overlayNodes[nodeId] ?: continue
            
            for (j in 0 until connectionsArray.length()) {
                val connectionId = connectionsArray.getString(j)
                node.connections.add(connectionId)
            }
        }
    }
    
    /**
     * Clear all overlay data
     */
    fun clearOverlay() {
        corridorPoints.clear()
        overlayNodes.clear()
        activeCorridor = null
    }
    
    /**
     * Get the code to paste into PositioningViewModel
     */
    fun getNodeCode(): String {
        val stringBuilder = StringBuilder()
        
        // Add node definitions
        for (node in overlayNodes.values) {
            val nodeCode = """
                val ${node.id} = NavNode("${node.id}", Position(${node.position.x}, ${node.position.y}, ${node.position.floor}), mutableListOf(
                    ${node.connections.joinToString(", ") { "\"$it\"" }}
                ))
            """.trimIndent()
            
            stringBuilder.append(nodeCode)
            stringBuilder.append("\n\n")
        }
        
        // Add nodes to map
        stringBuilder.append("// Add all nodes to map\n")
        for (nodeId in overlayNodes.keys) {
            stringBuilder.append("nodes[\"$nodeId\"] = $nodeId\n")
        }
        
        return stringBuilder.toString()
    }
}