package com.example.indoornavigation.data

import kotlin.math.pow

// Data Models
data class BeaconInfo(
    val id: String,
    val name: String,
    val uuid: String,
    val major: Int,
    val minor: Int,
    val rssi: Int = -100,
    val distance: Float = 0f,
    val txPower: Int = -59,
    val isConnected: Boolean = false,
    val position: Pair<Float, Float>? = null, // X, Y coordinates
    val lastUpdated: Long = System.currentTimeMillis()
)

data class PositionData(
    val x: Float,
    val y: Float,
    val accuracy: Float,
    val timestamp: Long = System.currentTimeMillis(),
    val algorithm: String = "Trilateration" // Added algorithm field
)

data class TrilaterationState(
    val availableBeacons: List<BeaconInfo> = emptyList(),
    val connectedBeacons: List<BeaconInfo> = emptyList(),
    val userPosition: PositionData? = null,
    val isScanning: Boolean = false,
    val pathLossExponent: Float = 2.0f, // Width, Height in meters
    val floorPlanBounds: Pair<Float, Float> = Pair(10f, 10f),
    val smoothingFactor: Float = 0.2f, // For position smoothing
    val positioningMethod: PositioningMethod = PositioningMethod.FUSION // Default to fusion method
)

// Positioning methods enum
enum class PositioningMethod {
    TRILATERATION,
    WEIGHTED_CENTROID,
    KALMAN_FILTER,
    FINGERPRINTING,
    FUSION
}

// Kalman Filter state
data class KalmanFilterState(
    var x: Float = 0f, // Position X
    var y: Float = 0f, // Position Y 
    var vx: Float = 0f, // Velocity X
    var vy: Float = 0f, // Velocity Y
    var errorCovariance: FloatArray = FloatArray(16) { if (it % 5 == 0) 1f else 0f }, // 4x4 error covariance matrix
    var initialized: Boolean = false
) 

// Fingerprint data for simulation
data class FingerprintData(
    val location: Pair<Float, Float>,
    val signalPatterns: Map<String, Int> // Map of beacon IDs to RSSI values
)

// Trilateration Algorithm
object TrilaterationCalculator {
    // Existing calculation methods
    fun calculatePosition(beacons: List<BeaconInfo>): PositionData? {
        if (beacons.size < 3) return null
        
        try {
            // Use first 3 beacons for trilateration
            val b1 = beacons[0]
            val b2 = beacons[1]
            val b3 = beacons[2]
            
            val p1 = b1.position ?: return fallbackPosition(beacons)
            val p2 = b2.position ?: return fallbackPosition(beacons)
            val p3 = b3.position ?: return fallbackPosition(beacons)
            
            // Trilateration calculation
            val r1 = b1.distance.coerceAtLeast(0.1f)  // Ensure non-zero distance
            val r2 = b2.distance.coerceAtLeast(0.1f)
            val r3 = b3.distance.coerceAtLeast(0.1f)
            
            val A = 2 * p2.first - 2 * p1.first
            val B = 2 * p2.second - 2 * p1.second
            val C = r1 * r1 - r2 * r2 - p1.first * p1.first + p2.first * p2.first - p1.second * p1.second + p2.second * p2.second
            
            val D = 2 * p3.first - 2 * p2.first
            val E = 2 * p3.second - 2 * p2.second
            val F = r2 * r2 - r3 * r3 - p2.first * p2.first + p3.first * p3.first - p2.second * p2.second + p3.second * p3.second
            
            // Check for potential division by zero
            val denominator1 = E * A - B * D
            val denominator2 = B * D - A * E
            
            if (denominator1 == 0f || denominator2 == 0f) {
                return fallbackPosition(beacons)
            }
            
            val x = (C * E - F * B) / denominator1
            val y = (C * D - A * F) / denominator2
            
            // Check if the result is valid
            if (x.isNaN() || y.isNaN() || x.isInfinite() || y.isInfinite()) {
                return fallbackPosition(beacons)
            }
            
            // Calculate accuracy as average distance error
            val accuracy = beacons.map { it.distance }.average().toFloat()
            
            return PositionData(x, y, accuracy, algorithm = "Trilateration")
        } catch (e: Exception) {
            // If trilateration fails, use fallback
            return fallbackPosition(beacons)
        }
    }
    
    // Weighted Centroid method
    fun calculateWeightedCentroid(beacons: List<BeaconInfo>): PositionData? {
        // Need at least one beacon with known position
        val validBeacons = beacons.filter { it.position != null }
        if (validBeacons.isEmpty()) return null
        
        var sumX = 0f
        var sumY = 0f
        var sumWeights = 0f
        
        validBeacons.forEach { beacon ->
            // Calculate weight based on RSSI or distance
            // Higher RSSI (closer) = higher weight
            val signalWeight = 1f / (100f + beacon.rssi).pow(2) // Transform RSSI to weight
            
            beacon.position?.let {
                sumX += it.first * signalWeight
                sumY += it.second * signalWeight
                sumWeights += signalWeight
            }
        }
        
        // Prevent division by zero
        if (sumWeights == 0f) return null
        
        val x = sumX / sumWeights
        val y = sumY / sumWeights
        
        // Calculate accuracy as average distance
        val accuracy = validBeacons.map { it.distance }.average().toFloat()
        
        return PositionData(x, y, accuracy, algorithm = "Weighted Centroid")
    }
    
