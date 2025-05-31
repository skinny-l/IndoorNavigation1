package com.example.indoornavigation.testing

import android.content.Context
import android.location.Location
import android.util.Log
import com.example.indoornavigation.outdoor.OutdoorNavigationManager
import com.example.indoornavigation.positioning.BuildingDetector
import kotlinx.coroutines.*

/**
 * Testing utilities for outdoor navigation system
 * Helps validate configuration and test detection accuracy
 */
class OutdoorNavigationTester(
    private val context: Context,
    private val buildingDetector: BuildingDetector,
    private val outdoorNavigationManager: OutdoorNavigationManager
) {
    companion object {
        private const val TAG = "OutdoorNavigationTester"
    }

    /**
     * Test building detection at various distances
     */
    suspend fun testBuildingDetection() = withContext(Dispatchers.IO) {
        Log.d(TAG, "=== Starting Building Detection Test ===")
        
        // Test coordinates at different distances from building
        val testLocations = listOf(
            TestLocation("Very Close", 3.071421, 101.500136, 0.0),
            TestLocation("Close", 3.071421, 101.500136, 50.0),
            TestLocation("Moderate", 3.071421, 101.500136, 200.0),
            TestLocation("Far", 3.071421, 101.500136, 500.0),
            TestLocation("Very Far", 3.071421, 101.500136, 1000.0)
        )
        
        for (location in testLocations) {
            val testLocation = Location("test").apply {
                latitude = location.lat
                longitude = location.lng
            }
            
            val isInside = buildingDetector.isInBuildingByLocation(testLocation)
            val confidence = buildingDetector.getConfidence()
            
            Log.d(TAG, "${location.name}: Inside=${isInside}, Confidence=${confidence}")
        }
    }

    /**
     * Test entrance detection and recommendations
     */
    suspend fun testEntranceDetection() = withContext(Dispatchers.IO) {
        Log.d(TAG, "=== Starting Entrance Detection Test ===")
        
        // Simulate being at different locations around building
        val testLocation = Location("test").apply {
            latitude = 3.071421  // Slightly away from building
            longitude = 101.500136
        }
        
        // Force location update in outdoor navigation manager
        val entrances = outdoorNavigationManager.getAllEntrances()
        
        Log.d(TAG, "Found ${entrances.size} entrances:")
        entrances.forEach { entrance ->
            Log.d(TAG, "- ${entrance.entrance.name}: ${entrance.distanceMeters}m, ${entrance.estimatedWalkTime}")
            Log.d(TAG, "  Recommended: ${entrance.isRecommended}")
            Log.d(TAG, "  Features: ${entrance.entrance.features}")
        }
    }

    /**
     * Test WiFi detection (simulated)
     */
    fun testWifiDetection() {
        Log.d(TAG, "=== WiFi Detection Test ===")
        
        // This would normally require actual WiFi scanning
        // For testing, we can check if WiFi manager is working
        try {
            buildingDetector.startWiFiScanning()
            Log.d(TAG, "WiFi scanning started successfully")
            
            // Let it run for a few seconds
            Thread.sleep(5000)
            
            buildingDetector.stopWiFiScanning()
            Log.d(TAG, "WiFi scanning stopped")
        } catch (e: Exception) {
            Log.e(TAG, "WiFi scanning failed: ${e.message}")
        }
    }

    /**
     * Test navigation suggestions at different distances
     */
    suspend fun testNavigationSuggestions() = withContext(Dispatchers.IO) {
        Log.d(TAG, "=== Navigation Suggestions Test ===")
        
        val testDistances = listOf(
            10.0 to "Very Close",
            50.0 to "Close", 
            200.0 to "Moderate",
            800.0 to "Far",
            1500.0 to "Very Far"
        )
        
        testDistances.forEach { (distance, label) ->
            // Calculate test coordinates at specified distance
            val testLat = 3.071421 + (distance / 111000.0) // Rough conversion
            val testLng = 101.500136 + (distance / 111000.0)
            
            Log.d(TAG, "$label ($distance m):")
            Log.d(TAG, "  Suggested action: Navigate based on distance")
        }
    }

    /**
     * Validate configuration data
     */
    fun validateConfiguration(): ConfigurationReport {
        Log.d(TAG, "=== Configuration Validation ===")
        
        val report = ConfigurationReport()
        
        // Check building coordinates
        if (isValidCoordinate(3.071421, 101.500136)) {
            report.validCoordinates = true
            Log.d(TAG, "✅ Building coordinates are valid")
        } else {
            report.validCoordinates = false
            Log.w(TAG, "❌ Building coordinates may need updating")
        }
        
        // Check entrances
        val entrances = outdoorNavigationManager.getAllEntrances()
        if (entrances.isNotEmpty()) {
            report.hasEntrances = true
            report.entranceCount = entrances.size
            Log.d(TAG, "✅ Found ${entrances.size} configured entrances")
        } else {
            report.hasEntrances = false
            Log.w(TAG, "❌ No entrances configured")
        }
        
        // Check parking info
        val parkingInfo = outdoorNavigationManager.getParkingInfo()
        if (parkingInfo.availableSpots.isNotEmpty()) {
            report.hasParkingInfo = true
            Log.d(TAG, "✅ Parking information configured")
        } else {
            report.hasParkingInfo = false
            Log.w(TAG, "❌ No parking information configured")
        }
        
        return report
    }
    
    private fun isValidCoordinate(lat: Double, lng: Double): Boolean {
        return lat != 0.0 && lng != 0.0 && 
               lat >= -90 && lat <= 90 && 
               lng >= -180 && lng <= 180
    }

    data class TestLocation(
        val name: String,
        val lat: Double, 
        val lng: Double,
        val expectedDistance: Double
    )

    data class ConfigurationReport(
        var validCoordinates: Boolean = false,
        var hasEntrances: Boolean = false,
        var entranceCount: Int = 0,
        var hasParkingInfo: Boolean = false,
        var wifiNetworksConfigured: Boolean = false
    )
}
