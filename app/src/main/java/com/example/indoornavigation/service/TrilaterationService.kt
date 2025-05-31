package com.example.indoornavigation.service

import com.example.indoornavigation.data.models.ManagedBeacon
import com.example.indoornavigation.data.models.Position
import com.example.indoornavigation.data.models.TrilaterationResult
import org.apache.commons.math3.fitting.leastsquares.LevenbergMarquardtOptimizer
import org.apache.commons.math3.fitting.leastsquares.MultivariateJacobianFunction
import org.apache.commons.math3.linear.Array2DRowRealMatrix
import org.apache.commons.math3.linear.ArrayRealVector
import org.apache.commons.math3.linear.DecompositionSolver
import org.apache.commons.math3.linear.QRDecomposition
import org.apache.commons.math3.linear.RealMatrix
import org.apache.commons.math3.linear.RealVector
import org.apache.commons.math3.util.Pair
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Service for performing trilateration calculations to determine position
 */
class TrilaterationService {
    
    /**
     * Minimum number of beacons required for trilateration
     */
    private val MIN_BEACONS = 3
    
    /**
     * Maximum age of beacon data to be considered valid (in milliseconds)
     */
    private val MAX_BEACON_AGE_MS = 10000L
    
    /**
     * Calculate position using trilateration from 3+ beacons
     */
    fun calculatePosition(beacons: List<ManagedBeacon>): TrilaterationResult? {
        // Need at least 3 beacons with distances for trilateration
        val validBeacons = getValidBeacons(beacons)
        if (validBeacons.size < MIN_BEACONS) return null
        
        try {
            // Create arrays for trilateration
            val positions = Array(validBeacons.size) { DoubleArray(2) }
            val distances = DoubleArray(validBeacons.size)
            
            // Fill arrays with beacon data
            validBeacons.forEachIndexed { index, beacon ->
                positions[index][0] = beacon.x
                positions[index][1] = beacon.y
                distances[index] = beacon.lastDistance
            }
            
            // Calculate position using linear least squares
            val result = solveTrilateration(positions, distances)
            
            // Determine floor based on majority vote from beacons
            val floor = validBeacons
                .groupBy { it.floor }
                .maxByOrNull { it.value.size }?.key ?: 0
            
            // Calculate accuracy (residual error)
            val position = Position(result[0], result[1], floor)
            val accuracy = calculateAccuracy(validBeacons, position)
            
            return TrilaterationResult(
                position = position,
                accuracy = accuracy,
                usedBeacons = validBeacons
            )
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }
    
    /**
     * Filter beacons to only include those with valid distances and recent readings
     */
    private fun getValidBeacons(beacons: List<ManagedBeacon>): List<ManagedBeacon> {
        val now = System.currentTimeMillis()
        return beacons.filter { beacon ->
            // Must have a valid distance and be seen recently
            beacon.lastDistance > 0 && (now - beacon.lastSeen) < MAX_BEACON_AGE_MS
        }
    }
    
    /**
     * Solve the trilateration problem using linear least squares
     */
    private fun solveTrilateration(positions: Array<DoubleArray>, distances: DoubleArray): DoubleArray {
        val numberOfPositions = positions.size
        
        // Create matrices for least squares calculation
        val a = Array2DRowRealMatrix(numberOfPositions, 2)
        val b = ArrayRealVector(numberOfPositions)
        
        // Last point used as reference
        val lastPoint = positions[numberOfPositions - 1]
        val lastDistance = distances[numberOfPositions - 1]
        
        for (i in 0 until numberOfPositions - 1) {
            a.setEntry(i, 0, positions[i][0] - lastPoint[0])
            a.setEntry(i, 1, positions[i][1] - lastPoint[1])
            
            val bi = (
                positions[i][0].pow(2) - lastPoint[0].pow(2) +
                positions[i][1].pow(2) - lastPoint[1].pow(2) +
                lastDistance.pow(2) - distances[i].pow(2)
            ) / 2.0
            
            b.setEntry(i, bi)
        }
        
        // Solve the system
        val solver: DecompositionSolver = QRDecomposition(a).solver
        val solution = solver.solve(b)
        return solution.toArray()
    }
    
    /**
     * Calculate accuracy by comparing measured distances with calculated distances
     */
    fun calculateAccuracy(beacons: List<ManagedBeacon>, position: Position): Double {
        if (beacons.isEmpty()) return 0.0
        
        var sumSquaredResiduals = 0.0
        
        beacons.forEach { beacon ->
            // Calculate expected distance based on position
            val expectedDistance = calculateDistance(
                beacon.x, beacon.y, 
                position.x, position.y
            )
            
            // Calculate residual (difference between measured and expected)
            val residual = beacon.lastDistance - expectedDistance
            sumSquaredResiduals += residual.pow(2)
        }
        
        // Root mean squared error
        val rmse = sqrt(sumSquaredResiduals / beacons.size)
        
        // Cap at reasonable values (1-10 meters)
        return rmse.coerceIn(1.0, 10.0)
    }
    
    /**
     * Calculate Euclidean distance between two points
     */
    private fun calculateDistance(x1: Double, y1: Double, x2: Double, y2: Double): Double {
        val dx = x2 - x1
        val dy = y2 - y1
        return sqrt(dx.pow(2) + dy.pow(2))
    }
    
    /**
     * Convert RSSI to distance using log-distance path loss model
     */
    fun rssiToDistance(rssi: Int, txPower: Int = -59, pathLossExponent: Double = 2.0): Double {
        // Path loss model: d = 10^((TxPower - RSSI)/(10 * n))
        // where n is the path loss exponent
        return 10.0.pow((txPower - rssi) / (10.0 * pathLossExponent))
    }
}