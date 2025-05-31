package com.example.indoornavigation.navigation

import com.example.indoornavigation.data.models.FloorTransitionType
import com.example.indoornavigation.data.models.NavNodeConnection
import com.example.indoornavigation.data.models.NavNodeEnhanced
import com.example.indoornavigation.data.models.Position
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Calculator for navigation metrics like ETA and remaining distance
 */
class NavigationMetricsCalculator {
    companion object {
        // Average walking speed in meters per second
        private const val AVERAGE_WALKING_SPEED = 1.4f
        
        // Factors affecting speed on different surfaces
        private const val STAIR_SPEED_FACTOR = 0.7f  // Slower on stairs
        private const val ELEVATOR_WAIT_TIME = 15f   // Average wait in seconds
        private const val ESCALATOR_SPEED_FACTOR = 1.2f // Faster on escalators
    }
    
    /**
     * Calculate estimated time of arrival in seconds
     * @param path List of navigation nodes forming the path
     * @return Estimated time in seconds
     */
    fun calculateETASeconds(path: List<NavNodeEnhanced>): Float {
        var totalTime = 0f
        
        for (i in 0 until path.size - 1) {
            val current = path[i]
            val next = path[i + 1]
            
            // Find the connection between these nodes
            val connection = current.connections.find { it.targetNodeId == next.id }
            
            if (connection != null) {
                // Base time from distance
                var segmentTime = connection.distance / AVERAGE_WALKING_SPEED
                
                // Adjust for transition type
                if (connection.isFloorTransition) {
                    when (connection.transitionType) {
                        FloorTransitionType.STAIRS -> segmentTime /= STAIR_SPEED_FACTOR
                        FloorTransitionType.ELEVATOR -> segmentTime += ELEVATOR_WAIT_TIME
                        FloorTransitionType.ESCALATOR -> segmentTime *= ESCALATOR_SPEED_FACTOR
                        null -> { /* No adjustment */ }
                    }
                }
                
                totalTime += segmentTime
            } else {
                // If no direct connection found, calculate straight-line distance
                val distance = calculateDistance(current.position, next.position)
                totalTime += distance.toFloat() / AVERAGE_WALKING_SPEED
            }
        }
        
        return totalTime
    }
    
    /**
     * Format the ETA into a human-readable string
     * @param etaSeconds ETA in seconds
     * @return Formatted string like "5 min" or "2 min 30 sec"
     */
    fun formatETA(etaSeconds: Float): String {
        val minutes = (etaSeconds / 60).toInt()
        val seconds = (etaSeconds % 60).toInt()
        
        return when {
            minutes > 0 && seconds > 0 -> "$minutes min $seconds sec"
            minutes > 0 -> "$minutes min"
            else -> "$seconds sec"
        }
    }
    
    /**
     * Calculate remaining distance from current position to destination
     * @param path Full navigation path
     * @param currentPosition User's current position
     * @return Remaining distance in meters
     */
    fun calculateRemainingDistance(path: List<NavNodeEnhanced>, currentPosition: Position): Float {
        if (path.isEmpty()) return 0f
        
        // Find closest node on path
        val closestNodeIndex = findClosestNodeIndex(path, currentPosition)
        
        // Sum distances from current position to end
        var remainingDistance = 0f
        
        // Add distance to closest node first
        remainingDistance += calculateDistance(currentPosition, path[closestNodeIndex].position).toFloat()
        
        // Add remaining segments
        for (i in closestNodeIndex until path.size - 1) {
            val current = path[i]
            val next = path[i + 1]
            
            // Find connection between nodes
            val connection = current.connections.find { it.targetNodeId == next.id }
            
            if (connection != null) {
                remainingDistance += connection.distance
            } else {
                // If no direct connection found, calculate straight-line distance
                remainingDistance += calculateDistance(current.position, next.position).toFloat()
            }
        }
        
        return remainingDistance
    }
    
    /**
     * Format distance to a human-readable string
     * @param distanceMeters Distance in meters
     * @return Formatted string like "15 m" or "1.2 km"
     */
    fun formatDistance(distanceMeters: Float): String {
        return when {
            distanceMeters < 1000 -> "${distanceMeters.toInt()} m"
            else -> String.format("%.1f km", distanceMeters / 1000)
        }
    }
    
    /**
     * Find index of the closest node to current position
     * @param path List of nodes in the path
     * @param position Current position
     * @return Index of the closest node
     */
    private fun findClosestNodeIndex(path: List<NavNodeEnhanced>, position: Position): Int {
        if (path.isEmpty()) return 0
        
        // Check if we're before the first node
        val distanceToFirst = calculateDistance(position, path.first().position)
        val distanceToLast = calculateDistance(position, path.last().position)
        
        // If closer to destination than start, we're likely near the end
        if (distanceToLast < distanceToFirst && path.size > 1) {
            return path.size - 1
        }
        
        // Find closest node
        var closestIndex = 0
        var minDistance = Double.MAX_VALUE
        
        path.forEachIndexed { index, node ->
            val distance = calculateDistance(position, node.position)
            if (distance < minDistance) {
                minDistance = distance
                closestIndex = index
            }
        }
        
        // If we're at the last node, return it
        if (closestIndex == path.size - 1) {
            return closestIndex
        }
        
        // Now check if we're between this node and the next one
        if (closestIndex < path.size - 1) {
            val closestNode = path[closestIndex]
            val nextNode = path[closestIndex + 1]
            
            // Check if we've passed the closest node using dot product
            val directionVector = Vector2D(
                nextNode.position.x - closestNode.position.x,
                nextNode.position.y - closestNode.position.y
            )
            val positionVector = Vector2D(
                position.x - closestNode.position.x,
                position.y - closestNode.position.y
            )
            
            // If dot product is positive, we're past the closest node
            if (directionVector.dot(positionVector) > 0) {
                // Check if we're closer to next node
                if (calculateDistance(position, nextNode.position) < 
                    calculateDistance(position, closestNode.position)) {
                    return closestIndex + 1
                }
            }
        }
        
        return closestIndex
    }
    
    /**
     * Calculate Euclidean distance between two positions
     */
    private fun calculateDistance(p1: Position, p2: Position): Double {
        // Add floor penalty if on different floors
        val floorPenalty = if (p1.floor != p2.floor) 10.0 else 0.0
        
        val dx = p2.x - p1.x
        val dy = p2.y - p1.y
        
        return sqrt(dx.pow(2) + dy.pow(2)) + floorPenalty
    }
    
    /**
     * Simple 2D vector for dot product calculations
     */
    private data class Vector2D(val x: Double, val y: Double) {
        fun dot(other: Vector2D): Double {
            return x * other.x + y * other.y
        }
    }
}