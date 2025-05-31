package com.example.indoornavigation.utils

import kotlin.math.*

/**
 * Utility functions for location and distance calculations
 */
object LocationUtils {
    
    /**
     * Calculate distance between two geographic coordinates using Haversine formula
     * @param lat1 Latitude of first point
     * @param lon1 Longitude of first point  
     * @param lat2 Latitude of second point
     * @param lon2 Longitude of second point
     * @return Distance in meters
     */
    fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val earthRadius = 6371000.0 // Earth radius in meters
        
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)
        
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        
        return earthRadius * c
    }
    
    /**
     * Calculate bearing from one point to another
     * @param lat1 Latitude of first point
     * @param lon1 Longitude of first point
     * @param lat2 Latitude of second point  
     * @param lon2 Longitude of second point
     * @return Bearing in degrees (0-360)
     */
    fun calculateBearing(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLon = Math.toRadians(lon2 - lon1)
        val lat1Rad = Math.toRadians(lat1)
        val lat2Rad = Math.toRadians(lat2)
        
        val y = sin(dLon) * cos(lat2Rad)
        val x = cos(lat1Rad) * sin(lat2Rad) - sin(lat1Rad) * cos(lat2Rad) * cos(dLon)
        
        val bearing = Math.toDegrees(atan2(y, x))
        return (bearing + 360) % 360
    }
    
    /**
     * Format distance for display
     * @param meters Distance in meters
     * @return Formatted distance string
     */
    fun formatDistance(meters: Double): String {
        return when {
            meters < 1000 -> "${meters.toInt()}m"
            else -> "${"%.1f".format(meters / 1000)}km"
        }
    }
}