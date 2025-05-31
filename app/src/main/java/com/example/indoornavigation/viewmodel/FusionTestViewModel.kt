package com.example.indoornavigation.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.indoornavigation.data.BeaconInfo
import com.example.indoornavigation.data.PositionData
import com.example.indoornavigation.data.PositioningMethod
import com.example.indoornavigation.data.TrilaterationCalculator
import com.example.indoornavigation.data.TrilaterationState
import com.example.indoornavigation.data.bluetooth.BleManager
import com.example.indoornavigation.data.fusion.SensorFusionManager
import com.example.indoornavigation.data.sensors.DeadReckoningManager
import com.example.indoornavigation.data.wifi.WifiPositioningManager
import com.example.indoornavigation.ui.TrilaterationViewModelInterface
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.log10
import kotlin.math.pow
import kotlin.random.Random

/**
 * ViewModel for fusion testing that combines beacon selection UI with sensor fusion
 * for accurate positioning using all available methods
 */
class FusionTestViewModel(application: Application) : AndroidViewModel(application), TrilaterationViewModelInterface {
    // UI state for the trilateration view
    private val _uiState = MutableStateFlow(TrilaterationState())
    override val uiState: StateFlow<TrilaterationState> = _uiState.asStateFlow()
    
    // Positioning components
    private val bleManager = BleManager(application.applicationContext)
    private val wifiManager = WifiPositioningManager(application.applicationContext)
    private val deadReckoningManager = DeadReckoningManager(application.applicationContext)
    
    // Sensor fusion manager
    private val sensorFusionManager = SensorFusionManager(
        bleManager,
        wifiManager,
        viewModelScope,
        application.applicationContext
    )
    
    // Additional state for combined position info
    private val _fusedPosition = MutableStateFlow<PositionData?>(null)
    val fusedPosition: StateFlow<PositionData?> = _fusedPosition.asStateFlow()
    
    private val _wifiPosition = MutableStateFlow<PositionData?>(null)
    val wifiPosition: StateFlow<PositionData?> = _wifiPosition.asStateFlow()
    
    private val _deadReckoningPosition = MutableStateFlow<PositionData?>(null)
    val deadReckoningPosition: StateFlow<PositionData?> = _deadReckoningPosition.asStateFlow()
    
    private var scanningJob: Job? = null
    private var fusionJob: Job? = null
    
    init {
        // Initialize with empty beacons list - we'll populate with real scanning
        _uiState.update { it.copy(availableBeacons = emptyList()) }
        
        // Start collecting sensor fusion data
        collectFusionData()
        
        // Start collecting BLE beacon data
        collectBLEBeaconData()
    }
    
    /**
     * Collect position data from the sensor fusion manager
     */
    private fun collectFusionData() {
        fusionJob = viewModelScope.launch {
            sensorFusionManager.fusedPosition.collectLatest { fusedPos ->
                if (fusedPos != null) {
                    // Convert Position to PositionData
                    val fusedPositionData = PositionData(
                        fusedPos.x.toFloat(),
                        fusedPos.y.toFloat(),
                        1.0f  // Default accuracy estimate
                    )
                    _fusedPosition.value = fusedPositionData
                    
                    // Also update the user position in the UI state for visualization
                    _uiState.update {
                        it.copy(userPosition = fusedPositionData)
                    }
                }
            }
        }
        
        // Also collect WiFi position data
        viewModelScope.launch {
            wifiManager.detectedAccessPoints.collectLatest { accessPoints ->
                val wifiPos = wifiManager.estimatePosition()
                if (wifiPos != null) {
                    val wifiPositionData = PositionData(
                        wifiPos.x.toFloat(),
                        wifiPos.y.toFloat(),
                        2.0f  // WiFi typically less accurate
                    )
                    _wifiPosition.value = wifiPositionData
                }
            }
        }
        
        // Collect dead reckoning position
        viewModelScope.launch {
            deadReckoningManager.estimatedPosition.collectLatest { drPos ->
                if (drPos != null) {
                    val drPositionData = PositionData(
                        drPos.x.toFloat(),
                        drPos.y.toFloat(),
                        3.0f  // Dead reckoning can drift over time
                    )
                    _deadReckoningPosition.value = drPositionData
                }
            }
        }
    }
    