    // Kalman Filter implementation for position tracking
    fun applyKalmanFilter(
        previousState: KalmanFilterState,
        measurement: PositionData,
        deltaTime: Float // Time since last measurement in seconds
    ): PositionData {
        // System matrices for constant velocity model
        val dt = deltaTime.coerceAtLeast(0.1f)
        
        // Process noise (adjust based on expected movement dynamics)
        val processNoise = 0.01f
        
        // Measurement noise (adjust based on measurement confidence)
        val measurementNoise = 0.1f + measurement.accuracy * 0.1f
        
        // If this is the first measurement, initialize the filter
        if (!previousState.initialized) {
            previousState.x = measurement.x
            previousState.y = measurement.y
            previousState.vx = 0f
            previousState.vy = 0f
            previousState.initialized = true
            return measurement
        }
        
        // Prediction step
        val predictedX = previousState.x + previousState.vx * dt
        val predictedY = previousState.y + previousState.vy * dt
        
        // Update step - compute Kalman gain
        val kalmanGain = previousState.errorCovariance[0] / 
            (previousState.errorCovariance[0] + measurementNoise)
        
        // Update position based on measurement
        val newX = predictedX + kalmanGain * (measurement.x - predictedX)
        val newY = predictedY + kalmanGain * (measurement.y - predictedY)
        
        // Update velocity estimate based on position change
        val newVx = (newX - previousState.x) / dt
        val newVy = (newY - previousState.y) / dt
        
        // Update error covariance
        val newErrorCovariance = previousState.errorCovariance.clone()
        newErrorCovariance[0] = (1f - kalmanGain) * previousState.errorCovariance[0] + processNoise
        newErrorCovariance[5] = (1f - kalmanGain) * previousState.errorCovariance[5] + processNoise
        
        // Update state
        previousState.x = newX
        previousState.y = newY
        previousState.vx = newVx
        previousState.vy = newVy
        previousState.errorCovariance = newErrorCovariance
        
        return PositionData(
            x = newX,
            y = newY,
            accuracy = measurement.accuracy * (1 - kalmanGain),
            algorithm = "Kalman Filter"
        )
    }
    
    // Fingerprinting method (simulated for testing)
    fun calculateFingerprintPosition(
        beacons: List<BeaconInfo>,
        fingerprintDatabase: List<FingerprintData>
    ): PositionData? {
        if (beacons.isEmpty() || fingerprintDatabase.isEmpty()) return null
        
        // Create current signal pattern
        val currentPattern = beacons.associate { it.id to it.rssi }
        
        // Find best matching fingerprint
        var bestMatch: FingerprintData? = null
        var smallestDifference = Float.MAX_VALUE
        
        for (fingerprint in fingerprintDatabase) {
            var totalDifference = 0f
            var matchedSignals = 0
            
            // Compare RSSI values for each beacon
            fingerprint.signalPatterns.forEach { (beaconId, expectedRssi) ->
                val actualRssi = currentPattern[beaconId] ?: return@forEach
                
                // Calculate difference
                val difference = kotlin.math.abs(actualRssi - expectedRssi)
                totalDifference += difference
                matchedSignals++
            }
            
            // Skip if no matching signals
            if (matchedSignals == 0) continue
            
            // Calculate average difference
            val averageDifference = totalDifference / matchedSignals
            
            // Check if this is the best match so far
            if (averageDifference < smallestDifference) {
                smallestDifference = averageDifference
                bestMatch = fingerprint
            }
        }
        
        // Return position of best matching fingerprint
        return bestMatch?.let { 
            PositionData(
                x = it.location.first,
                y = it.location.second,
                // Accuracy based on how close the match was (smaller difference = better accuracy)
                accuracy = (smallestDifference / 10f).coerceAtLeast(0.5f),
                algorithm = "Fingerprinting"
            )
        }
    }
    
