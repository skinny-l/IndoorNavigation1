package com.example.indoornavigation.testing

import android.util.Log
import com.example.indoornavigation.data.models.Position
import com.example.indoornavigation.data.models.TestResult
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import java.util.UUID
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Testing framework for measuring positioning accuracy
 */
class PositioningTester {
    
    private val TAG = "PositioningTester"
    
    // Firestore for storing test results
    private val db = FirebaseFirestore.getInstance()
    
    // Active test data
    private var isTestActive = false
    private var testStartTime = 0L
    private var testId = ""
    private var testName = ""
    private var groundTruthPosition: Position? = null
    
    // Test measurements
    private val measurements = mutableListOf<TestMeasurement>()
    
    // Test statistics
    private var totalError = 0.0
    private var maxError = 0.0
    private var measurementCount = 0
    
    // Test result
    private val _testResult = MutableStateFlow<TestResult?>(null)
    val testResult: StateFlow<TestResult?> = _testResult.asStateFlow()
    
    /**
     * Start a new test
     */
    fun startTest(name: String, groundTruthPosition: Position): Boolean {
        if (isTestActive) {
            Log.e(TAG, "Test already active")
            return false
        }
        
        // Initialize test
        testId = UUID.randomUUID().toString()
        testName = name
        this.groundTruthPosition = groundTruthPosition
        testStartTime = System.currentTimeMillis()
        
        // Reset measurements
        measurements.clear()
        totalError = 0.0
        maxError = 0.0
        measurementCount = 0
        
        isTestActive = true
        Log.d(TAG, "Test started: $testName")
        
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
     * Record a position measurement
     */
    fun recordMeasurement(estimatedPosition: Position): Double {
        if (!isTestActive || groundTruthPosition == null) {
            Log.e(TAG, "No active test")
            return 0.0
        }
        
        // Calculate error
        val error = calculateDistance(groundTruthPosition!!, estimatedPosition)
        
        // Create measurement
        val measurement = TestMeasurement(
            timestamp = System.currentTimeMillis(),
            groundTruth = groundTruthPosition!!,
            estimated = estimatedPosition,
            error = error
        )
        
        // Add to measurements
        measurements.add(measurement)
        
        // Update statistics
        totalError += error
        if (error > maxError) {
            maxError = error
        }
        measurementCount++
        
        Log.d(TAG, "Measurement recorded: error = $error meters")
        
        return error
    }
    
    /**
     * End the test and calculate results
     */
    suspend fun endTest(): TestResult? {
        if (!isTestActive) {
            Log.e(TAG, "No active test")
            return null
        }
        
        // Calculate statistics
        val avgError = if (measurementCount > 0) totalError / measurementCount else 0.0
        val testDuration = System.currentTimeMillis() - testStartTime
        
        // Calculate standard deviation
        var varianceSum = 0.0
        for (measurement in measurements) {
            varianceSum += (measurement.error - avgError).pow(2)
        }
        val stdDeviation = if (measurementCount > 1) 
            sqrt(varianceSum / (measurementCount - 1)) else 0.0
        
        // Create test result
        val result = TestResult(
            id = testId,
            name = testName,
            startTime = testStartTime,
            duration = testDuration,
            measurementCount = measurementCount,
            averageError = avgError,
            maxError = maxError,
            standardDeviation = stdDeviation,
            floor = groundTruthPosition?.floor ?: 0
        )
        
        // Save to Firestore
        try {
            saveResult(result)
        } catch (e: Exception) {
            Log.e(TAG, "Error saving test result", e)
        }
        
        // Update state
        _testResult.value = result
        isTestActive = false
        groundTruthPosition = null
        
        Log.d(TAG, "Test ended: avg error = $avgError meters")
        
        return result
    }
    
    /**
     * Save test result to Firestore
     */
    private suspend fun saveResult(result: TestResult) {
        val resultData = hashMapOf(
            "id" to result.id,
            "name" to result.name,
            "startTime" to result.startTime,
            "duration" to result.duration,
            "measurementCount" to result.measurementCount,
            "averageError" to result.averageError,
            "maxError" to result.maxError,
            "standardDeviation" to result.standardDeviation,
            "floor" to result.floor,
            "timestamp" to System.currentTimeMillis()
        )
        
        // Save result
        db.collection("test_results")
            .document(result.id)
            .set(resultData)
            .await()
        
        // Save measurements
        for ((index, measurement) in measurements.withIndex()) {
            val measurementData = hashMapOf(
                "testId" to result.id,
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
            
            db.collection("test_results")
                .document(result.id)
                .collection("measurements")
                .document(index.toString())
                .set(measurementData)
                .await()
        }
    }
    
    /**
     * Load test results from Firestore
     */
    suspend fun loadTestResults(): List<TestResult> {
        try {
            val snapshot = db.collection("test_results")
                .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .limit(20)
                .get()
                .await()
            
            return snapshot.documents.mapNotNull { doc ->
                try {
                    TestResult(
                        id = doc.id,
                        name = doc.getString("name") ?: "",
                        startTime = doc.getLong("startTime") ?: 0L,
                        duration = doc.getLong("duration") ?: 0L,
                        measurementCount = doc.getLong("measurementCount")?.toInt() ?: 0,
                        averageError = doc.getDouble("averageError") ?: 0.0,
                        maxError = doc.getDouble("maxError") ?: 0.0,
                        standardDeviation = doc.getDouble("standardDeviation") ?: 0.0,
                        floor = doc.getLong("floor")?.toInt() ?: 0
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing test result", e)
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading test results", e)
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
     * Data class for test measurements
     */
    data class TestMeasurement(
        val timestamp: Long,
        val groundTruth: Position,
        val estimated: Position,
        val error: Double
    )
}