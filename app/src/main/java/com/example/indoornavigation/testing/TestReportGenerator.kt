package com.example.indoornavigation.testing

import android.util.Log
import com.example.indoornavigation.data.models.Position
import com.example.indoornavigation.data.models.TestResult
import com.example.indoornavigation.data.models.ComparativeTestResult
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.pow

/**
 * Class responsible for generating detailed test reports with statistical analysis
 */
class TestReportGenerator {
    private val TAG = "TestReportGenerator"
    
    // Firestore instance for data access
    private val db = FirebaseFirestore.getInstance()
    
    /**
     * Generate a detailed report for a specific test
     */
    suspend fun generateReport(testId: String): TestReport? {
        // First, determine if this is a regular test or an A/B test
        val regularTest = getTestResult(testId)
        if (regularTest != null) {
            return generateSingleTestReport(regularTest, getMeasurements(testId))
        }
        
        val comparativeTest = getComparativeTestResult(testId)
        if (comparativeTest != null) {
            return generateComparativeTestReport(
                comparativeTest,
                getMeasurementsA(testId),
                getMeasurementsB(testId)
            )
        }
        
        Log.e(TAG, "No test found with ID: $testId")
        return null
    }
    
    /**
     * Generate a report for a single-algorithm test
     */
    private fun generateSingleTestReport(
        testResult: TestResult,
        measurements: List<PositioningTester.TestMeasurement>
    ): TestReport {
        // Format timestamps
        val dateFormat = SimpleDateFormat("MMM dd, yyyy HH:mm:ss", Locale.US)
        val startTimeFormatted = dateFormat.format(Date(testResult.startTime))
        
        // Error distribution analysis
        val errorBands = analyzeErrorDistribution(measurements.map { it.error })
        
        // Mean and median error calculation
        val sortedErrors = measurements.map { it.error }.sorted()
        val meanError = testResult.averageError
        val medianError = if (sortedErrors.isNotEmpty()) {
            if (sortedErrors.size % 2 == 0) {
                (sortedErrors[sortedErrors.size / 2 - 1] + sortedErrors[sortedErrors.size / 2]) / 2
            } else {
                sortedErrors[sortedErrors.size / 2]
            }
        } else 0.0
        
        // 90th percentile error
        val percentile90Index = (measurements.size * 0.9).toInt()
        val percentile90Error = if (sortedErrors.isNotEmpty() && percentile90Index < sortedErrors.size) {
            sortedErrors[percentile90Index]
        } else 0.0
        
        // Floor detection accuracy
        var floorMismatches = 0
        for (measurement in measurements) {
            if (measurement.groundTruth.floor != measurement.estimated.floor) {
                floorMismatches++
            }
        }
        val floorAccuracy = if (measurements.isNotEmpty()) {
            100.0 * (1.0 - (floorMismatches.toDouble() / measurements.size))
        } else 0.0
        
        // Time-series analysis
        val timeSeriesData = createTimeSeriesData(measurements)
        
        return TestReport(
            id = testResult.id,
            name = testResult.name,
            type = TestReport.ReportType.SINGLE_ALGORITHM,
            startTime = startTimeFormatted,
            duration = "${testResult.duration / 1000.0} seconds",
            measurementCount = testResult.measurementCount,
            meanError = meanError,
            medianError = medianError,
            maxError = testResult.maxError,
            standardDeviation = testResult.standardDeviation,
            percentile90Error = percentile90Error,
            floorAccuracy = floorAccuracy,
            errorDistribution = errorBands,
            timeSeriesData = timeSeriesData,
            algorithmComparison = null
        )
    }
    
