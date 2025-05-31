package com.example.indoornavigation.testing

import android.util.Log
import com.example.indoornavigation.data.models.Position
import com.example.indoornavigation.data.models.ComparativeTestResult
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import java.util.UUID
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * A/B Testing tool for comparing different positioning algorithms
 */
class AlgorithmComparator {

    private val TAG = "AlgorithmComparator"
    
    // Firestore for storing test results
    private val db = FirebaseFirestore.getInstance()
    
    // Active test data
    private var isTestActive = false
    private var testStartTime = 0L
    private var testId = ""
    private var testName = ""
    private var groundTruthPosition: Position? = null
    
    // Algorithm IDs
    private var algorithmAId = ""
    private var algorithmBId = ""
    
    // Test measurements for each algorithm
    private val measurementsA = mutableListOf<MeasurementPair>()
    private val measurementsB = mutableListOf<MeasurementPair>()
    
    // Test statistics for algorithm A
    private var totalErrorA = 0.0
    private var maxErrorA = 0.0
    private var measurementCountA = 0
    
    // Test statistics for algorithm B
    private var totalErrorB = 0.0
    private var maxErrorB = 0.0
    private var measurementCountB = 0
    
    // Test result
    private val _testResult = MutableStateFlow<ComparativeTestResult?>(null)
    val testResult: StateFlow<ComparativeTestResult?> = _testResult.asStateFlow()
    
    /**
     * Start a new A/B test
     */
    fun startABTest(
        name: String, 
        groundTruthPosition: Position,
        algorithmAId: String,
        algorithmBId: String
    ): Boolean {
        if (isTestActive) {
            Log.e(TAG, "Test already active")
            return false
        }
        
        // Initialize test
        testId = UUID.randomUUID().toString()
        testName = name
        this.groundTruthPosition = groundTruthPosition
        this.algorithmAId = algorithmAId
        this.algorithmBId = algorithmBId
        testStartTime = System.currentTimeMillis()
        
        // Reset measurements
        measurementsA.clear()
        measurementsB.clear()
        
        // Reset statistics
        totalErrorA = 0.0
        maxErrorA = 0.0
        measurementCountA = 0
        
        totalErrorB = 0.0
        maxErrorB = 0.0
        measurementCountB = 0
        
        isTestActive = true
        Log.d(TAG, "A/B Test started: $testName (A: $algorithmAId, B: $algorithmBId)")
        
        return true
    }
    
    /**
     * Update the ground truth position during a test
     * Used for walking tests where the reference position changes
     */
    fun updateGroundTruth(newPosition: Position): Boolean {
        if (!isTestActive) {
            Log.e(TAG, "No active test to update ground truth")
            return false
        }
        
        groundTruthPosition = newPosition
        Log.d(TAG, "Ground truth updated: x=${newPosition.x}, y=${newPosition.y}, floor=${newPosition.floor}")
        
        return true
    }
    
    /**
     * Record a position measurement from an algorithm
     */
    fun recordMeasurement(estimatedPosition: Position, algorithmId: String): Double {
        if (!isTestActive || groundTruthPosition == null) {
            Log.e(TAG, "No active test")
            return 0.0
        }
        
        if (algorithmId != algorithmAId && algorithmId != algorithmBId) {
            Log.e(TAG, "Unknown algorithm ID: $algorithmId")
            return 0.0
        }
        
        // Calculate error
        val error = calculateDistance(groundTruthPosition!!, estimatedPosition)
        
        // Create measurement
        val measurement = MeasurementPair(
            timestamp = System.currentTimeMillis(),
            groundTruth = groundTruthPosition!!,
            estimated = estimatedPosition,
            error = error,
            algorithmId = algorithmId
        )
        
        // Add to appropriate list and update statistics
        if (algorithmId == algorithmAId) {
            measurementsA.add(measurement)
            totalErrorA += error
            if (error > maxErrorA) {
                maxErrorA = error
            }
            measurementCountA++
            Log.d(TAG, "Algorithm A measurement recorded: error = $error meters")
        } else {
            measurementsB.add(measurement)
            totalErrorB += error
            if (error > maxErrorB) {
                maxErrorB = error
            }
            measurementCountB++
            Log.d(TAG, "Algorithm B measurement recorded: error = $error meters")
        }
        
        return error
    }
    
