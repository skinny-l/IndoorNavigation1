package com.example.indoornavigation.data.wifi

import com.example.indoornavigation.data.models.Position
import com.example.indoornavigation.data.models.WiFiFingerprint
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Estimates user position using WiFi fingerprinting and k-nearest neighbors algorithm
 */
class WiFiPositionEstimator(private val fingerprintDatabase: List<WiFiFingerprint>) {
    
    /**
     * Estimate position based on current WiFi scan
     * @param currentScan Map of BSSID to RSSI values from current scan
     * @return Estimated Position or null if estimation failed
     */
    fun estimatePosition(currentScan: Map<String, Int>): Position? {
        if (fingerprintDatabase.isEmpty()) {
            return null
        }
        
        // Find k-nearest fingerprints based on signal distance
        val sortedFingerprints = fingerprintDatabase.sortedBy { fingerprint ->
            calculateEuclideanDistance(currentScan, fingerprint.signalMap)
        }
        
        // Use the closest fingerprint if available
        if (sortedFingerprints.isNotEmpty()) {
            return getPositionFromLocationId(sortedFingerprints.first().locationId)
        }
        
        return null
    }
    
    /**
     * Estimate position using k-nearest neighbors with weighted averaging
     * @param currentScan Map of BSSID to RSSI values from current scan
     * @param k Number of nearest neighbors to consider
     * @return Estimated Position or null if estimation failed
     */
    fun estimatePositionKnn(currentScan: Map<String, Int>, k: Int = 3): Position? {
        if (fingerprintDatabase.isEmpty() || k <= 0) {
            return null
        }
        
        // Finding k nearest fingerprints
        val sortedFingerprints = fingerprintDatabase.sortedBy { fingerprint ->
            calculateEuclideanDistance(currentScan, fingerprint.signalMap)
        }
        
        val kNearestFingerprints = sortedFingerprints.take(k.coerceAtMost(sortedFingerprints.size))
        
        // Calculate weighted position
        if (kNearestFingerprints.isNotEmpty()) {
            var sumX = 0.0
            var sumY = 0.0
            var sumFloor = 0.0
            var sumWeights = 0.0
            
            for (fingerprint in kNearestFingerprints) {
                val position = getPositionFromLocationId(fingerprint.locationId) ?: continue
                val distance = calculateEuclideanDistance(currentScan, fingerprint.signalMap)
                
                // Weight is inversely proportional to distance
                val weight = if (distance < 0.1) 10.0 else 1.0 / distance
                
                sumX += position.x * weight
                sumY += position.y * weight
                sumFloor += position.floor * weight
                sumWeights += weight
            }
            
            if (sumWeights > 0) {
                return Position(
                    x = sumX / sumWeights,
                    y = sumY / sumWeights,
                    floor = (sumFloor / sumWeights).toInt()
                )
            }
        }
        
        return null
    }
    
    /**
     * Calculate Euclidean distance between two signal strength maps
     * Only considers APs that are present in both scans
     */
    private fun calculateEuclideanDistance(scan1: Map<String, Int>, scan2: Map<String, Int>): Double {
        var sumOfSquares = 0.0
        var numCommonAPs = 0
        
        // First, consider BSSIDs that appear in both scans
        for ((bssid, rssi1) in scan1) {
            val rssi2 = scan2[bssid]
            if (rssi2 != null) {
                sumOfSquares += (rssi1 - rssi2).toDouble().pow(2)
                numCommonAPs++
            }
        }
        
        // If no common APs, use a penalty approach
        if (numCommonAPs == 0) {
            // Apply a high distance if no common APs
            return 1000.0
        }
        
        return sqrt(sumOfSquares / numCommonAPs)
    }
    
    /**
     * Retrieve position information for a location ID
     * In a real implementation, this would query a database
     */
    private fun getPositionFromLocationId(locationId: String): Position? {
        // Mock implementation - in a real app, this would retrieve from a database
        // Format: "location_x_y_floor" e.g. "location_10_15_2"
        try {
            val parts = locationId.split("_")
            if (parts.size >= 4) {
                val x = parts[1].toDouble()
                val y = parts[2].toDouble()
                val floor = parts[3].toInt()
                return Position(x, y, floor)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        return null
    }
}