    /**
     * Generate a report for a comparative (A/B) test
     */
    private fun generateComparativeTestReport(
        testResult: ComparativeTestResult,
        measurementsA: List<AlgorithmComparator.MeasurementPair>,
        measurementsB: List<AlgorithmComparator.MeasurementPair>
    ): TestReport {
        // Format timestamps
        val dateFormat = SimpleDateFormat("MMM dd, yyyy HH:mm:ss", Locale.US)
        val startTimeFormatted = dateFormat.format(Date(testResult.startTime))
        
        // Error distribution analysis for both algorithms
        val errorBandsA = analyzeErrorDistribution(measurementsA.map { it.error })
        val errorBandsB = analyzeErrorDistribution(measurementsB.map { it.error })
        
        // Combined error distribution for chart display
        val errorBands = mergeErrorDistributions(errorBandsA, errorBandsB)
        
        // Calculate median errors
        val sortedErrorsA = measurementsA.map { it.error }.sorted()
        val sortedErrorsB = measurementsB.map { it.error }.sorted()
        
        val medianErrorA = if (sortedErrorsA.isNotEmpty()) {
            if (sortedErrorsA.size % 2 == 0) {
                (sortedErrorsA[sortedErrorsA.size / 2 - 1] + sortedErrorsA[sortedErrorsA.size / 2]) / 2
            } else {
                sortedErrorsA[sortedErrorsA.size / 2]
            }
        } else 0.0
        
        val medianErrorB = if (sortedErrorsB.isNotEmpty()) {
            if (sortedErrorsB.size % 2 == 0) {
                (sortedErrorsB[sortedErrorsB.size / 2 - 1] + sortedErrorsB[sortedErrorsB.size / 2]) / 2
            } else {
                sortedErrorsB[sortedErrorsB.size / 2]
            }
        } else 0.0
        
        // 90th percentile errors
        val percentile90IndexA = (measurementsA.size * 0.9).toInt()
        val percentile90ErrorA = if (sortedErrorsA.isNotEmpty() && percentile90IndexA < sortedErrorsA.size) {
            sortedErrorsA[percentile90IndexA]
        } else 0.0
        
        val percentile90IndexB = (measurementsB.size * 0.9).toInt()
        val percentile90ErrorB = if (sortedErrorsB.isNotEmpty() && percentile90IndexB < sortedErrorsB.size) {
            sortedErrorsB[percentile90IndexB]
        } else 0.0
        
        // Floor detection accuracy for both algorithms
        var floorMismatchesA = 0
        for (measurement in measurementsA) {
            if (measurement.groundTruth.floor != measurement.estimated.floor) {
                floorMismatchesA++
            }
        }
        val floorAccuracyA = if (measurementsA.isNotEmpty()) {
            100.0 * (1.0 - (floorMismatchesA.toDouble() / measurementsA.size))
        } else 0.0
        
        var floorMismatchesB = 0
        for (measurement in measurementsB) {
            if (measurement.groundTruth.floor != measurement.estimated.floor) {
                floorMismatchesB++
            }
        }
        val floorAccuracyB = if (measurementsB.isNotEmpty()) {
            100.0 * (1.0 - (floorMismatchesB.toDouble() / measurementsB.size))
        } else 0.0
        
        // Statistical significance test (simplified t-test)
        val isStatisticallySignificant = performSimplifiedTTest(
            measurementsA.map { it.error },
            measurementsB.map { it.error }
        )
        
        // Time series data
        val timeSeriesDataA = createTimeSeriesData(measurementsA)
        val timeSeriesDataB = createTimeSeriesData(measurementsB)
        
        // Create algorithm comparison object
        val algorithmComparison = TestReport.AlgorithmComparison(
            algorithmAId = testResult.algorithmAId,
            algorithmBId = testResult.algorithmBId,
            algorithmAMeanError = testResult.algorithmAAvgError,
            algorithmBMeanError = testResult.algorithmBAvgError,
            algorithmAMedianError = medianErrorA,
            algorithmBMedianError = medianErrorB,
            algorithmAMaxError = testResult.algorithmAMaxError,
            algorithmBMaxError = testResult.algorithmBMaxError,
            algorithmAStdDeviation = testResult.algorithmAStdDeviation,
            algorithmBStdDeviation = testResult.algorithmBStdDeviation,
            algorithmAPercentile90Error = percentile90ErrorA,
            algorithmBPercentile90Error = percentile90ErrorB,
            algorithmAFloorAccuracy = floorAccuracyA,
            algorithmBFloorAccuracy = floorAccuracyB,
            errorDifference = testResult.errorDifference,
            improvementPercentage = testResult.improvementPercentage,
            isStatisticallySignificant = isStatisticallySignificant,
            algorithmAErrorDistribution = errorBandsA,
            algorithmBErrorDistribution = errorBandsB,
            algorithmATimeSeriesData = timeSeriesDataA,
            algorithmBTimeSeriesData = timeSeriesDataB
        )
        
        return TestReport(
            id = testResult.id,
            name = testResult.name,
            type = TestReport.ReportType.COMPARATIVE,
            startTime = startTimeFormatted,
            duration = "${testResult.duration / 1000.0} seconds",
            measurementCount = testResult.algorithmAMeasurementCount + testResult.algorithmBMeasurementCount,
            meanError = (testResult.algorithmAAvgError + testResult.algorithmBAvgError) / 2,
            medianError = (medianErrorA + medianErrorB) / 2,
            maxError = maxOf(testResult.algorithmAMaxError, testResult.algorithmBMaxError),
            standardDeviation = (testResult.algorithmAStdDeviation + testResult.algorithmBStdDeviation) / 2,
            percentile90Error = (percentile90ErrorA + percentile90ErrorB) / 2,
            floorAccuracy = (floorAccuracyA + floorAccuracyB) / 2,
            errorDistribution = errorBands,
            timeSeriesData = timeSeriesDataA, // Just use algorithmA data for the main chart
            algorithmComparison = algorithmComparison
        )
    }
    
