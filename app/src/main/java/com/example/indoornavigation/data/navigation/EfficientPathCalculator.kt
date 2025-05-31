package com.example.indoornavigation.data.navigation

import com.example.indoornavigation.data.models.NavNodeEnhanced
import com.example.indoornavigation.data.models.Position

/**
 * Efficient path calculator that implements caching for navigation paths
 * to avoid unnecessary recalculations
 */
class EfficientPathCalculator(private val navigationService: EnhancedNavigationService) {
    
    // Cache for last calculated path
    private var lastCalculatedPath: List<NavNodeEnhanced> = emptyList()
    private var pathStartNodeId: String? = null
    private var pathEndNodeId: String? = null
    
    /**
     * Calculate path with caching
     * 
     * @param startNodeId ID of the starting node
     * @param endNodeId ID of the destination node
     * @return List of NavNodes representing the path
     */
    fun calculatePath(startNodeId: String, endNodeId: String): List<NavNodeEnhanced> {
        // Return cached path if available and unchanged
        if (startNodeId == pathStartNodeId && endNodeId == pathEndNodeId && lastCalculatedPath.isNotEmpty()) {
            return lastCalculatedPath
        }
        
        // Calculate new path
        val newPath = navigationService.findPath(startNodeId, endNodeId)
        
        // Cache results if valid
        if (newPath.isNotEmpty()) {
            lastCalculatedPath = newPath
            pathStartNodeId = startNodeId
            pathEndNodeId = endNodeId
        }
        
        return newPath
    }
    
    /**
     * Recalculate path from a new position but reuse existing path if possible
     * Useful for rerouting when user deviates from path
     * 
     * @param currentPosition Current user position
     * @param endNodeId ID of the destination node
     * @return Recalculated path
     */
    fun recalculateFromPosition(currentPosition: Position, endNodeId: String): List<NavNodeEnhanced> {
        // Find closest node to current position
        val closestNode = navigationService.findClosestNode(currentPosition) ?: return emptyList()
        
        // Check if we're already close to a node in existing path
        val existingPathIndex = lastCalculatedPath.indexOfFirst { 
            it.id == closestNode.id 
        }
        
        // If found a matching node in the existing path, reuse the remainder
        if (existingPathIndex >= 0 && existingPathIndex < lastCalculatedPath.size - 1) {
            // We can reuse part of existing path
            return lastCalculatedPath.subList(existingPathIndex, lastCalculatedPath.size)
        }
        
        // Calculate completely new path
        return calculatePath(closestNode.id, endNodeId)
    }
    
    /**
     * Invalidate the path cache
     * Should be called when the navigation graph changes
     */
    fun invalidateCache() {
        lastCalculatedPath = emptyList()
        pathStartNodeId = null
        pathEndNodeId = null
    }
    
    /**
     * Clear existing path cache
     */
    fun clear() {
        invalidateCache()
    }
}