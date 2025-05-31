package com.example.indoornavigation.testing

import android.content.Context
import android.util.Log
import com.example.indoornavigation.data.models.Position
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * A framework for testing the accuracy of different positioning methods
 * Records and analyzes the difference between ground truth and estimated positions
 */
class PositioningAccuracyTester(private val context: Context) {
    private val TAG = "PositioningAccuracyTester"
    
    // Map of locations to their ground truth positions
    private val groundTruthPositions = mutableMapOf<String, Position>()
    
    // Collection of test results
    private val testResults = mutableListOf<PositionTestResult>()
    
    // Current test results as state flow for observation
    private val _currentResults = MutableStateFlow<List<PositionTestResult>>(emptyList())
    val currentResults: StateFlow<List<PositionTestResult>> = _currentResults.asStateFlow()
    
    /**
     * Represents a single positioning test result
     */
    data class PositionTestResult(
        val locationId: String,
        val timestamp: Long,
        val groundTruth: Position,
        val estimatedPosition: Position,
        val error: Float,
        val positionSource: String
    )
    
    /**
     * Set the ground truth position for a specific location
     * 
     * @param locationId Identifier for the location (e.g., "lobby", "room101")
     * @param position The known accurate position for this location
     */
    fun setGroundTruth(locationId: String, position: Position) {
        groundTruthPositions[locationId] = position
        Log.d(TAG, "Set ground truth for $locationId: $position")
    }
    
    /**
     * Run a positioning test at a specified location using a specific positioning method
     * 
     * @param locationId The identifier for the location being tested
     * @param positionSource Identifier for the positioning method (e.g., "BEACON", "WIFI", "FUSION")
     * @param estimatedPosition The position estimate from the positioning system
     * @return The calculated error (or null if ground truth not available)
     */
    fun runPositioningTest(locationId: String, positionSource: String, estimatedPosition: Position): Float? {
        val groundTruth = groundTruthPositions[locationId] ?: run {
            Log.w(TAG, "No ground truth available for location: $locationId")
            return null
        }
        
        // Calculate error between ground truth and estimated position
        val error = calculateDistance(groundTruth, estimatedPosition)
        
        // Create and store the test result
        val result = PositionTestResult(
            locationId = locationId,
            timestamp = System.currentTimeMillis(),
            groundTruth = groundTruth,
            estimatedPosition = estimatedPosition,
            error = error,
            positionSource = positionSource
        )
        
        testResults.add(result)
        
        // Update the current results flow
        _currentResults.value = testResults.toList()
        
        Log.d(TAG, "Test for $locationId using $positionSource: error=$error meters")
        return error
    }
    
    /**
     * Get the average error for a specific positioning method
     * 
     * @param positionSource The positioning method to analyze, or null for all methods
     * @return The average error in meters
     */
    fun getAverageError(positionSource: String? = null): Float {
        val filteredResults = positionSource?.let {
            testResults.filter { it.positionSource == positionSource }
        } ?: testResults
        
        if (filteredResults.isEmpty()) return 0f
        
        return filteredResults.map { it.error }.average().toFloat()
    }
    
    /**
     * Get detailed error statistics for all positioning methods
     * 
     * @return Map of positioning method to array of [mean error, min error, max error, standard deviation]
     */
    fun getErrorStats(): Map<String, FloatArray> {
        // Group results by positioning source
        return testResults.groupBy { it.positionSource }
            .mapValues { (_, results) ->
                val errors = results.map { it.error }
                
                if (errors.isEmpty()) {
                    floatArrayOf(0f, 0f, 0f, 0f)
                } else {
                    floatArrayOf(
                        errors.average().toFloat(),     // Mean error
                        errors.minOrNull() ?: 0f,       // Min error
                        errors.maxOrNull() ?: 0f,       // Max error
                        calculateStdDev(errors)         // Standard deviation
                    )
                }
            }
    }
    
    /**
     * Calculate the standard deviation of a list of values
     */
    private fun calculateStdDev(values: List<Float>): Float {
        if (values.size <= 1) return 0f
        
        val mean = values.average()
        val variance = values.map { (it - mean).pow(2) }.average()
        return sqrt(variance).toFloat()
    }
    
    /**
     * Calculate distance between two positions
     */
    private fun calculateDistance(p1: Position, p2: Position): Float {
        // If positions are on different floors, add a floor penalty
        val floorPenalty = if (p1.floor != p2.floor) {
            Math.abs(p1.floor - p2.floor) * 4.0
        } else {
            0.0
        }
        
        // Calculate 2D distance
        val dx = p2.x - p1.x
        val dy = p2.y - p1.y
        
        return (sqrt(dx * dx + dy * dy) + floorPenalty).toFloat()
    }
    
    /**
     * Get test results for a specific location
     * 
     * @param locationId The location identifier
     * @return List of test results for that location
     */
    fun getResultsForLocation(locationId: String): List<PositionTestResult> {
        return testResults.filter { it.locationId == locationId }
    }
    
    /**
     * Get test results for a specific positioning method
     * 
     * @param positionSource The positioning method identifier
     * @return List of test results for that method
     */
    fun getResultsForMethod(positionSource: String): List<PositionTestResult> {
        return testResults.filter { it.positionSource == positionSource }
    }
    
    /**
     * Clear all test results
     */
    fun clearResults() {
        testResults.clear()
        _currentResults.value = emptyList()
        Log.d(TAG, "Cleared all test results")
    }
    
    /**
     * Export test results as CSV format string
     */
    fun exportResultsAsCsv(): String {
        val sb = StringBuilder()
        // Header
        sb.appendLine("Location,Timestamp,GroundTruth_X,GroundTruth_Y,GroundTruth_Floor,Estimated_X,Estimated_Y,Estimated_Floor,Error,PositionSource")
        
        // Data rows
        for (result in testResults) {
            sb.appendLine("${result.locationId},${result.timestamp},${result.groundTruth.x},${result.groundTruth.y},${result.groundTruth.floor}," +
                    "${result.estimatedPosition.x},${result.estimatedPosition.y},${result.estimatedPosition.floor}," +
                    "${result.error},${result.positionSource}")
        }
        
        return sb.toString()
    }
    
    /**
     * Get summary of test results
     */
    fun getResultSummary(): String {
        val stats = getErrorStats()
        val sb = StringBuilder()
        
        sb.appendLine("=== Positioning Accuracy Test Summary ===")
        sb.appendLine("Total tests: ${testResults.size}")
        sb.appendLine("Locations tested: ${testResults.map { it.locationId }.distinct().size}")
        sb.appendLine()
        
        stats.forEach { (source, data) ->
            sb.appendLine("Method: $source")
            sb.appendLine("  - Average error: ${String.format("%.2f", data[0])} m")
            sb.appendLine("  - Min error: ${String.format("%.2f", data[1])} m")
            sb.appendLine("  - Max error: ${String.format("%.2f", data[2])} m")
            sb.appendLine("  - Std deviation: ${String.format("%.2f", data[3])} m")
            sb.appendLine()
        }
        
        return sb.toString()
    }
}