    // Sensor fusion method that combines results from all positioning methods
    fun calculateFusedPosition(
        beacons: List<BeaconInfo>,
        fingerprintDatabase: List<FingerprintData>,
        kalmanState: KalmanFilterState,
        lastPositionTimestamp: Long
    ): PositionData? {
        // Need at least one beacon with position data
        if (beacons.isEmpty() || beacons.all { it.position == null }) {
            return null
        }
        
        // Get positions from different methods
        val positions = mutableListOf<Pair<PositionData, Float>>()  // (position, weight)
        
        // 1. Trilateration (if we have 3+ beacons)
        if (beacons.size >= 3) {
            calculatePosition(beacons)?.let { trilaterationPos ->
                // Weight based on number of beacons and inverse of accuracy
                val weight = beacons.size * (1.0f / trilaterationPos.accuracy.coerceAtLeast(0.1f))
                positions.add(trilaterationPos to weight)
            }
        }
        
        // 2. Weighted Centroid (always possible with 1+ beacon)
        calculateWeightedCentroid(beacons)?.let { centroidPos ->
            // Weight based on number of beacons
            val weight = beacons.size * 0.7f
            positions.add(centroidPos to weight)
        }
        
        // 3. Fingerprinting (if we have a database)
        if (fingerprintDatabase.isNotEmpty()) {
            calculateFingerprintPosition(beacons, fingerprintDatabase)?.let { fingerprintPos ->
                // Weight based on match quality (inverse of accuracy)
                val weight = 5.0f * (1.0f / fingerprintPos.accuracy.coerceAtLeast(0.1f))
                positions.add(fingerprintPos to weight)
            }
        }
        
        // If we have no positions yet, we can't proceed
        if (positions.isEmpty()) {
            return null
        }
        
        // Calculate weighted average of all positions
        var sumX = 0f
        var sumY = 0f
        var sumWeights = 0f
        var sumWeightedAccuracy = 0f
        
        positions.forEach { (pos, weight) ->
            sumX += pos.x * weight
            sumY += pos.y * weight
            sumWeights += weight
            sumWeightedAccuracy += pos.accuracy * weight
        }
        
        // Create combined position
        val combinedPos = PositionData(
            x = sumX / sumWeights,
            y = sumY / sumWeights,
            // Average accuracy, weighted by position weights
            accuracy = sumWeightedAccuracy / sumWeights,
            algorithm = "Sensor Fusion"
        )
        
        // 4. Apply Kalman filtering to the combined position for temporal coherence
        val deltaTime = if (lastPositionTimestamp > 0) {
            (combinedPos.timestamp - lastPositionTimestamp) / 1000f
        } else {
            0.1f
        }
        
        return applyKalmanFilter(kalmanState, combinedPos, deltaTime)
    }
    
    private fun fallbackPosition(beacons: List<BeaconInfo>): PositionData? {
        // Only use beacons with positions
        val validBeacons = beacons.filter { it.position != null }
        if (validBeacons.isEmpty()) return null
        
        var sumX = 0f
        var sumY = 0f
        var sumWeights = 0f
        
        validBeacons.forEach { beacon ->
            // Use inverse of distance as weight (closer beacons have higher influence)
            val weight = 1f / beacon.distance.coerceAtLeast(0.1f)
            beacon.position?.let {
                sumX += it.first * weight
                sumY += it.second * weight
                sumWeights += weight
            }
        }
        
        // Prevent division by zero
        if (sumWeights == 0f) return null
        
        val x = sumX / sumWeights
        val y = sumY / sumWeights
        
        // Calculate accuracy as average distance
        val accuracy = validBeacons.map { it.distance }.average().toFloat()
        
        return PositionData(x, y, accuracy, algorithm = "Fallback")
    }
    
    fun calculateDistance(rssi: Int?, txPower: Int?, pathLossExponent: Float?): Float {
        // Apply RSSI filtering and smoothing
        val filteredRssi = rssi?.let { if (it < -100) -100 else if (it > 0) 0 else it } ?: -100
        val tx = txPower ?: -59
        val pathLoss = pathLossExponent?.coerceAtLeast(0.1f) ?: 0.1f
        
        return 10f.pow((tx - filteredRssi) / (10 * pathLoss))
    }
}

// Generate simulated fingerprint database for testing
object FingerprintDatabaseGenerator {
    fun generateDatabase(floorBounds: Pair<Float, Float>, beaconPositions: Map<String, Pair<Float, Float>>): List<FingerprintData> {
        val database = mutableListOf<FingerprintData>()
        val stepSize = 1.0f // 1 meter steps
        
        // Generate fingerprints in a grid pattern
        for (x in 0 until floorBounds.first.toInt() step stepSize.toInt()) {
            for (y in 0 until floorBounds.second.toInt() step stepSize.toInt()) {
                val location = Pair(x.toFloat(), y.toFloat())
                val signalPatterns = mutableMapOf<String, Int>()
                
                // Generate expected RSSI for each beacon
                beaconPositions.forEach { (beaconId, beaconPos) ->
                    // Calculate distance from fingerprint location to beacon
                    val dx = beaconPos.first - location.first
                    val dy = beaconPos.second - location.second
                    val distance = kotlin.math.sqrt(dx * dx + dy * dy)
                    
                    // Convert distance to RSSI using path loss model
                    val txPower = -59
                    val pathLoss = 2.0
                    val rssi = (txPower - 10 * pathLoss * kotlin.math.log10(distance.toDouble())).toInt()
                    
                    signalPatterns[beaconId] = rssi
                }
                
                database.add(FingerprintData(location, signalPatterns))
            }
        }
        
        return database
    }
}