    /**
     * Analyze error distribution by grouping into bands
     */
    private fun analyzeErrorDistribution(errors: List<Double>): Map<String, Int> {
        val bands = mutableMapOf(
            "0-0.5m" to 0,
            "0.5-1m" to 0,
            "1-2m" to 0,
            "2-3m" to 0,
            "3-5m" to 0,
            "5m+" to 0
        )
        
        for (error in errors) {
            when {
                error < 0.5 -> bands["0-0.5m"] = bands["0-0.5m"]!! + 1
                error < 1.0 -> bands["0.5-1m"] = bands["0.5-1m"]!! + 1
                error < 2.0 -> bands["1-2m"] = bands["1-2m"]!! + 1
                error < 3.0 -> bands["2-3m"] = bands["2-3m"]!! + 1
                error < 5.0 -> bands["3-5m"] = bands["3-5m"]!! + 1
                else -> bands["5m+"] = bands["5m+"]!! + 1
            }
        }
        
        return bands
    }
    
    /**
     * Merge two error distributions
     */
    private fun mergeErrorDistributions(
        distA: Map<String, Int>,
        distB: Map<String, Int>
    ): Map<String, Int> {
        val result = mutableMapOf<String, Int>()
        
        // Just sum the counts from both distributions
        for (key in distA.keys) {
            result[key] = (distA[key] ?: 0) + (distB[key] ?: 0)
        }
        
        return result
    }
    
    /**
     * Create time series data for visualization
     */
    private fun createTimeSeriesData(measurements: List<Any>): List<Pair<Long, Double>> {
        val result = mutableListOf<Pair<Long, Double>>()
        
        for (measurement in measurements) {
            val timestamp = when (measurement) {
                is PositioningTester.TestMeasurement -> measurement.timestamp
                is AlgorithmComparator.MeasurementPair -> measurement.timestamp
                else -> continue
            }
            
            val error = when (measurement) {
                is PositioningTester.TestMeasurement -> measurement.error
                is AlgorithmComparator.MeasurementPair -> measurement.error
                else -> continue
            }
            
            result.add(Pair(timestamp, error))
        }
        
        return result.sortedBy { it.first }
    }
    
