package com.example.indoornavigation.data.models

/**
 * Data class representing comparative (A/B) test results
 * Used to compare two different positioning algorithms
 */
data class ComparativeTestResult(
    val id: String,
    val name: String,
    val startTime: Long,
    val duration: Long,
    val algorithmAId: String,
    val algorithmBId: String,
    val algorithmAMeasurementCount: Int,
    val algorithmBMeasurementCount: Int,
    val algorithmAAvgError: Double,
    val algorithmBAvgError: Double,
    val algorithmAMaxError: Double,
    val algorithmBMaxError: Double,
    val algorithmAStdDeviation: Double,
    val algorithmBStdDeviation: Double,
    val errorDifference: Double,
    val improvementPercentage: Double
)