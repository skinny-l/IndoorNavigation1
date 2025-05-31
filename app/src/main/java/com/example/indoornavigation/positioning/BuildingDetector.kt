package com.example.indoornavigation.positioning

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.app.ActivityCompat
import com.example.indoornavigation.viewmodel.LocationStatus
import com.example.indoornavigation.viewmodel.NavigationViewModel

/**
 * Enhanced BuildingDetector that uses WiFi fingerprinting with GPS fallback
 * to accurately detect when users are inside or outside a building
 */
class BuildingDetector(private val context: Context) {
    
    companion object {
        private const val TAG = "BuildingDetector"
        
        // WiFi configuration for building detection - CAMPUS WITH SHARED SSIDs
        // NOTE: If your campus uses the same SSID for all buildings (e.g., "Campus_WiFi")
        // you should focus on BSSID fingerprinting instead of SSID detection
        private val BUILDING_WIFI_SSIDS = listOf(
            "Campus_WiFi",      // Same across all buildings - used for general campus detection
            "YourCampus_WiFi",  // Replace with your actual campus WiFi name
            "Student_WiFi"      // Any campus-wide networks
        )
        
        // IMPORTANT: For buildings sharing the same SSID, use BSSID fingerprinting
        // Each WiFi access point has a unique BSSID (MAC address) even if SSID is same
        private val KNOWN_BUILDING_BSSIDS = mapOf(
            // FORMAT: "BSSID" to expected_signal_strength
            // You need to collect these by walking around your specific building
            "00:11:22:33:44:55" to -60, // Access point near building entrance
            "AA:BB:CC:DD:EE:FF" to -65, // Access point on floor 1 
            "11:22:33:44:55:66" to -70, // Access point on floor 2
            "BB:CC:DD:EE:FF:AA" to -65, // Access point in main corridor
            "22:33:44:55:66:77" to -70  // Access point near library/lab area
            // ADD MORE BSSIDs SPECIFIC TO YOUR BUILDING
        )
        
        // RSSI thresholds
        private const val MIN_RSSI_THRESHOLD = -80  // dBm for any AP
        private const val HIGH_CONFIDENCE_THRESHOLD = -65 // dBm for high confidence
        private const val STRONG_SIGNAL_COUNT = 2 // Number of strong signals needed
        
        // GPS geofence parameters - UPDATE WITH YOUR REAL BUILDING LOCATION
        private const val BUILDING_LAT = 3.071421 // CS1 building latitude
        private const val BUILDING_LONG = 101.500136 // CS1 building longitude
        private const val BUILDING_RADIUS_METERS = 60.0 // Adjusted for 75m building size
        private const val BUILDING_EXIT_RADIUS_METERS = 120.0 // Larger for smooth detection
        
        // Update intervals
        private const val WIFI_SCAN_INTERVAL_MS = 10000L // 10 seconds
        private const val GPS_UPDATE_MIN_TIME_MS = 10000L // 10 seconds
        private const val GPS_UPDATE_MIN_DISTANCE_M = 5f // 5 meters
    }
    
    private val wifiManager by lazy {
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    }
    
    private val locationManager by lazy {
        context.applicationContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    }
    
    // Current state
    private var lastKnownLocation: Location? = null
    private var lastScanResults: List<ScanResult> = emptyList()
    private var inBuilding = false
    private var buildingConfidence = 0.0 // 0.0 to 1.0
    private var enableGPSFallback = true
    
    // Development mode flag (forces inside building state)
    private var developmentMode = false
    
    // Handlers for periodic updates
    private val handler = Handler(Looper.getMainLooper())
    private var wifiScanRunnable: Runnable? = null
    
