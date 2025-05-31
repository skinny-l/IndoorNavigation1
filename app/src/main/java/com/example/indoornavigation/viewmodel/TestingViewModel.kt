package com.example.indoornavigation.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.example.indoornavigation.data.models.Position
import com.example.indoornavigation.data.models.TestResult
import com.example.indoornavigation.testing.PositioningTester
import kotlinx.coroutines.flow.StateFlow

/**
 * ViewModel for testing functionality
 */
class TestingViewModel(application: Application) : AndroidViewModel(application) {

    // Positioning tester
    private val positioningTester = PositioningTester()
    
    // Test type
    private var testType = TestType.ACCURACY
    
    // A/B test configuration
    private var abTestConfig: ABTestConfig? = null
    private var currentAlgorithm: Algorithm? = null
    private var algorithmAResults: TestResult? = null
    private var algorithmBResults: TestResult? = null
    
    // Test result
    val testResult: StateFlow<TestResult?> = positioningTester.testResult
    
    /**
     * Start accuracy test with known ground truth position
     */
    fun startTest(name: String, groundTruthPosition: Position): Boolean {
        return positioningTester.startTest(name, groundTruthPosition)
    }
    
    /**
     * Start walking test from current position
     */
    fun startWalkingTest(name: String, startPosition: Position): Boolean {
        return positioningTester.startTest("$name (Walking)", startPosition)
    }
    
    /**
     * Start A/B test comparing two algorithms
     */
    fun startABTest(
        testName: String,
        algorithmA: Algorithm,
        algorithmB: Algorithm,
        sampleCount: Int
    ) {
        // Store A/B test configuration
        abTestConfig = ABTestConfig(
            testName = testName,
            algorithmA = algorithmA,
            algorithmB = algorithmB,
            sampleCount = sampleCount,
            currentSample = 0
        )
        
        // Start with algorithm A
        currentAlgorithm = algorithmA
        startAlgorithmTest(testName, algorithmA)
    }
    
    /**
     * Start test for specific algorithm
     */
    private fun startAlgorithmTest(baseName: String, algorithm: Algorithm) {
        // Get current position as ground truth
        // In a real implementation, we would have predefined test points
        val position = getCurrentPosition() ?: return
        
        // Start test
        val testName = "$baseName (${algorithm.displayName})"
        positioningTester.startTest(testName, position)
    }
    
    /**
     * Record position measurement
     */
    fun recordMeasurement(position: Position): Double {
        return positioningTester.recordMeasurement(position)
    }
    
    /**
     * End current test
     */
    suspend fun endTest(): TestResult? {
        val result = positioningTester.endTest()
        
        // Handle A/B test completion
        if (abTestConfig != null && currentAlgorithm != null) {
            handleABTestCompletion(result)
        }
        
        return result
    }
    
    /**
     * Handle A/B test completion
     */
    private fun handleABTestCompletion(result: TestResult?) {
        val config = abTestConfig ?: return
        
        // Store result for current algorithm
        when (currentAlgorithm) {
            Algorithm.BLE_TRILATERATION, 
            Algorithm.BLE_WEIGHTED_AVERAGE,
            Algorithm.WIFI_FINGERPRINTING,
            Algorithm.FUSION,
            Algorithm.FUSION_WITH_DR -> {
                if (currentAlgorithm == config.algorithmA) {
                    algorithmAResults = result
                    
                    // Switch to algorithm B
                    currentAlgorithm = config.algorithmB
                    startAlgorithmTest(config.testName, config.algorithmB)
                } else if (currentAlgorithm == config.algorithmB) {
                    algorithmBResults = result
                    
                    // A/B test completed
                    abTestConfig = null
                    currentAlgorithm = null
                    
                    // Compare results
                    compareABTestResults()
                }
            }
            else -> {
                // Reset A/B test
                abTestConfig = null
                currentAlgorithm = null
            }
        }
    }
    
    /**
     * Compare A/B test results
     */
    private fun compareABTestResults() {
        val resultA = algorithmAResults
        val resultB = algorithmBResults
        
        if (resultA != null && resultB != null) {
            // In a real implementation, we would display a comparison UI
            // For now, just log the results
            val improvement = if (resultA.averageError > resultB.averageError) {
                val percent = (resultA.averageError - resultB.averageError) / resultA.averageError * 100
                "Algorithm B is ${String.format("%.1f", percent)}% more accurate"
            } else {
                val percent = (resultB.averageError - resultA.averageError) / resultB.averageError * 100
                "Algorithm A is ${String.format("%.1f", percent)}% more accurate"
            }
            
            // TODO: Show comparison dialog
        }
    }
    
    /**
     * Load test results
     */
    suspend fun loadTestResults(): List<TestResult> {
        return positioningTester.loadTestResults()
    }
    
    /**
     * Check if a test is active
     */
    fun isTestActive(): Boolean {
        return positioningTester.isTestActive()
    }
    
    /**
     * Get current position from system
     */
    private fun getCurrentPosition(): Position? {
        // This would be implemented to get position from the positioning system
        // For now, return a dummy position
        return Position(20.0, 30.0, 1)
    }
    
    /**
     * Set test type
     */
    fun setTestType(type: TestType) {
        testType = type
    }
    
    /**
     * Get test type
     */
    fun getTestType(): TestType {
        return testType
    }
    
    /**
     * Test types
     */
    enum class TestType {
        ACCURACY,   // Static position testing
        WALKING,    // Walking path testing
        A_B_TEST    // Compare two algorithms
    }
    
    /**
     * Positioning algorithms
     */
    enum class Algorithm(val displayName: String) {
        BLE_TRILATERATION("BLE Trilateration"),
        BLE_WEIGHTED_AVERAGE("BLE Weighted Average"),
        WIFI_FINGERPRINTING("WiFi Fingerprinting"),
        FUSION("Sensor Fusion"),
        FUSION_WITH_DR("Fusion with Dead Reckoning")
    }
    
    /**
     * A/B test configuration
     */
    data class ABTestConfig(
        val testName: String,
        val algorithmA: Algorithm,
        val algorithmB: Algorithm,
        val sampleCount: Int,
        var currentSample: Int
    )
}