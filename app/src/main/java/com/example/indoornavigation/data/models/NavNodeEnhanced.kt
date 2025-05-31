package com.example.indoornavigation.data.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Represents a connection between navigation nodes
 * Contains metadata about the connection such as distance and transition type
 */
@Parcelize
data class NavNodeConnection(
    val targetNodeId: String,
    val distance: Float,
    val isFloorTransition: Boolean = false,
    val transitionType: FloorTransitionType? = null
) : Parcelable

/**
 * Types of floor transitions available
 */
enum class FloorTransitionType {
    STAIRS, 
    ELEVATOR, 
    ESCALATOR
}

/**
 * Enhanced node in the navigation graph that handles obstacles and floor transitions
 */
@Parcelize
data class NavNodeEnhanced(
    val id: String,
    val position: Position,
    val isTraversable: Boolean = true,
    val floorId: Int,
    val connections: MutableList<NavNodeConnection> = mutableListOf()
) : Parcelable {
    
    /**
     * Add a connection to another node
     * @param targetNodeId ID of the connected node
     * @param distance Distance between nodes
     * @param isFloorTransition Whether this connection crosses floors
     * @param transitionType Type of floor transition (if applicable)
     */
    fun addConnection(
        targetNodeId: String, 
        distance: Float,
        isFloorTransition: Boolean = false,
        transitionType: FloorTransitionType? = null
    ) {
        connections.add(NavNodeConnection(
            targetNodeId = targetNodeId,
            distance = distance,
            isFloorTransition = isFloorTransition,
            transitionType = transitionType
        ))
    }
    
    /**
     * Remove a connection to another node
     * @param targetNodeId ID of the node to disconnect
     * @return true if the connection was found and removed
     */
    fun removeConnection(targetNodeId: String): Boolean {
        val initial = connections.size
        connections.removeIf { it.targetNodeId == targetNodeId }
        return connections.size < initial
    }
    
    /**
     * Check if this node is connected to another node
     * @param targetNodeId ID of the node to check
     * @return true if there is a connection
     */
    fun isConnectedTo(targetNodeId: String): Boolean {
        return connections.any { it.targetNodeId == targetNodeId }
    }
    
    /**
     * Get all connection node IDs
     * @return List of connected node IDs
     */
    fun getConnectedNodeIds(): List<String> {
        return connections.map { it.targetNodeId }
    }
    
    /**
     * Get floor transition connections
     * @return List of connections that are floor transitions
     */
    fun getFloorTransitions(): List<NavNodeConnection> {
        return connections.filter { it.isFloorTransition }
    }
}