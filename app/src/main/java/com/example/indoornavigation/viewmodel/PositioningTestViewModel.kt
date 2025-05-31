package com.example.indoornavigation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.indoornavigation.data.models.PositioningState
import com.example.indoornavigation.data.models.PositioningSources
import com.example.indoornavigation.data.models.TestBeacon
import com.example.indoornavigation.data.models.TestPosition
import com.example.indoornavigation.data.models.TestSensor
import com.example.indoornavigation.data.models.TestWiFi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import android.util.Log
import kotlin.random.Random

class PositioningTestViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(PositioningState())
    val uiState: StateFlow<PositioningState> = _uiState.asStateFlow()
    
    private var positioningJob: Job? = null
    private var scanIntervalMs: Long = 1000 // Default 1 second
    
    fun startPositioning() {
        positioningJob?.cancel()
        positioningJob = viewModelScope.launch {
            _uiState.update { it.copy(isScanning = true) }
            
            // Simulate positioning updates
            while (isActive) {
                simulatePositioningUpdate()
                delay(scanIntervalMs) // Update based on scan interval
            }
        }
    }
    
    fun stopPositioning() {
        positioningJob?.cancel()
        _uiState.update { it.copy(isScanning = false) }
    }
    
    fun toggleSource(source: Int) {
        _uiState.update { state ->
            val newMask = state.activeSourcesMask xor source
            state.copy(activeSourcesMask = newMask)
        }
    }
    
    fun resetPosition() {
        _uiState.update { PositioningState() }
    }
    
    fun setScanInterval(intervalMs: Long) {
        if (intervalMs < 100) return // Prevent too fast scanning
        
        scanIntervalMs = intervalMs
        
        // Restart positioning with new interval if currently scanning
        if (_uiState.value.isScanning) {
            startPositioning()
        }
    }
    
    fun forceRescan() {
        if (_uiState.value.isScanning) {
            // Cancel current job and start a new one immediately
            positioningJob?.cancel()
            startPositioning()
        } else {
            // Just do a single update
            viewModelScope.launch {
                simulatePositioningUpdate()
            }
        }
    }
    
    fun saveTestData() {
        val currentState = _uiState.value
        val timestamp = System.currentTimeMillis()
        
        // In a real implementation, you'd save this data to a file or database
        // For this example, we'll just log it
        Log.d("PositioningTest", "Saved test data at $timestamp")
        Log.d("PositioningTest", "Current position: ${currentState.currentPosition}")
        Log.d("PositioningTest", "BLE position: ${currentState.blePosition}")
        Log.d("PositioningTest", "WiFi position: ${currentState.wifiPosition}")
        Log.d("PositioningTest", "Dead reckoning position: ${currentState.deadReckoningPosition}")
        Log.d("PositioningTest", "Fused position: ${currentState.fusedPosition}")
        Log.d("PositioningTest", "Detected beacons: ${currentState.detectedBeacons.size}")
        Log.d("PositioningTest", "Detected WiFi APs: ${currentState.detectedWiFi.size}")
    }
    
    private fun simulatePositioningUpdate() {
        val currentState = _uiState.value
        val time = System.currentTimeMillis()
        
        // Simulate BLE positioning
        val blePosition = if (currentState.activeSourcesMask and PositioningSources.BLE != 0) {
            TestPosition(
                x = Random.nextDouble(0.0, 100.0),
                y = Random.nextDouble(0.0, 100.0),
                floor = Random.nextInt(0, 3),
                accuracy = Random.nextDouble(0.0, 5.0),
                timestamp = time
            )
        } else null
        
        // Simulate WiFi positioning
        val wifiPosition = if (currentState.activeSourcesMask and PositioningSources.WIFI != 0) {
            TestPosition(
                x = Random.nextDouble(0.0, 100.0),
                y = Random.nextDouble(0.0, 100.0),
                floor = Random.nextInt(0, 3),
                accuracy = Random.nextDouble(0.0, 8.0),
                timestamp = time
            )
        } else null
        
        // Simulate dead reckoning
        val deadReckoningPosition = if (currentState.activeSourcesMask and PositioningSources.DEAD_RECKONING != 0) {
            val lastPos = currentState.currentPosition
            TestPosition(
                x = lastPos.x + Random.nextDouble(-1.0, 1.0),
                y = lastPos.y + Random.nextDouble(-1.0, 1.0),
                floor = lastPos.floor,
                accuracy = Random.nextDouble(0.0, 3.0),
                timestamp = time
            )
        } else null
        
        // Simulate sensor fusion
        val fusedPosition = if (currentState.activeSourcesMask and PositioningSources.SENSOR_FUSION != 0) {
            val positions = listOfNotNull(blePosition, wifiPosition, deadReckoningPosition)
            if (positions.isNotEmpty()) {
                TestPosition(
                    x = positions.map { it.x }.average(),
                    y = positions.map { it.y }.average(),
                    floor = positions.firstOrNull()?.floor ?: 0,
                    accuracy = positions.map { it.accuracy }.average(),
                    timestamp = time
                )
            } else null
        } else null
        
        // Simulate beacon detection
        val beacons = if (currentState.activeSourcesMask and PositioningSources.BLE != 0) {
            generateRealisticBeacons(Random.nextInt(3, 8), time)
        } else emptyList()
        
        // Simulate WiFi detection
        val wifiAPs = if (currentState.activeSourcesMask and PositioningSources.WIFI != 0) {
            List(Random.nextInt(5, 12)) { index ->
                TestWiFi(
                    ssid = "WiFi-${index + 1}",
                    bssid = "XX:XX:XX:XX:XX:${index.toString(16).padStart(2, '0')}",
                    rssi = Random.nextInt(-85, -30),
                    timestamp = time
                )
            }
        } else emptyList()
        
        // Simulate sensor data
        val sensorData = mapOf(
            "accelerometer" to TestSensor(
                type = "accelerometer",
                values = floatArrayOf(
                    Random.nextFloat() * 2 - 1,
                    Random.nextFloat() * 2 - 1,
                    9.8f + Random.nextFloat() * 0.2f - 0.1f
                ),
                timestamp = time
            ),
            "gyroscope" to TestSensor(
                type = "gyroscope",
                values = floatArrayOf(
                    Random.nextFloat() * 0.1f - 0.05f,
                    Random.nextFloat() * 0.1f - 0.05f,
                    Random.nextFloat() * 0.1f - 0.05f
                ),
                timestamp = time
            ),
            "magnetometer" to TestSensor(
                type = "magnetometer",
                values = floatArrayOf(
                    Random.nextFloat() * 60 - 30,
                    Random.nextFloat() * 60 - 30,
                    Random.nextFloat() * 60 - 30
                ),
                timestamp = time
            )
        )
        
        // Update state
        _uiState.update { state ->
            state.copy(
                currentPosition = fusedPosition ?: blePosition ?: wifiPosition ?: deadReckoningPosition ?: state.currentPosition,
                blePosition = blePosition,
                wifiPosition = wifiPosition,
                deadReckoningPosition = deadReckoningPosition,
                fusedPosition = fusedPosition,
                detectedBeacons = beacons,
                detectedWiFi = wifiAPs,
                sensorData = sensorData
            )
        }
    }
    
    /**
     * Generate realistic beacon data with proper names and IDs
     */
    private fun generateRealisticBeacons(count: Int, timestamp: Long): List<TestBeacon> {
        // Different beacon types and manufacturers for more realistic simulation
        val beaconTypes = listOf(
            Pair("iBeacon", "Apple"),
            Pair("Eddystone", "Google"),
            Pair("AltBeacon", "Radius"),
            Pair("ESP32", "Espressif"),
            Pair("CC2650", "TI"),
            Pair("nRF52", "Nordic"),
            Pair("BLE", "Generic"),
            Pair("HM-10", "JDY")
        )
        
        return List(count) { index ->
            // Generate a realistic MAC address
            val macAddress = generateMacAddress()
            val shortMac = macAddress.takeLast(5).replace(":", "")
            
            // Select a random beacon type
            val (beaconType, manufacturer) = beaconTypes.random()
            
            // Create beacon name based on type and MAC
            val beaconName = "$beaconType-$shortMac"
            
            TestBeacon(
                id = macAddress,
                name = beaconName,
                rssi = Random.nextInt(-90, -40),
                distance = Random.nextDouble(0.0, 10.0),
                timestamp = timestamp
            )
        }
    }
    
    /**
     * Generate a realistic MAC address
     */
    private fun generateMacAddress(): String {
        val hexChars = "0123456789ABCDEF"
        return buildString {
            for (i in 0 until 6) {
                append(hexChars.random())
                append(hexChars.random())
                if (i < 5) append(":")
            }
        }
    }
}