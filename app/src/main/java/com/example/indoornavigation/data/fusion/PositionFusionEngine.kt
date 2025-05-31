package com.example.indoornavigation.data.fusion

import android.graphics.PointF
import android.hardware.SensorManager
import com.example.indoornavigation.service.TrilaterationService
import com.example.indoornavigation.data.models.Position
import com.example.indoornavigation.data.wifi.WiFiPositionEstimator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Position measurement with accuracy information for sensor fusion
 */
data class PositionMeasurement(
    val position: Position,
    val accuracy: Double
)

/**
 * Implements a Kalman filter for position estimation
 */
class KalmanFilter {
    // State variables
    private var x = 0.0
    private var y = 0.0
    private var floor = 0
    
    // Error covariance matrix
    private var p00 = 1.0 // Variance in x
    private var p01 = 0.0 // Covariance of x and y
    private var p10 = 0.0 // Covariance of x and y
    private var p11 = 1.0 // Variance in y
    
    // Process noise (higher = more responsive to measurements)
    private var processNoise = 0.01
    
    /**
     * Update the filter with new measurements
     * @param measurements List of position measurements with accuracy information
     * @return Fused position estimate
     */
    fun update(measurements: List<PositionMeasurement>): Position? {
        if (measurements.isEmpty()) {
            return null
        }
        
        // Add process noise to covariance matrix (prediction step)
        p00 += processNoise
        p11 += processNoise
        
        // Handle floor changes separately (discrete value)
        val floorVotes = mutableMapOf<Int, Double>()
        
        // Update with each measurement (correction step)
        for (measurement in measurements) {
            // Apply weight to floor vote based on inverse accuracy
            val floorWeight = 1.0 / measurement.accuracy
            floorVotes[measurement.position.floor] = 
                (floorVotes[measurement.position.floor] ?: 0.0) + floorWeight
            
            // For continuous variables (x, y), apply Kalman filter
            
            // Calculate Kalman gain
            val s00 = p00 + measurement.accuracy
            val s11 = p11 + measurement.accuracy
            
            val k00 = p00 / s00
            val k10 = p10 / s00
            val k01 = p01 / s11
            val k11 = p11 / s11
            
            // Update state
            val innovation0 = measurement.position.x - x
            val innovation1 = measurement.position.y - y
            
            x += k00 * innovation0 + k01 * innovation1
            y += k10 * innovation0 + k11 * innovation1
            
            // Update covariance matrix
            p00 = (1 - k00) * p00
            p01 = (1 - k00) * p01
            p10 = p10 - k10 * p00
            p11 = (1 - k11) * p11
        }
        
        // Determine floor by weighted voting
        floor = floorVotes.maxByOrNull { it.value }?.key ?: floor
        
        return Position(x, y, floor)
    }
    
    /**
     * Reset the filter state
     */
    fun reset() {
        x = 0.0
        y = 0.0
        floor = 0
        p00 = 1.0
        p01 = 0.0
        p10 = 0.0
        p11 = 1.0
    }
    
    /**
     * Set process noise
     */
    fun setProcessNoise(noise: Double) {
        processNoise = noise
    }
}

/**
 * Engine for fusing position data from multiple sources
 */
