package com.example.indoornavigation.data.models

/**
 * Data class representing step detection information
 */
data class StepData(
    val count: Int,          // Total step count
    val heading: Float,      // Heading in degrees
    val timestamp: Long      // Detection timestamp
)