    // Location listener for GPS updates
    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            lastKnownLocation = location
            updateBuildingDetection()
        }
        
        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String, status: Int, extras: Bundle) {}
        
        override fun onProviderEnabled(provider: String) {}
        
        override fun onProviderDisabled(provider: String) {}
    }
    
    init {
        // Start periodic WiFi scanning
        startWiFiScanning()
    }
    
    /**
     * Start periodic WiFi scanning to detect building presence
     */
    fun startWiFiScanning() {
        wifiScanRunnable = Runnable {
            try {
                // Request a WiFi scan
                if (ActivityCompat.checkSelfPermission(context, 
                    Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                    
                    wifiManager.startScan()
                    lastScanResults = wifiManager.scanResults ?: emptyList()
                    Log.d(TAG, "WiFi scan found ${lastScanResults.size} networks")
                    
                    // Update detection status
                    updateBuildingDetection()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error scanning WiFi: ${e.message}")
            }
            
            // Schedule next scan
            handler.postDelayed(wifiScanRunnable!!, WIFI_SCAN_INTERVAL_MS)
        }
        
        // Start scanning
        handler.post(wifiScanRunnable!!)
    }
    
    /**
     * Stop WiFi scanning when not needed
     */
    fun stopWiFiScanning() {
        wifiScanRunnable?.let {
            handler.removeCallbacks(it)
        }
    }
    
    /**
     * Start GPS location updates for outdoor detection
     */
    fun startGPSUpdates() {
        if (!enableGPSFallback) return
        
        try {
            if (ActivityCompat.checkSelfPermission(context,
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                
                locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    GPS_UPDATE_MIN_TIME_MS,
                    GPS_UPDATE_MIN_DISTANCE_M,
                    locationListener
                )
                Log.d(TAG, "GPS updates started")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error starting GPS updates: ${e.message}")
        }
    }
    
    /**
     * Stop GPS updates when not needed
     */
    fun stopGPSUpdates() {
        try {
            locationManager.removeUpdates(locationListener)
            Log.d(TAG, "GPS updates stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping GPS updates: ${e.message}")
        }
    }
    
    /**
     * Update building detection based on both WiFi and GPS data
     */
    private fun updateBuildingDetection() {
        // First check WiFi data (primary method)
        val wifiDetectionResult = isInBuildingByWiFi()
        
        // Then check GPS as fallback
        val gpsDetectionResult = if (enableGPSFallback && lastKnownLocation != null) {
            isInBuildingByLocation(lastKnownLocation)
        } else {
            null // GPS data not available
        }
        
        // Combine results - WiFi takes precedence inside building, GPS takes precedence outside
        when {
            // High confidence WiFi detection
            wifiDetectionResult.confidence > 0.8 -> {
                inBuilding = wifiDetectionResult.inside
                buildingConfidence = wifiDetectionResult.confidence
            }
            
            // GPS detection with WiFi backup
            gpsDetectionResult != null -> {
                if (gpsDetectionResult) {
                    // GPS says we're inside, use WiFi confidence
                    inBuilding = wifiDetectionResult.inside
                    buildingConfidence = wifiDetectionResult.confidence
                } else {
                    // GPS says we're outside, trust it
                    inBuilding = false
                    buildingConfidence = 0.9
                }
            }
            
            // Default to WiFi only
            else -> {
                inBuilding = wifiDetectionResult.inside
                buildingConfidence = wifiDetectionResult.confidence
            }
        }
        
        Log.d(TAG, "Building detection updated. Inside: $inBuilding, Confidence: $buildingConfidence")
    }
    
    /**
     * Check if the user is inside the building based on WiFi networks with confidence level
     * Enhanced for campus environments where buildings share the same SSID
     */
    private fun isInBuildingByWiFi(): DetectionResult {
        if (lastScanResults.isEmpty()) {
            return DetectionResult(inside = false, confidence = 0.5)
        }
        
        // Count building-specific networks with good signal
        var knownNetworksFound = 0
        var strongSignalCount = 0
        var averageRSSI = 0
        var knownBSSIDsFound = 0
        var campusWifiFound = false
        
        for (result in lastScanResults) {
            // Check SSID-based detection (for general campus presence)
            if (BUILDING_WIFI_SSIDS.contains(result.SSID)) {
                knownNetworksFound++
                campusWifiFound = true
                if (result.level > MIN_RSSI_THRESHOLD) {
                    averageRSSI += result.level
                    if (result.level > HIGH_CONFIDENCE_THRESHOLD) {
                        strongSignalCount++
                    }
                }
            }
            
            // PRIORITY: Check BSSID-based detection (building-specific fingerprinting)
            if (KNOWN_BUILDING_BSSIDS.containsKey(result.BSSID)) {
                knownBSSIDsFound++
                val expectedRSSI = KNOWN_BUILDING_BSSIDS[result.BSSID] ?: MIN_RSSI_THRESHOLD
                val diff = Math.abs(result.level - expectedRSSI)
                
                // Within 15dBm of expected value is good match (increased tolerance)
                if (diff < 15) {
                    strongSignalCount += 2 // Weight BSSID matches more heavily
                }
                
                Log.d(TAG, "Found building-specific BSSID: ${result.BSSID}, RSSI: ${result.level}, Expected: $expectedRSSI, Diff: $diff")
            }
        }
        
        // Calculate average RSSI if any building APs found
        if (knownNetworksFound > 0) {
            averageRSSI /= knownNetworksFound
        }
        
        // Enhanced logic for campus environments:
        // 1. If we have BSSID matches, prioritize those
        // 2. If no BSSID matches but campus WiFi present, lower confidence
        // 3. If neither, assume outside
        
        val inside = when {
            // High confidence: Multiple BSSID matches (building-specific)
            knownBSSIDsFound >= 2 -> true
            // Medium confidence: Single BSSID match with good signal
            knownBSSIDsFound >= 1 && strongSignalCount >= 2 -> true
            // Low confidence: Campus WiFi present but no building-specific BSSIDs
            campusWifiFound && strongSignalCount >= STRONG_SIGNAL_COUNT -> false // Likely different building
            // Very low confidence: Some campus networks but weak signals
            knownNetworksFound >= 1 && averageRSSI > MIN_RSSI_THRESHOLD -> false
            // Default: Outside campus entirely
            else -> false
        }
        
        // Enhanced confidence calculation for campus environments
        var confidence = 0.3 // Lower base confidence for shared SSID environments
        
        if (knownBSSIDsFound > 0) {
            // High confidence boost for building-specific BSSID matches
            confidence += knownBSSIDsFound * 0.25 // +0.25 per BSSID match
            confidence += strongSignalCount * 0.1 // Additional boost for strong signals
            Log.d(TAG, "BSSID-based detection: ${knownBSSIDsFound} BSSIDs found, confidence boosted")
        } else if (campusWifiFound) {
            // Campus WiFi present but no building-specific BSSIDs
            confidence += 0.1 // Small boost for being on campus
            Log.d(TAG, "Campus WiFi detected but no building-specific BSSIDs - likely different building")
        }
        
        if (knownNetworksFound > 0) {
            // Adjust based on signal strength
            confidence += (averageRSSI + 100) * 0.003 // Smaller signal strength influence
        }
        
        // Limit confidence to 0.0-1.0 range
        confidence = confidence.coerceIn(0.0, 1.0)
        
        Log.d(TAG, "WiFi Detection - Inside: $inside, Confidence: $confidence, BSSIDs: $knownBSSIDsFound, Campus WiFi: $campusWifiFound")
        
        return DetectionResult(inside, confidence)
    }
    
    /**
     * Check if the user is inside the building using GPS location
     */
    fun isInBuildingByLocation(location: Location?): Boolean {
        location ?: return false
        
        val distanceToBuilding = FloatArray(1)
        Location.distanceBetween(
            location.latitude, location.longitude,
            BUILDING_LAT, BUILDING_LONG, 
            distanceToBuilding
        )
        
        // Use hysteresis - different radius for entering vs exiting
        return if (inBuilding) {
            // Already inside, use larger radius for exiting
            distanceToBuilding[0] <= BUILDING_EXIT_RADIUS_METERS
        } else {
            // Outside, use smaller radius for entering
            distanceToBuilding[0] <= BUILDING_RADIUS_METERS
        }
    }
    
    /**
     * Check if the user is inside the building (convenience method)
     */
    fun isInBuilding(): Boolean {
        // Always return true if in development mode
        if (developmentMode) {
            return true
        }
        return inBuilding
    }
    
    /**
     * Get building detection confidence
     */
    fun getConfidence(): Double {
        return buildingConfidence
    }
    
    /**
     * Get the last known GPS location
     */
    fun getLastKnownLocation(): Location? {
        return lastKnownLocation
    }
    
    /**
     * Update app state based on location
     */
    fun updateAppState(navigationViewModel: NavigationViewModel) {
        // Update detection first
        updateBuildingDetection()
        
        if (isInBuilding()) {
            // Enable indoor navigation
            navigationViewModel.setIndoorModeEnabled(true)
            
            // Notify user they're in the building
            navigationViewModel.setLocationStatus(LocationStatus.INSIDE_BUILDING)
            Log.d(TAG, "User is inside the building (confidence: $buildingConfidence)")
        } else {
            // Show outdoor map or directions to building
            navigationViewModel.setIndoorModeEnabled(false)
            navigationViewModel.setLocationStatus(LocationStatus.OUTSIDE_BUILDING)
            Log.d(TAG, "User is outside the building (confidence: $buildingConfidence)")
        }
    }
    
    /**
     * Set development mode (forces inside building status)
     */
    fun setDevelopmentMode(enabled: Boolean) {
        developmentMode = enabled
        if (enabled) {
            // Force inside building status with high confidence
            inBuilding = true
            buildingConfidence = 1.0
            Log.d(TAG, "Development mode enabled - forced inside building status")
        } else {
            // Reset and use normal detection
            updateBuildingDetection()
            Log.d(TAG, "Development mode disabled - using normal detection")
        }
    }
    
    /**
     * Release resources when detector is no longer needed
     */
    fun shutdown() {
        stopWiFiScanning()
        stopGPSUpdates()
    }
    
    /**
     * Data class for WiFi detection results with confidence
     */
    data class DetectionResult(val inside: Boolean, val confidence: Double)
}