class PositionFusionEngine(
    private val beaconPositioner: TrilaterationService,
    private val wifiPositioner: WiFiPositionEstimator,
    private val sensorManager: SensorManager
) {
    companion object {
        // Position source weights
        const val BEACON_ACCURACY = 2.0   // Lower is better
        const val WIFI_ACCURACY = 4.0     // Lower is better
        const val DEAD_RECKONING_ACCURACY = 6.0 // Lower is better, increases over time
    }
    
    // Kalman filter for position estimation
    private val kalmanFilter = KalmanFilter()
    
    // Last known positions from different sources
    private var lastBeaconPosition: Position? = null
    private var lastWifiPosition: Position? = null
    private var lastDeadReckoningOffset: PointF = PointF(0f, 0f)
    private var deadReckoningBasePosition: Position? = null
    private var lastDeadReckoningTimestamp = 0L
    
    // Step counter for dead reckoning
    private var stepCount = 0
    private var currentHeading = 0f // in degrees
    
    // Position output
    private val _fusedPosition = MutableStateFlow<Position?>(null)
    val fusedPosition: StateFlow<Position?> = _fusedPosition.asStateFlow()
    
    // Position accuracy
    private val _positionAccuracy = MutableStateFlow(5.0)
    val positionAccuracy: StateFlow<Double> = _positionAccuracy.asStateFlow()
    
    /**
     * Update positions from all sources and apply fusion
     * @return The fused position estimate
     */
    fun updatePositions(): Position? {
        // Get latest readings
        val trilaterationResult = beaconPositioner.calculatePosition(emptyList())
        lastBeaconPosition = trilaterationResult?.position
        
        // Get WiFi position (in a real implementation, we'd cache the scan results
        // and pass them to the estimator)
        val mockWifiScan = getCurrentWifiScan() 
        lastWifiPosition = wifiPositioner.estimatePosition(mockWifiScan)
        
        // Update dead reckoning
        updateDeadReckoning()
        
        // Apply Kalman filter
        val fusedPosition = kalmanFilter.update(
            listOfNotNull(
                lastBeaconPosition?.let { PositionMeasurement(it, BEACON_ACCURACY) },
                lastWifiPosition?.let { PositionMeasurement(it, WIFI_ACCURACY) },
                getDeadReckoningBasedPosition()?.let { PositionMeasurement(it, DEAD_RECKONING_ACCURACY) }
            )
        )
        
        // Update output flows
        fusedPosition?.let {
            _fusedPosition.value = it
            
            // Calculate accuracy estimate based on the number and quality of sources
            val sourceCount = listOfNotNull(lastBeaconPosition, lastWifiPosition, 
                getDeadReckoningBasedPosition()).size
            val accuracyEstimate = when(sourceCount) {
                0 -> 10.0
                1 -> 5.0
                2 -> 3.0
                else -> 2.0
            }
            _positionAccuracy.value = accuracyEstimate
        }
        
        return fusedPosition
    }
    
    /**
     * Update dead reckoning position based on step detection and heading
     */
    private fun updateDeadReckoning() {
        // In a real implementation, we would:
        // 1. Register sensors for step detection and heading
        // 2. Process raw sensor data
        // 3. Apply step length estimation and direction
        
        // This is a minimal placeholder implementation
        val currentTime = System.currentTimeMillis()
        
        // If we have a previous timestamp
        if (lastDeadReckoningTimestamp > 0) {
            val elapsed = currentTime - lastDeadReckoningTimestamp
            
            // Mock step detection (would be based on accelerometer in reality)
            if (elapsed > 500) { // assume 1 step every 500ms for demonstration
                stepCount++
                
                // Convert heading to radians
                val headingRad = Math.toRadians(currentHeading.toDouble())
                
                // Assume average step length of 0.75m
                val stepLength = 0.75f
                
                // Update dead reckoning offset based on step and heading
                lastDeadReckoningOffset.x += (stepLength * Math.sin(headingRad)).toFloat()
                lastDeadReckoningOffset.y += (stepLength * Math.cos(headingRad)).toFloat()
                
                lastDeadReckoningTimestamp = currentTime
            }
        } else {
            // First time
            lastDeadReckoningTimestamp = currentTime
            
            // Initialize base position from another source
            if (deadReckoningBasePosition == null) {
                deadReckoningBasePosition = lastBeaconPosition ?: lastWifiPosition
            }
        }
    }
    
    /**
     * Get the dead reckoning based position
     */
    private fun getDeadReckoningBasedPosition(): Position? {
        val basePosition = deadReckoningBasePosition ?: return null
        
        return Position(
            x = basePosition.x + lastDeadReckoningOffset.x,
            y = basePosition.y + lastDeadReckoningOffset.y,
            floor = basePosition.floor
        )
    }
    
    /**
     * Get current WiFi scan results
     * In a real implementation, this would come from the actual WiFi scan
     */
    private fun getCurrentWifiScan(): Map<String, Int> {
        // This is a stub - in a real app, we'd use the WifiPositioningManager
        return mapOf(
            "00:11:22:33:44:55" to -65,
            "00:11:22:33:44:56" to -70,
            "00:11:22:33:44:57" to -75
        )
    }
    
    /**
     * Reset the dead reckoning position using an absolute position reference
     */
    fun resetDeadReckoning(referencePosition: Position) {
        deadReckoningBasePosition = referencePosition
        lastDeadReckoningOffset = PointF(0f, 0f)
        stepCount = 0
    }
    
    /**
     * Update heading from compass/gyroscope
     */
    fun updateHeading(heading: Float) {
        currentHeading = heading
    }
    
    /**
     * Set process noise for the Kalman filter
     */
    fun setProcessNoise(noise: Double) {
        kalmanFilter.setProcessNoise(noise)
    }
    
    /**
     * Reset the fusion engine
     */
    fun reset() {
        lastBeaconPosition = null
        lastWifiPosition = null
        lastDeadReckoningOffset = PointF(0f, 0f)
        deadReckoningBasePosition = null
        lastDeadReckoningTimestamp = 0L
        stepCount = 0
        currentHeading = 0f
        kalmanFilter.reset()
    }
}