package com.example.indoornavigation.data.repositories

import android.content.Context
import com.example.indoornavigation.data.models.NavNodeEnhanced
import com.example.indoornavigation.data.models.Position
import com.example.indoornavigation.data.models.FloorTransitionType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Repository for navigation data
 */
class NavigationRepository private constructor(private val context: Context) {
    
    // Navigation graph
    private var navigationGraph = mutableMapOf<String, NavNodeEnhanced>()
    
    // Flag to track if navigation graph has been initialized
    private var isNavigationGraphInitialized = false
    
    companion object {
        @Volatile
        private var INSTANCE: NavigationRepository? = null
        
        fun getInstance(context: Context): NavigationRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: NavigationRepository(context.applicationContext).also {
                    INSTANCE = it
                }
            }
        }
    }
    
    /**
     * Get the navigation graph
     * Creates a default graph if not initialized yet
     */
    fun getNavigationGraph(): Map<String, NavNodeEnhanced> {
        if (!isNavigationGraphInitialized) {
            initializeNavigationGraph()
        }
        
        return navigationGraph
    }
    
    /**
     * Initialize the navigation graph
     * In a real implementation, this would load from a database or file
     */
    private fun initializeNavigationGraph() {
        // Create a sample navigation graph
        val node1 = NavNodeEnhanced(
            id = "node1",
            position = Position(10.0, 10.0, 0),
            floorId = 0,
            isTraversable = true
        )
        
        val node2 = NavNodeEnhanced(
            id = "node2",
            position = Position(20.0, 10.0, 0),
            floorId = 0,
            isTraversable = true
        )
        
        val node3 = NavNodeEnhanced(
            id = "node3",
            position = Position(20.0, 20.0, 0),
            floorId = 0,
            isTraversable = true
        )
        
        val node4 = NavNodeEnhanced(
            id = "node4",
            position = Position(20.0, 20.0, 1),
            floorId = 1,
            isTraversable = true
        )
        
        // Add connections
        node1.addConnection("node2", 10.0f)
        node2.addConnection("node1", 10.0f)
        node2.addConnection("node3", 10.0f)
        node3.addConnection("node2", 10.0f)
        node3.addConnection("node4", 5.0f, true, FloorTransitionType.STAIRS)
        node4.addConnection("node3", 5.0f, true, FloorTransitionType.STAIRS)
        
        // Add to graph
        navigationGraph["node1"] = node1
        navigationGraph["node2"] = node2
        navigationGraph["node3"] = node3
        navigationGraph["node4"] = node4
        
        isNavigationGraphInitialized = true
    }
    
    /**
     * Load navigation graph from a data source
     * Returns true if successful
     */
    suspend fun loadNavigationGraph(): Boolean = withContext(Dispatchers.IO) {
        try {
            // In a real implementation, this would load data from a file or database
            initializeNavigationGraph()
            true
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Save navigation graph to a data source
     * Returns true if successful
     */
    suspend fun saveNavigationGraph(): Boolean = withContext(Dispatchers.IO) {
        try {
            // In a real implementation, this would save to a file or database
            true
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Add a node to the navigation graph
     */
    fun addNode(node: NavNodeEnhanced) {
        navigationGraph[node.id] = node
    }
    
    /**
     * Remove a node from the navigation graph
     */
    fun removeNode(nodeId: String) {
        navigationGraph.remove(nodeId)
        
        // Remove connections to this node from other nodes
        for (node in navigationGraph.values) {
            node.removeConnection(nodeId)
        }
    }
}