    /**
     * Perform a simplified t-test to determine statistical significance
     * Returns true if the difference is statistically significant
     */
    private fun performSimplifiedTTest(
        samplesA: List<Double>,
        samplesB: List<Double>
    ): Boolean {
        // Need reasonable sample sizes for significance testing
        if (samplesA.size < 10 || samplesB.size < 10) {
            return false
        }
        
        // Calculate means
        val meanA = samplesA.average()
        val meanB = samplesB.average()
        
        // Calculate variances
        val varianceA = samplesA.map { (it - meanA).pow(2) }.average()
        val varianceB = samplesB.map { (it - meanB).pow(2) }.average()
        
        // Calculate standard errors
        val seA = Math.sqrt(varianceA / samplesA.size)
        val seB = Math.sqrt(varianceB / samplesB.size)
        
        // Calculate t-statistic
        val se = Math.sqrt(seA * seA + seB * seB)
        val tStatistic = Math.abs(meanA - meanB) / se
        
        // Simplified degrees of freedom calculation (conservative)
        val df = minOf(samplesA.size - 1, samplesB.size - 1)
        
        // Critical t-value for 95% confidence (approximate)
        val criticalT = when {
            df < 10 -> 2.26
            df < 20 -> 2.09
            df < 30 -> 2.04
            df < 60 -> 2.00
            df < 120 -> 1.98
            else -> 1.96
        }
        
        // Compare t-statistic with critical value
        return tStatistic > criticalT
    }
    