    /**
     * End the test and calculate results
     */
    suspend fun endTest(): ComparativeTestResult? {
        if (!isTestActive) {
            Log.e(TAG, "No active test")
            return null
        }
        
        // Calculate statistics for algorithm A
        val avgErrorA = if (measurementCountA > 0) totalErrorA / measurementCountA else 0.0
        var varianceSumA = 0.0
        for (measurement in measurementsA) {
            varianceSumA += (measurement.error - avgErrorA).pow(2)
        }
        val stdDeviationA = if (measurementCountA > 1) 
            sqrt(varianceSumA / (measurementCountA - 1)) else 0.0
            
        // Calculate statistics for algorithm B
        val avgErrorB = if (measurementCountB > 0) totalErrorB / measurementCountB else 0.0
        var varianceSumB = 0.0
        for (measurement in measurementsB) {
            varianceSumB += (measurement.error - avgErrorB).pow(2)
        }
        val stdDeviationB = if (measurementCountB > 1) 
            sqrt(varianceSumB / (measurementCountB - 1)) else 0.0
        
        val testDuration = System.currentTimeMillis() - testStartTime
        
        // Create test result
        val result = ComparativeTestResult(
            id = testId,
            name = testName,
            startTime = testStartTime,
            duration = testDuration,
            algorithmAId = algorithmAId,
            algorithmBId = algorithmBId,
            algorithmAMeasurementCount = measurementCountA,
            algorithmBMeasurementCount = measurementCountB,
            algorithmAAvgError = avgErrorA,
            algorithmBAvgError = avgErrorB,
            algorithmAMaxError = maxErrorA,
            algorithmBMaxError = maxErrorB,
            algorithmAStdDeviation = stdDeviationA,
            algorithmBStdDeviation = stdDeviationB,
            errorDifference = avgErrorB - avgErrorA, // Positive means A is better
            improvementPercentage = if (avgErrorB > 0) ((avgErrorB - avgErrorA) / avgErrorB) * 100 else 0.0
        )
        
        // Save to Firestore
        try {
            saveResult(result)
        } catch (e: Exception) {
            Log.e(TAG, "Error saving comparative test result", e)
        }
        
        // Update state
        _testResult.value = result
        isTestActive = false
        groundTruthPosition = null
        
        Log.d(TAG, "A/B test ended: Algorithm A avg error = $avgErrorA, Algorithm B avg error = $avgErrorB")
        
        return result
    }
    
    /**
     * Save test result to Firestore
     */
    private suspend fun saveResult(result: ComparativeTestResult) {
        val resultData = hashMapOf(
            "id" to result.id,
            "name" to result.name,
            "startTime" to result.startTime,
            "duration" to result.duration,
            "algorithmAId" to result.algorithmAId,
            "algorithmBId" to result.algorithmBId,
            "algorithmAMeasurementCount" to result.algorithmAMeasurementCount,
            "algorithmBMeasurementCount" to result.algorithmBMeasurementCount,
            "algorithmAAvgError" to result.algorithmAAvgError,
            "algorithmBAvgError" to result.algorithmBAvgError,
            "algorithmAMaxError" to result.algorithmAMaxError,
            "algorithmBMaxError" to result.algorithmBMaxError,
            "algorithmAStdDeviation" to result.algorithmAStdDeviation,
            "algorithmBStdDeviation" to result.algorithmBStdDeviation,
            "errorDifference" to result.errorDifference,
            "improvementPercentage" to result.improvementPercentage,
            "timestamp" to System.currentTimeMillis()
        )
        
        // Save result
        db.collection("comparative_test_results")
            .document(result.id)
            .set(resultData)
            .await()
        
        // Save measurements for algorithm A
        for ((index, measurement) in measurementsA.withIndex()) {
            val measurementData = hashMapOf(
                "testId" to result.id,
                "algorithmId" to algorithmAId,
                "index" to index,
                "timestamp" to measurement.timestamp,
                "groundTruth" to hashMapOf(
                    "x" to measurement.groundTruth.x,
                    "y" to measurement.groundTruth.y,
                    "floor" to measurement.groundTruth.floor
                ),
                "estimated" to hashMapOf(
                    "x" to measurement.estimated.x,
                    "y" to measurement.estimated.y,
                    "floor" to measurement.estimated.floor
                ),
                "error" to measurement.error
            )
            
            db.collection("comparative_test_results")
                .document(result.id)
                .collection("measurements_a")
                .document(index.toString())
                .set(measurementData)
                .await()
        }
        
        // Save measurements for algorithm B
        for ((index, measurement) in measurementsB.withIndex()) {
            val measurementData = hashMapOf(
                "testId" to result.id,
                "algorithmId" to algorithmBId,
                "index" to index,
                "timestamp" to measurement.timestamp,
                "groundTruth" to hashMapOf(
                    "x" to measurement.groundTruth.x,
                    "y" to measurement.groundTruth.y,
                    "floor" to measurement.groundTruth.floor
                ),
                "estimated" to hashMapOf(
                    "x" to measurement.estimated.x,
                    "y" to measurement.estimated.y,
                    "floor" to measurement.estimated.floor
                ),
                "error" to measurement.error
            )
            
            db.collection("comparative_test_results")
                .document(result.id)
                .collection("measurements_b")
                .document(index.toString())
                .set(measurementData)
                .await()
        }
    }
    
