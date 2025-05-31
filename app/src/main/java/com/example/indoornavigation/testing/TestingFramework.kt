package com.example.indoornavigation.testing

import android.content.Context
import android.util.Log
import com.example.indoornavigation.data.models.Position
import com.example.indoornavigation.data.models.TestResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.util.UUID
import kotlin.math.sqrt

/**
 * Comprehensive testing framework for indoor positioning
 * Provides functionality for:
 * - Single algorithm testing
 * - A/B testing between algorithms
 * - Static and walking tests
 * - Statistical analysis and reporting
 */
class TestingFramework(private val context: Context) {

    private val TAG = "TestingFramework"
    
    // Testing components
    private val positioningTester = PositioningTester()
    private val algorithmComparator = AlgorithmComparator()
    
    // Framework state
    private val _testMode = MutableStateFlow<TestMode>(TestMode.STATIC)
    val testMode: StateFlow<TestMode> = _testMode.asStateFlow()
    
    // Test parameters
    private var walkingPathPoints: List<Position> = emptyList()
    private var currentPathIndex = 0
    
    /**
     * Start a static test with a single ground truth position
     */
    fun startStaticTest(
        name: String,
        groundTruthPosition: Position,
        algorithmId: String
    ): Boolean {
        _testMode.value = TestMode.STATIC
        return positioningTester.startTest(name, groundTruthPosition)
    }
    
    /**
     * Start an A/B test comparing two algorithms at the same position
     */
    fun startABTest(
        name: String,
        groundTruthPosition: Position,
        algorithmAId: String,
        algorithmBId: String
    ): Boolean {
        _testMode.value = TestMode.STATIC_AB
        return algorithmComparator.startABTest(name, groundTruthPosition, algorithmAId, algorithmBId)
    }
    
    /**
     * Start a walking test with multiple reference positions
     */
    fun startWalkingTest(
        name: String,
        pathPoints: List<Position>,
        algorithmId: String
    ): Boolean {
        if (pathPoints.isEmpty()) {
            Log.e(TAG, "Cannot start walking test with empty path")
            return false
        }
        
        _testMode.value = TestMode.WALKING
        walkingPathPoints = pathPoints
        currentPathIndex = 0
        
        // Start with first position
        return positioningTester.startTest("$name (Walking)", pathPoints[0])
    }
    
    /**
     * Start a walking A/B test comparing two algorithms along a path
     */
    fun startWalkingABTest(
        name: String,
        pathPoints: List<Position>,
        algorithmAId: String,
        algorithmBId: String
    ): Boolean {
        if (pathPoints.isEmpty()) {
            Log.e(TAG, "Cannot start walking A/B test with empty path")
            return false
        }
        
        _testMode.value = TestMode.WALKING_AB
        walkingPathPoints = pathPoints
        currentPathIndex = 0
        
        // Start with first position
        return algorithmComparator.startABTest(
            "$name (Walking A/B)",
            pathPoints[0],
            algorithmAId,
            algorithmBId
        )
    }
    
    /**
     * Advance to next position in a walking test
     * Returns true if there is a next position, false if test is complete
     */
    fun advanceToNextWalkingPosition(): Boolean {
        if (_testMode.value != TestMode.WALKING && _testMode.value != TestMode.WALKING_AB) {
            Log.e(TAG, "Not in a walking test mode")
            return false
        }
        
        if (currentPathIndex >= walkingPathPoints.size - 1) {
            // No more positions
            return false
        }
        
        // Move to next position
        currentPathIndex++
        val nextPosition = walkingPathPoints[currentPathIndex]
        
        if (_testMode.value == TestMode.WALKING) {
            // Update ground truth for single algorithm test
            positioningTester.updateGroundTruth(nextPosition)
        } else {
            // Update ground truth for A/B test
            algorithmComparator.updateGroundTruth(nextPosition)
        }
        
        return true
    }
    
    /**
     * Record a position measurement for the current test
     */
    fun recordMeasurement(estimatedPosition: Position, algorithmId: String = ""): Double {
        return when (_testMode.value) {
            TestMode.STATIC, TestMode.WALKING -> {
                positioningTester.recordMeasurement(estimatedPosition)
            }
            TestMode.STATIC_AB, TestMode.WALKING_AB -> {
                algorithmComparator.recordMeasurement(estimatedPosition, algorithmId)
            }
        }
    }
    
    /**
     * End the current test and get results
     */
    suspend fun endTest(): Any? = withContext(Dispatchers.IO) {
        when (_testMode.value) {
            TestMode.STATIC, TestMode.WALKING -> {
                positioningTester.endTest()
            }
            TestMode.STATIC_AB, TestMode.WALKING_AB -> {
                algorithmComparator.endTest()
            }
        }
    }
    
    /**
     * Get the current test mode
     */
    fun getCurrentTestMode(): TestMode {
        return _testMode.value
    }
    
    /**
     * Get test history
     */
    suspend fun getTestHistory(): List<TestResult> = withContext(Dispatchers.IO) {
        positioningTester.loadTestResults()
    }
    
    /**
     * Generate a test report with statistical analysis
     */
    suspend fun generateTestReport(testId: String): TestReport = withContext(Dispatchers.IO) {
        TestReportGenerator().generateReport(testId) ?: throw IllegalArgumentException("Test not found with ID: $testId")
    }
    
    /**
     * Enum defining test modes
     */
    enum class TestMode {
        STATIC,       // Single position, single algorithm
        STATIC_AB,    // Single position, comparing A/B algorithms
        WALKING,      // Multiple positions (path), single algorithm
        WALKING_AB    // Multiple positions (path), comparing A/B algorithms
    }
}