    /**
     * Collect BLE beacon data from the BleManager
     */
    private fun collectBLEBeaconData() {
        viewModelScope.launch {
            bleManager.detectedBeacons.collectLatest { bleBeacons ->
                // Convert BLE beacons to BeaconInfo objects
                val beaconInfoList = bleBeacons.values.map { beacon ->
                    // Try to find if this beacon was already in our list to preserve its connected state
                    val existingBeacon = _uiState.value.availableBeacons.find { it.id == beacon.id }
                    val isConnected = existingBeacon?.isConnected ?: false
                    
                    BeaconInfo(
                        id = beacon.id,
                        name = beacon.name,
                        uuid = beacon.id, // Use MAC address as UUID
                        major = 1,
                        minor = 1,
                        rssi = beacon.rssi,
                        txPower = -59, // Default transmit power at 1m
                        distance = beacon.distance.toFloat(),
                        isConnected = isConnected,
                        position = existingBeacon?.position // Keep position if we had it
                    )
                }.sortedByDescending { it.rssi } // Sort by strongest signal
                
                _uiState.update {
                    it.copy(availableBeacons = beaconInfoList)
                }
                
                // Update the connected beacons list
                val connectedBeaconIds = _uiState.value.connectedBeacons.map { it.id }
                val updatedConnectedBeacons = beaconInfoList
                    .filter { connectedBeaconIds.contains(it.id) }
                
                if (updatedConnectedBeacons.isNotEmpty()) {
                    _uiState.update { it.copy(connectedBeacons = updatedConnectedBeacons) }
                }
            }
        }
    }
    
    override fun connectBeacon(beaconId: String) {
        val state = _uiState.value
        val beacon = state.availableBeacons.find { it.id == beaconId }
        
        // Skip if beacon not found
        if (beacon == null) return
        
        val updatedBeacon = beacon.copy(isConnected = true)
        
        val updatedAvailable = state.availableBeacons.map {
            if (it.id == beaconId) updatedBeacon else it
        }
        
        val connectedBeacons = state.connectedBeacons.toMutableList()
        if (!connectedBeacons.any { it.id == beaconId }) {
            connectedBeacons.add(updatedBeacon)
        }
        
        _uiState.update { 
            it.copy(
                availableBeacons = updatedAvailable,
                connectedBeacons = connectedBeacons
            )
        }
        
        // Start positioning if we have enough beacons
        if (connectedBeacons.size >= 3 && !_uiState.value.isScanning) {
            startScanning()
        }
        
        // Update beacon positions in the sensor fusion system
        updateBeaconPositionsInFusion()
    }
    
    override fun disconnectBeacon(beaconId: String) {
        _uiState.update { state ->
            val updatedAvailable = state.availableBeacons.map {
                if (it.id == beaconId) it.copy(isConnected = false) else it
            }
            
            val connectedBeacons = state.connectedBeacons.filter { it.id != beaconId }
            
            state.copy(
                availableBeacons = updatedAvailable,
                connectedBeacons = connectedBeacons
            )
        }
    }
    
    /**
     * Update beacon positions in the sensor fusion system
     */
    private fun updateBeaconPositionsInFusion() {
        // In a real implementation, we would update the beacon positions in the BleManager
        // that's being used by the SensorFusionManager
        // For demonstration purposes, we'll just simulate this
    }
    
    override fun startScanning() {
        scanningJob?.cancel()
        scanningJob = viewModelScope.launch {
            _uiState.update { it.copy(isScanning = true) }
            
            // Start the sensor fusion positioning
            sensorFusionManager.startScanning()
            
            // Start real BLE scanning
            bleManager.startScanning()
        }
    }
    
    override fun stopScanning() {
        scanningJob?.cancel()
        sensorFusionManager.stopScanning()
        bleManager.stopScanning()
        _uiState.update { it.copy(isScanning = false) }
    }
    
    override fun updatePathLossExponent(value: Float) {
        _uiState.update { it.copy(pathLossExponent = value) }
    }
    
    override fun setPositioningMethod(method: PositioningMethod) {
        _uiState.update { it.copy(positioningMethod = method) }
    }
    
    override fun onCleared() {
        super.onCleared()
        stopScanning()
        fusionJob?.cancel()
    }
    
    /**
     * Factory for creating FusionTestViewModel with Application context
     */
    class Factory(private val application: Application) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(FusionTestViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return FusionTestViewModel(application) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