    /**
     * Get a regular test result by ID
     */
    private suspend fun getTestResult(testId: String): TestResult? {
        try {
            val doc = db.collection("test_results")
                .document(testId)
                .get()
                .await()
                
            if (!doc.exists()) {
                return null
            }
            
            return TestResult(
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
            Log.e(TAG, "Error getting test result", e)
            return null
        }
    }
    
    /**
     * Get a comparative test result by ID
     */
    private suspend fun getComparativeTestResult(testId: String): ComparativeTestResult? {
        try {
            val doc = db.collection("comparative_test_results")
                .document(testId)
                .get()
                .await()
                
            if (!doc.exists()) {
                return null
            }
            
            return ComparativeTestResult(
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
            Log.e(TAG, "Error getting comparative test result", e)
            return null
        }
    }
    
    /**
     * Get measurements for a regular test
     */
    private suspend fun getMeasurements(testId: String): List<PositioningTester.TestMeasurement> {
        try {
            val snapshot = db.collection("test_results")
                .document(testId)
                .collection("measurements")
                .get()
                .await()
                
            return snapshot.documents.mapNotNull { doc ->
                try {
                    // Extract ground truth position
                    val groundTruth = doc.get("groundTruth") as? Map<*, *>
                    val groundTruthX = (groundTruth?.get("x") as? Number)?.toDouble() ?: 0.0
                    val groundTruthY = (groundTruth?.get("y") as? Number)?.toDouble() ?: 0.0
                    val groundTruthFloor = (groundTruth?.get("floor") as? Number)?.toInt() ?: 0
                    
                    // Extract estimated position
                    val estimated = doc.get("estimated") as? Map<*, *>
                    val estimatedX = (estimated?.get("x") as? Number)?.toDouble() ?: 0.0
                    val estimatedY = (estimated?.get("y") as? Number)?.toDouble() ?: 0.0
                    val estimatedFloor = (estimated?.get("floor") as? Number)?.toInt() ?: 0
                    
                    PositioningTester.TestMeasurement(
                        timestamp = doc.getLong("timestamp") ?: 0L,
                        groundTruth = Position(groundTruthX, groundTruthY, groundTruthFloor),
                        estimated = Position(estimatedX, estimatedY, estimatedFloor),
                        error = doc.getDouble("error") ?: 0.0
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing measurement", e)
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting measurements", e)
            return emptyList()
        }
    }
    
    /**
     * Get measurements for algorithm A in a comparative test
     */
    private suspend fun getMeasurementsA(testId: String): List<AlgorithmComparator.MeasurementPair> {
        return getComparativeMeasurements(testId, "measurements_a")
    }
    
    /**
     * Get measurements for algorithm B in a comparative test
     */
    private suspend fun getMeasurementsB(testId: String): List<AlgorithmComparator.MeasurementPair> {
        return getComparativeMeasurements(testId, "measurements_b")
    }
    
    /**
     * Get measurements from a specific collection in a comparative test
     */
    private suspend fun getComparativeMeasurements(
        testId: String,
        collection: String
    ): List<AlgorithmComparator.MeasurementPair> {
        try {
            val snapshot = db.collection("comparative_test_results")
                .document(testId)
                .collection(collection)
                .get()
                .await()
                
            return snapshot.documents.mapNotNull { doc ->
                try {
                    // Extract ground truth position
                    val groundTruth = doc.get("groundTruth") as? Map<*, *>
                    val groundTruthX = (groundTruth?.get("x") as? Number)?.toDouble() ?: 0.0
                    val groundTruthY = (groundTruth?.get("y") as? Number)?.toDouble() ?: 0.0
                    val groundTruthFloor = (groundTruth?.get("floor") as? Number)?.toInt() ?: 0
                    
                    // Extract estimated position
                    val estimated = doc.get("estimated") as? Map<*, *>
                    val estimatedX = (estimated?.get("x") as? Number)?.toDouble() ?: 0.0
                    val estimatedY = (estimated?.get("y") as? Number)?.toDouble() ?: 0.0
                    val estimatedFloor = (estimated?.get("floor") as? Number)?.toInt() ?: 0
                    
                    AlgorithmComparator.MeasurementPair(
                        timestamp = doc.getLong("timestamp") ?: 0L,
                        groundTruth = Position(groundTruthX, groundTruthY, groundTruthFloor),
                        estimated = Position(estimatedX, estimatedY, estimatedFloor),
                        error = doc.getDouble("error") ?: 0.0,
                        algorithmId = doc.getString("algorithmId") ?: ""
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing comparative measurement", e)
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting comparative measurements", e)
            return emptyList()
        }
    }
}

/**
 * Data class for test reports
 */
data class TestReport(
    val id: String,
    val name: String,
    val type: ReportType,
    val startTime: String,
    val duration: String,
    val measurementCount: Int,
    val meanError: Double,
    val medianError: Double,
    val maxError: Double,
    val standardDeviation: Double,
    val percentile90Error: Double,
    val floorAccuracy: Double,
    val errorDistribution: Map<String, Int>,
    val timeSeriesData: List<Pair<Long, Double>>,
    val algorithmComparison: AlgorithmComparison?
) {
    /**
     * Report type enum
     */
    enum class ReportType {
        SINGLE_ALGORITHM,
        COMPARATIVE
    }
    
    /**
     * Data class for algorithm comparison details
     */
    data class AlgorithmComparison(
        val algorithmAId: String,
        val algorithmBId: String,
        val algorithmAMeanError: Double,
        val algorithmBMeanError: Double,
        val algorithmAMedianError: Double,
        val algorithmBMedianError: Double,
        val algorithmAMaxError: Double,
        val algorithmBMaxError: Double,
        val algorithmAStdDeviation: Double,
        val algorithmBStdDeviation: Double,
        val algorithmAPercentile90Error: Double,
        val algorithmBPercentile90Error: Double,
        val algorithmAFloorAccuracy: Double,
        val algorithmBFloorAccuracy: Double,
        val errorDifference: Double,
        val improvementPercentage: Double,
        val isStatisticallySignificant: Boolean,
        val algorithmAErrorDistribution: Map<String, Int>,
        val algorithmBErrorDistribution: Map<String, Int>,
        val algorithmATimeSeriesData: List<Pair<Long, Double>>,
        val algorithmBTimeSeriesData: List<Pair<Long, Double>>
    )
}