    /**
     * Load comparative test results from Firestore
     */
    suspend fun loadComparativeTestResults(): List<ComparativeTestResult> {
        try {
            val snapshot = db.collection("comparative_test_results")
                .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .limit(20)
                .get()
                .await()
            
            return snapshot.documents.mapNotNull { doc ->
                try {
                    ComparativeTestResult(
                        id = doc.id,
                        name = doc.getString("name") ?: "",
                        startTime = doc.getLong("startTime") ?: 0L,
                        duration = doc.getLong("duration") ?: 0L,
                        algorithmAId = doc.getString("algorithmAId") ?: "",
                        algorithmBId = doc.getString("algorithmBId") ?: "",
                        algorithmAMeasurementCount = doc.getLong("algorithmAMeasurementCount")?.toInt() ?: 0,
                        algorithmBMeasurementCount = doc.getLong("algorithmBMeasurementCount")?.toInt() ?: 0,
                        algorithmAAvgError = doc.getDouble("algorithmAAvgError") ?: 0.0,
                        algorithmBAvgError = doc.getDouble("algorithmBAvgError") ?: 0.0,
                        algorithmAMaxError = doc.getDouble("algorithmAMaxError") ?: 0.0,
                        algorithmBMaxError = doc.getDouble("algorithmBMaxError") ?: 0.0,
                        algorithmAStdDeviation = doc.getDouble("algorithmAStdDeviation") ?: 0.0,
                        algorithmBStdDeviation = doc.getDouble("algorithmBStdDeviation") ?: 0.0,
                        errorDifference = doc.getDouble("errorDifference") ?: 0.0,
                        improvementPercentage = doc.getDouble("improvementPercentage") ?: 0.0
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing comparative test result", e)
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading comparative test results", e)
            return emptyList()
        }
    }
    
    /**
     * Calculate distance between two positions
     */
    private fun calculateDistance(p1: Position, p2: Position): Double {
        // If on different floors, add floor penalty
        val floorPenalty = if (p1.floor != p2.floor) {
            Math.abs(p1.floor - p2.floor) * 4.0
        } else {
            0.0
        }
        
        // Calculate 2D distance
        val dx = p2.x - p1.x
        val dy = p2.y - p1.y
        
        return sqrt(dx * dx + dy * dy) + floorPenalty
    }
    
    /**
     * Check if a test is active
     */
    fun isTestActive(): Boolean {
        return isTestActive
    }
    
    /**
     * Data class for test measurements with algorithm ID
     */
    data class MeasurementPair(
        val timestamp: Long,
        val groundTruth: Position,
        val estimated: Position,
        val error: Double,
        val algorithmId: String
    )
}