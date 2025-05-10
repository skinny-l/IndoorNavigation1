package com.example.indoornavigation.utils

import com.example.indoornavigation.data.models.Position
import org.apache.commons.math3.linear.Array2DRowRealMatrix
import org.apache.commons.math3.linear.ArrayRealVector
import org.apache.commons.math3.linear.DecompositionSolver
import org.apache.commons.math3.linear.QRDecomposition

/**
 * Utility class for positioning algorithms and calculations
 */
object PositioningUtils {

    // Kalman filter parameters
    private const val PROCESS_NOISE = 0.01
    private const val MEASUREMENT_NOISE = 0.5
    private var kalmanGain = 0.0
    private var errorCovariance = 1.0
    
    /**
     * Calculate position using trilateration algorithm
     * Requires at least 3 known beacon positions and their distances
     */
    fun calculatePositionByTrilateration(
        beaconPositions: List<Position>,
        distances: List<Double>
    ): Position {
        // Need at least 3 beacons for trilateration
        if (beaconPositions.size < 3 || distances.size < 3) {
            throw IllegalArgumentException("At least 3 beacons are required for trilateration")
        }
        
        // Use the first beacon as reference point
        val p1 = beaconPositions[0]
        val p2 = beaconPositions[1]
        val p3 = beaconPositions[2]
        
        val r1 = distances[0]
        val r2 = distances[1]
        val r3 = distances[2]
        
        // Create a system of linear equations
        // Based on the formula: (x-x1)² + (y-y1)² = r1²
        // Rearranged to standard form: ax + by = c
        
        // For 2D trilateration (ignoring Z/floor)
        val a = Array2DRowRealMatrix(2, 2)
        val b = ArrayRealVector(2)
        
        // First equation: 2(x2-x1)x + 2(y2-y1)y = r1² - r2² - x1² - y1² + x2² + y2²
        a.setEntry(0, 0, 2 * (p2.x - p1.x))
        a.setEntry(0, 1, 2 * (p2.y - p1.y))
        b.setEntry(0, r1 * r1 - r2 * r2 - p1.x * p1.x - p1.y * p1.y + p2.x * p2.x + p2.y * p2.y)
        
        // Second equation: 2(x3-x1)x + 2(y3-y1)y = r1² - r3² - x1² - y1² + x3² + y3²
        a.setEntry(1, 0, 2 * (p3.x - p1.x))
        a.setEntry(1, 1, 2 * (p3.y - p1.y))
        b.setEntry(1, r1 * r1 - r3 * r3 - p1.x * p1.x - p1.y * p1.y + p3.x * p3.x + p3.y * p3.y)
        
        // Solve the system using QR decomposition
        val solver: DecompositionSolver = QRDecomposition(a).solver
        val solution = solver.solve(b)
        
        // Extract calculated x and y
        val x = solution.getEntry(0)
        val y = solution.getEntry(1)
        
        // Use the floor from the closest beacon
        val closestBeaconIndex = distances.indexOf(distances.minOrNull())
        val floor = beaconPositions[closestBeaconIndex].floor
        
        return Position(x, y, floor)
    }
    
    /**
     * Calculate position using weighted average
     * Useful when less than 3 beacons are available
     */
    fun calculatePositionByWeightedAverage(
        beaconPositions: List<Position>,
        weights: List<Double>
    ): Position {
        if (beaconPositions.isEmpty() || weights.isEmpty() || beaconPositions.size != weights.size) {
            throw IllegalArgumentException("Invalid inputs for weighted average calculation")
        }
        
        val totalWeight = weights.sum()
        
        var weightedX = 0.0
        var weightedY = 0.0
        var floorCounts = mutableMapOf<Int, Double>()
        
        // Calculate weighted position
        for (i in beaconPositions.indices) {
            val position = beaconPositions[i]
            val weight = weights[i]
            
            weightedX += position.x * weight
            weightedY += position.y * weight
            
            // Track floor votes by weight
            val currentFloorWeight = floorCounts.getOrDefault(position.floor, 0.0)
            floorCounts[position.floor] = currentFloorWeight + weight
        }
        
        // Calculate final position
        val x = weightedX / totalWeight
        val y = weightedY / totalWeight
        
        // Determine floor by max weight
        val floor = floorCounts.maxByOrNull { it.value }?.key ?: beaconPositions[0].floor
        
        return Position(x, y, floor)
    }
    
    /**
     * Apply Kalman filter for position smoothing
     */
    fun applyKalmanFilter(
        previousPosition: Position?,
        currentMeasurement: Position
    ): Position {
        // If this is the first measurement, return it directly
        if (previousPosition == null) {
            return currentMeasurement
        }
        
        // Prediction step
        // No state transition model needed for simple static positioning
        
        // Update error covariance
        errorCovariance = errorCovariance + PROCESS_NOISE
        
        // Update Kalman gain
        kalmanGain = errorCovariance / (errorCovariance + MEASUREMENT_NOISE)
        
        // Update position estimate
        val filteredX = previousPosition.x + kalmanGain * (currentMeasurement.x - previousPosition.x)
        val filteredY = previousPosition.y + kalmanGain * (currentMeasurement.y - previousPosition.y)
        
        // Update error covariance
        errorCovariance = (1 - kalmanGain) * errorCovariance
        
        // Use the floor from the current measurement
        // (floors are discrete so filtering doesn't make sense)
        return Position(filteredX, filteredY, currentMeasurement.floor)
    }
}