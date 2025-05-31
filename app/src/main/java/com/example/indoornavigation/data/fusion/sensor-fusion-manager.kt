package com.example.indoornavigation.data.fusion

import android.content.Context
import com.example.indoornavigation.data.bluetooth.BleManager
import com.example.indoornavigation.data.models.Beacon
import com.example.indoornavigation.data.models.Position
import com.example.indoornavigation.data.wifi.WifiPositioningManager
import com.example.indoornavigation.data.repository.BeaconRepository
import com.example.indoornavigation.utils.PositioningUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Manager class for sensor fusion between BLE and Wi-Fi positioning
 * Implements a Kalman filter for fusing multiple position sources
 */
class SensorFusionManager(
    private val bleManager: BleManager,
    private val wifiManager: WifiPositioningManager,
    private val coroutineScope: CoroutineScope,
    private val context: Context // Add the context parameter
) {
    // Fused position data
    private val _fusedPosition = MutableStateFlow<Position?>(null)
    val fusedPosition: StateFlow<Position?> = _fusedPosition.asStateFlow()
    
    // Sensor weights (adjustable based on confidence)
    private var bleWeight = 0.7  // Default weights giving more emphasis to BLE
    private var wifiWeight = 0.3
    
    // Kalman filter state
    private var kalmanGain = 0.0
    private var errorCovariance = 1.0
    private val processNoise = 0.01
    private val measurementNoise = 0.5
    
    // Last known positions from each source
    private var lastBlePosition: Position? = null
    private var lastWifiPosition: Position? = null
    
    // Shared repository instance
    private val beaconRepository = BeaconRepository(context)
    
    init {
        // Start collecting BLE and Wi-Fi data
        collectPositionData()
    }
    
    /**
     * Collect position data from BLE and Wi-Fi sources
     */
    private fun collectPositionData() {
        coroutineScope.launch(Dispatchers.Default) {
            // Process BLE beacons and calculate position
            bleManager.detectedBeacons.collectLatest { beacons ->
                // Simulate BLE position calculation here
                // In a real implementation, this would be handled by a PositioningUtils class
                val nearestBeacons = beacons.values.sortedBy { it.distance }.take(3)
                if (nearestBeacons.size >= 3) {
                    // Calculate BLE position (placeholder for actual trilateration)
                    val blePosition = calculateBlePosition(nearestBeacons)
                    lastBlePosition = blePosition
                    
                    // Update fused position
                    updateFusedPosition()
                }
            }
        }
        
        coroutineScope.launch(Dispatchers.Default) {
            // Process Wi-Fi access points and calculate position
            wifiManager.detectedAccessPoints.collectLatest { accessPoints ->
                // Calculate Wi-Fi position
                val wifiPosition = wifiManager.estimatePosition()
                if (wifiPosition != null) {
                    lastWifiPosition = wifiPosition
                    
                    // Update fused position
                    updateFusedPosition()
                }
            }
        }
    }
    
    /**
     * Update the fused position based on available sensor data
     */
    private fun updateFusedPosition() {
        // Return early if no position data available
        if (lastBlePosition == null && lastWifiPosition == null) {
            return
        }
        
        // If only one position source is available, use it
        if (lastBlePosition == null) {
            _fusedPosition.value = lastWifiPosition
            return
        }
        if (lastWifiPosition == null) {
            _fusedPosition.value = lastBlePosition
            return
        }
        
        // Both position sources available, perform sensor fusion
        val ble = lastBlePosition!!
        val wifi = lastWifiPosition!!
        
        // Check if on the same floor
        if (ble.floor != wifi.floor) {
            // If on different floors, rely more on BLE for floor determination
            val floorConfidenceBle = 0.8
            val floorConfidenceWifi = 0.2
            
            val floorBle = ble.floor.toDouble() * floorConfidenceBle
            val floorWifi = wifi.floor.toDouble() * floorConfidenceWifi
            val fusedFloor = Math.round(floorBle + floorWifi).toInt()
            
            // Adjust weights based on floor match
            adjustWeights(ble, wifi)
            
            // Weighted average for position
            val fusedX = ble.x * bleWeight + wifi.x * wifiWeight
            val fusedY = ble.y * bleWeight + wifi.y * wifiWeight
            
            val fusedPosition = Position(fusedX, fusedY, fusedFloor)
            
            // Apply Kalman filter for smoothing
            _fusedPosition.value = applyKalmanFilter(fusedPosition)
        } else {
            // Same floor, straightforward fusion
            adjustWeights(ble, wifi)
            
            // Weighted average for position
            val fusedX = ble.x * bleWeight + wifi.x * wifiWeight
            val fusedY = ble.y * bleWeight + wifi.y * wifiWeight
            
            val fusedPosition = Position(fusedX, fusedY, ble.floor)
            
            // Apply Kalman filter for smoothing
            _fusedPosition.value = applyKalmanFilter(fusedPosition)
        }
    }
    
    /**
     * Adjust weights based on signal quality and position confidence
     */
    private fun adjustWeights(blePosition: Position, wifiPosition: Position) {
        // This is a simplified version of weight adjustment
        // In a real implementation, this would consider signal strength, number of beacons/APs,
        // historical accuracy, etc.
        
        // Example: Adjust based on number of beacons/APs
        val bleBeaconCount = bleManager.detectedBeacons.value.size
        val wifiApCount = wifiManager.detectedAccessPoints.value.size
        
        if (bleBeaconCount > 3 && wifiApCount > 3) {
            // Both have good coverage, use default weights
            bleWeight = 0.7
            wifiWeight = 0.3
        } else if (bleBeaconCount > 3) {
            // More BLE beacons, increase BLE weight
            bleWeight = 0.85
            wifiWeight = 0.15
        } else if (wifiApCount > 3) {
            // More Wi-Fi APs, increase Wi-Fi weight
            bleWeight = 0.4
            wifiWeight = 0.6
        } else {
            // Few beacons/APs, use default weights
            bleWeight = 0.7
            wifiWeight = 0.3
        }
    }
    
    /**
     * Apply Kalman filter for position smoothing
     */
    private fun applyKalmanFilter(measurement: Position): Position {
        // If this is the first measurement, return it directly
        val previousPosition = _fusedPosition.value ?: return measurement
        
        // Prediction step
        // (No state transition model needed for simple static positioning)
        
        // Update error covariance with process noise
        errorCovariance += processNoise
        
        // Update Kalman gain
        kalmanGain = errorCovariance / (errorCovariance + measurementNoise)
        
        // Update position estimate
        val filteredX = previousPosition.x + kalmanGain * (measurement.x - previousPosition.x)
        val filteredY = previousPosition.y + kalmanGain * (measurement.y - previousPosition.y)
        
        // Update error covariance
        errorCovariance = (1 - kalmanGain) * errorCovariance
        
        // Use the floor from the measurement (floors are discrete so filtering doesn't make sense)
        return Position(filteredX, filteredY, measurement.floor)
    }
    
    /**
     * Calculate BLE position using trilateration or weighted average
     * Depending on the number of available beacons with known positions
     */
    private fun calculateBlePosition(nearestBeacons: List<Beacon>): Position {
        // Get beacons with known positions from repository
        val managedBeacons = beaconRepository.beacons.value
        
        // Match detected beacons with their known positions
        val beaconsWithPositions = nearestBeacons.mapNotNull { beacon ->
            val managedBeacon = managedBeacons.find { it.id == beacon.id }
            if (managedBeacon != null) {
                Pair(
                    Position(managedBeacon.x, managedBeacon.y, managedBeacon.floor),
                    beacon.distance
                )
            } else {
                null
            }
        }
        
        // If we don't have enough beacons with known positions, return null
        if (beaconsWithPositions.size < 3) {
            // If we have at least one beacon, use weighted average
            if (beaconsWithPositions.isNotEmpty()) {
                val positions = beaconsWithPositions.map { it.first }
                val weights = beaconsWithPositions.map { 1.0 / Math.pow(it.second.coerceAtLeast(0.1), 2.0) }
                return PositioningUtils.calculatePositionByWeightedAverage(positions, weights)
            }
            
            // Default floor (0) if we can't determine it
            val floor = beaconsWithPositions.firstOrNull()?.first?.floor ?: 0
            
            // Return a default position if we don't have enough data
            return Position(0.0, 0.0, floor)
        }
        
        // Use trilateration if we have 3 or more beacons with known positions
        val positions = beaconsWithPositions.map { it.first }
        val distances = beaconsWithPositions.map { it.second }
        
        try {
            // Use the PositioningUtils class for trilateration
            return PositioningUtils.calculatePositionByTrilateration(positions, distances)
        } catch (e: Exception) {
            // Fallback to weighted average if trilateration fails
            val weights = beaconsWithPositions.map { 1.0 / Math.pow(it.second.coerceAtLeast(0.1), 2.0) }
            return PositioningUtils.calculatePositionByWeightedAverage(positions, weights)
        }
    }
    
    /**
     * Start scanning with both sensors
     */
    fun startScanning() {
        bleManager.startScanning()
        wifiManager.startScanning()
    }
    
    /**
     * Stop scanning with both sensors
     */
    fun stopScanning() {
        bleManager.stopScanning()
        wifiManager.stopScanning()
    }
    
    /**
     * Set process noise for Kalman filter
     * Higher values make the filter respond faster to changes
     */
    fun setProcessNoise(noise: Double) {
        // processNoise = noise
    }
    
    /**
     * Set measurement noise for Kalman filter
     * Higher values make the filter trust new measurements less
     */
    fun setMeasurementNoise(noise: Double) {
        // measurementNoise = noise
    }
}
