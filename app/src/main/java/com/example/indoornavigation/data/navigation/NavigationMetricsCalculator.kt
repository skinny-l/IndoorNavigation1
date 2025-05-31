package com.example.indoornavigation.data.navigation

import com.example.indoornavigation.data.models.Position
import kotlin.math.sqrt

/**
 * Utility for calculating navigation metrics like distance and ETA
 */
class NavigationMetricsCalculator {
    
    // Average walking speed in meters per second
    private val averageWalkingSpeed = 1.4f
    
    /**
     * Calculate remaining distance to destination
     * 
     * @param path Path waypoints
     * @param currentPosition Current user position
     * @return Remaining distance in meters
     */
    fun calculateRemainingDistance(path: List<Position>, currentPosition: Position): Float {
        if (path.isEmpty()) return 0f
        
        // Find the closest point in the path
        val (closestPointIndex, _) = findClosestPointInPath(path, currentPosition)
        
        // Calculate distance from that point to the end of the path
        var distance = 0f
        
        for (i in closestPointIndex until path.size - 1) {
            val p1 = path[i]
            val p2 = path[i + 1]
            
            // Only calculate distance on the same floor or add penalty
            if (p1.floor == p2.floor) {
                val dx = p2.x - p1.x
                val dy = p2.y - p1.y
                distance += sqrt(dx * dx + dy * dy).toFloat()
            } else {
                // Add floor transition penalty (typical stair/elevator time equivalent)
                distance += 20f
            }
        }
        
        return distance
    }
    
    /**
     * Calculate estimated time of arrival in seconds
     * 
     * @param path Path waypoints
     * @return Estimated time in seconds
     */
    fun calculateETASeconds(path: List<Position>): Float {
        if (path.isEmpty()) return 0f
        
        // Calculate total distance
        var distance = 0f
        var floorTransitions = 0
        
        for (i in 0 until path.size - 1) {
            val p1 = path[i]
            val p2 = path[i + 1]
            
            // Count floor transitions
            if (p1.floor != p2.floor) {
                floorTransitions++
            }
            
            // Calculate distance on the same floor
            val dx = p2.x - p1.x
            val dy = p2.y - p1.y
            distance += sqrt(dx * dx + dy * dy).toFloat()
        }
        
        // Calculate time based on distance and average walking speed
        // Add extra time for floor transitions (typically 15-30 seconds per transition)
        val walkingTimeSeconds = distance / averageWalkingSpeed
        val floorTransitionTimeSeconds = floorTransitions * 20f // 20 seconds per transition
        
        return walkingTimeSeconds + floorTransitionTimeSeconds
    }
    
    /**
     * Find closest point in the path to the current position
     * 
     * @param path Path waypoints
     * @param position Current position
     * @return Pair of (index, distance) of the closest point
     */
    private fun findClosestPointInPath(path: List<Position>, position: Position): Pair<Int, Float> {
        var closestDistance = Float.MAX_VALUE
        var closestIndex = 0
        
        // Find the closest point in the path
        for (i in path.indices) {
            val point = path[i]
            
            // Only consider points on the same floor
            if (point.floor == position.floor) {
                val dx = point.x - position.x
                val dy = point.y - position.y
                val distance = sqrt(dx * dx + dy * dy).toFloat()
                
                if (distance < closestDistance) {
                    closestDistance = distance
                    closestIndex = i
                }
            }
        }
        
        // If we couldn't find a point on the same floor, just use the first point
        if (closestDistance == Float.MAX_VALUE && path.isNotEmpty()) {
            closestIndex = 0
            val point = path[0]
            val dx = point.x - position.x
            val dy = point.y - position.y
            closestDistance = sqrt(dx * dx + dy * dy).toFloat()
        }
        
        return Pair(closestIndex, closestDistance)
    }
}