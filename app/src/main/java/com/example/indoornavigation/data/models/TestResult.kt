package com.example.indoornavigation.data.models

/**
 * Data class representing positioning test results
 */
data class TestResult(
    val id: String,
    val name: String,
    val startTime: Long,
    val duration: Long,
    val measurementCount: Int,
    val averageError: Double,
    val maxError: Double,
    val standardDeviation: Double,
    val floor: Int
)