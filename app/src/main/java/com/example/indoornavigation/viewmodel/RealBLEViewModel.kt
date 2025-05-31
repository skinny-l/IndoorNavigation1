package com.example.indoornavigation.viewmodel

import android.Manifest
import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.indoornavigation.data.BeaconInfo
import com.example.indoornavigation.data.FingerprintData
import com.example.indoornavigation.data.FingerprintDatabaseGenerator
import com.example.indoornavigation.data.KalmanFilterState
import com.example.indoornavigation.data.PositionData
import com.example.indoornavigation.data.PositioningMethod
import com.example.indoornavigation.data.TrilaterationCalculator
import com.example.indoornavigation.data.TrilaterationState
import com.example.indoornavigation.data.bluetooth.BleManager
import com.example.indoornavigation.data.models.Beacon
import com.example.indoornavigation.data.models.ManagedBeacon
import com.example.indoornavigation.data.models.Position
import com.example.indoornavigation.data.models.TrilaterationStatus
import com.example.indoornavigation.ui.TrilaterationViewModelInterface
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.UUID
import kotlin.math.sqrt
import kotlin.random.Random

class RealBLEViewModel(application: Application) : AndroidViewModel(application), TrilaterationViewModelInterface {
    private val _uiState = MutableStateFlow(TrilaterationState())
    override val uiState: StateFlow<TrilaterationState> = _uiState.asStateFlow()
    
    // Current floor for beacon management
    private val _currentFloor = MutableStateFlow(0)
    val currentFloor: StateFlow<Int> = _currentFloor.asStateFlow()
    
    // List of managed beacons
    private val _managedBeacons = MutableStateFlow<List<ManagedBeacon>>(emptyList())
    val managedBeacons: StateFlow<List<ManagedBeacon>> = _managedBeacons.asStateFlow()
    
    // Trilateration status for UI updates
    private val _trilaterationStatus = MutableStateFlow(TrilaterationStatus())
    val trilaterationStatus: StateFlow<TrilaterationStatus> = _trilaterationStatus.asStateFlow()
    
    // Demo mode flag
    private val _demoMode = MutableStateFlow(false)
    val demoMode: StateFlow<Boolean> = _demoMode.asStateFlow()
    
    // Path loss exponent for distance calculation
    private var _pathLossExponent = MutableStateFlow(1.8f)
    val pathLossExponent: StateFlow<Float> = _pathLossExponent.asStateFlow()
    
    private var scanJob: Job? = null
    private var positioningJob: Job? = null
    
    // BLE Manager for real beacon scanning
    private val bleManager = BleManager(application.applicationContext)
    
    // Beacon positions - can be updated from the database or UI
    private val beaconPositions = mutableMapOf(
        "beacon1" to Pair(0f, 0f),
        "beacon2" to Pair(10f, 0f),
        "beacon3" to Pair(5f, 8f)
    )
    
    // Previous position for smoothing
    private var previousPosition: PositionData? = null
    
    // For Kalman filter
    private val kalmanFilterState = KalmanFilterState()
    private var lastUpdateTime: Long = 0
    
    // For fusion method
    private val fusionKalmanState = KalmanFilterState()
    private var lastFusionUpdate: Long = 0
    
    // For fingerprinting
    private var fingerprintDatabase: List<FingerprintData> = emptyList()
    
    init {
        // Initialize with some managed beacons
        val initialBeacons = listOf(
            ManagedBeacon(
                id = "beacon1",
                uuid = "UUID-1",
                x = 0.0,
                y = 0.0,
                floor = 0,
                name = "Beacon 1"
            ),
            ManagedBeacon(
                id = "beacon2",
                uuid = "UUID-2",
                x = 10.0,
                y = 0.0,
                floor = 0,
                name = "Beacon 2"
            ),
            ManagedBeacon(
                id = "beacon3",
                uuid = "UUID-3",
                x = 5.0,
                y = 8.0,
                floor = 0,
                name = "Beacon 3"
            )
        )
        _managedBeacons.value = initialBeacons
        
        // Initialize fingerprint database (simulated)
        generateFingerprintDatabase()
        
        // Collect real beacon data from BleManager
        viewModelScope.launch {
            bleManager.detectedBeacons.collectLatest { beacons ->
                processDetectedBeacons(beacons)
            }
        }
    }
    
    /**
     * Process detected beacons from BleManager and convert them to our BeaconInfo format
     */
    private fun processDetectedBeacons(beacons: Map<String, Beacon>) {
        val beaconInfoList = beacons.map { (id, beacon) ->
            // Get a meaningful name for the beacon
            val beaconName = when {
                // If beacon has a non-empty name, use it
                beacon.name.isNotEmpty() -> beacon.name
                
                // Otherwise generate a name based on MAC address
                else -> {
                    val shortId = id.takeLast(4)
                    "Beacon-${shortId}"
                }
            }
            
            // Find position from known positions or managed beacons
            val position = beaconPositions[id] ?: _managedBeacons.value
                .find { it.id == id }
                ?.let { Pair(it.x.toFloat(), it.y.toFloat()) }
                ?: when {
                    id.contains("1") || id.startsWith("B1") -> Pair(0f, 0f)
                    id.contains("2") || id.startsWith("B2") -> Pair(10f, 0f)
                    id.contains("3") || id.startsWith("B3") -> Pair(5f, 8f)
                    id.contains("4") || id.startsWith("B4") -> Pair(0f, 8f)
                    id.contains("5") || id.startsWith("B5") -> Pair(10f, 8f)
                    id.contains("6") || id.startsWith("B6") -> Pair(5f, 0f)
                    // For any other beacon, use a consistent but random position based on the beacon ID
                    else -> {
                        // Generate a deterministic position based on the beacon ID
                        val idHash = id.hashCode()
                        val x = (Math.abs(idHash % 100) / 10f).coerceIn(0f, 10f)
                        val y = (Math.abs((idHash / 100) % 100) / 10f).coerceIn(0f, 10f)
                        Pair(x, y)
                    }
                }
            
            // Calculate distance using the path loss exponent
            val distance = TrilaterationCalculator.calculateDistance(
                beacon.rssi,
                -59, // Default txPower if not provided
                _pathLossExponent.value // Use the current path loss exponent
            )
            
            BeaconInfo(
                id = id,
                name = beaconName,
                uuid = beacon.id,
                major = 1, // Default values since we don't have this info
                minor = 1,
                rssi = beacon.rssi,
                distance = distance,
                position = position,
                lastUpdated = System.currentTimeMillis()
            )
        }
        
        // Update available beacons in UI state
        _uiState.update { state ->
            val updatedAvailable = beaconInfoList.map { newBeacon ->
                // Preserve connection status for beacons that are already in the list
                val existingBeacon = state.availableBeacons.find { it.id == newBeacon.id }
                if (existingBeacon != null) {
                    newBeacon.copy(isConnected = existingBeacon.isConnected)
                } else {
                    newBeacon
                }
            }
            
            // Update connected beacons with new signal information
            val updatedConnected = state.connectedBeacons.map { connectedBeacon ->
                val updatedBeacon = beaconInfoList.find { it.id == connectedBeacon.id }
                if (updatedBeacon != null) {
                    connectedBeacon.copy(
                        rssi = updatedBeacon.rssi,
                        distance = updatedBeacon.distance,
                        name = updatedBeacon.name  // Make sure to update the name too
                    )
                } else {
                    connectedBeacon
                }
            }
            
            state.copy(
                availableBeacons = updatedAvailable,
                connectedBeacons = updatedConnected
            )
        }
    }
    
    /**
     * Set the current floor for beacon management
     */
    fun setCurrentFloor(floor: Int) {
        _currentFloor.value = floor
    }
    
    /**
     * Save a beacon to the list of managed beacons
     */
    fun saveBeacon(beacon: ManagedBeacon) {
        val currentList = _managedBeacons.value.toMutableList()
        val index = currentList.indexOfFirst { it.id == beacon.id }
        
        if (index >= 0) {
            // Update existing
            currentList[index] = beacon
        } else {
            // Add new
            currentList.add(beacon)
        }
        
        _managedBeacons.value = currentList
    }
    
    /**
     * Delete a beacon from the list of managed beacons
     */
    fun deleteBeacon(id: String) {
        _managedBeacons.value = _managedBeacons.value.filter { it.id != id }
    }
    
    /**
     * Create a new beacon with a generated ID
     */
    fun createBeacon(uuid: String, name: String, x: Double, y: Double, floor: Int): ManagedBeacon {
        return ManagedBeacon(
            id = generateBeaconId(),
            uuid = uuid,
            name = name,
            x = x,
            y = y,
            floor = floor
        )
    }
    
    /**
     * Generate a unique beacon ID
     */
    fun generateBeaconId(): String {
        return "beacon_" + UUID.randomUUID().toString().substring(0, 8)
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
        
        // Limit to 3 connected beacons for trilateration
        val connectedBeacons = if (state.connectedBeacons.size < 3) {
            state.connectedBeacons + updatedBeacon
        } else {
            // Replace the oldest beacon
            state.connectedBeacons.drop(1) + updatedBeacon
        }
        
        _uiState.update { 
            it.copy(
                availableBeacons = updatedAvailable,
                connectedBeacons = connectedBeacons
            )
        }
        
        if (_uiState.value.connectedBeacons.size == 3 && !_uiState.value.isScanning) {
            startScanning()
        }
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
    
    private fun hasRequiredPermissions(): Boolean {
        val context = getApplication<Application>()
        
        // Check location permissions (required for BLE scanning)
        val hasLocationPermission = ActivityCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        
        // For Android S and above, check BLE permissions
        val hasBlePermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_SCAN
            ) == PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
        
        return hasLocationPermission && hasBlePermissions
    }
    
    override fun startScanning() {
        if (!hasRequiredPermissions()) {
            _uiState.update { it.copy(isScanning = false) }
            return
        }
        
        // Start BLE scanning using BleManager
        bleManager.startScanning()
        
        // Update UI state
        _uiState.update { it.copy(isScanning = true) }
        
        scanJob = viewModelScope.launch {
            while (isActive) {
                // Update position if enough beacons are connected
                updatePositionIfEnoughBeacons()
                delay(1000)
            }
        }
    }
    
    /**
     * Toggle demo mode (simulated vs real beacons)
     */
    fun toggleDemoMode(enabled: Boolean) {
        _demoMode.value = enabled
        
        // Restart positioning if already active
        if (positioningJob != null) {
            stopPositioning()
            startPositioning()
        }
    }
    
    /**
     * Start the positioning system
     */
    fun startPositioning() {
        positioningJob?.cancel()
        positioningJob = viewModelScope.launch {
            while (isActive) {
                // If in demo mode, use simulated data
                if (_demoMode.value) {
                    updateDemoBeaconSignals()
                } else {
                    // Use real BLE scanning
                    if (!uiState.value.isScanning) {
                        startScanning()
                    }
                }
                
                // Calculate position using trilateration
                calculateTrilateration()
                
                delay(1000) // Update every second
            }
        }
    }
    
    /**
     * Stop the positioning system
     */
    fun stopPositioning() {
        positioningJob?.cancel()
        
        // Stop scanning if it's running
        if (uiState.value.isScanning) {
            stopScanning()
        }
    }
    
    /**
     * Update beacon signals with simulated data (for demo mode)
     */
    private fun updateDemoBeaconSignals() {
        _managedBeacons.update { beacons ->
            beacons.map { beacon ->
                val currentFloor = _currentFloor.value
                
                // Only update beacons on the current floor in demo mode
                if (beacon.floor == currentFloor) {
                    // Simulate RSSI using proximity to user
                    val userPositionX = _trilaterationStatus.value.position?.x ?: 5.0
                    val userPositionY = _trilaterationStatus.value.position?.y ?: 5.0
                    
                    val dx = beacon.x - userPositionX
                    val dy = beacon.y - userPositionY
                    val realDistance = sqrt(dx * dx + dy * dy)
                    
                    // Add some noise to the distance
                    val noiseFactor = 0.2
                    val noiseAmount = (realDistance * noiseFactor * (Random.nextDouble() - 0.5))
                    val noisyDistance = realDistance + noiseAmount
                    
                    // Calculate RSSI based on distance and path loss model
                    val txPower = -59 // Reference signal at 1m
                    val pathLoss = 2.0 // Path loss exponent
                    val rssi = (txPower - 10 * pathLoss * kotlin.math.log10(noisyDistance)).toInt()
                    
                    beacon.copy(
                        lastRssi = rssi,
                        lastDistance = noisyDistance,
                        lastSeen = System.currentTimeMillis()
                    )
                } else {
                    beacon
                }
            }
        }
    }
    
    /**
     * Calculate position using trilateration from beacon data
     */
    private fun calculateTrilateration() {
        // Get beacons on current floor with recent readings
        val activeBeacons = _managedBeacons.value.filter { 
            it.floor == _currentFloor.value && 
            it.lastSeen > 0 &&
            System.currentTimeMillis() - it.lastSeen < 10000 // Only use beacons seen in last 10 seconds
        }
        
        if (activeBeacons.size >= 3) {
            // We have enough data for trilateration
            
            // Calculate position 
            // (simplified version for demo, in a real app would use Apache Commons Math)
            var sumX = 0.0
            var sumY = 0.0
            var sumWeight = 0.0
            
            activeBeacons.forEach { beacon ->
                // Use inverse square of distance as weight
                val weight = 1.0 / (beacon.lastDistance * beacon.lastDistance)
                sumX += beacon.x * weight
                sumY += beacon.y * weight
                sumWeight += weight
            }
            
            // Weighted average of positions
            val x = sumX / sumWeight
            val y = sumY / sumWeight
            
            // Calculate accuracy as average of distances
            val accuracy = activeBeacons.map { it.lastDistance }.average()
            
            // Update status
            _trilaterationStatus.value = TrilaterationStatus(
                position = Position(x, y, _currentFloor.value),
                accuracy = accuracy,
                activeBeacons = activeBeacons.size,
                timestamp = System.currentTimeMillis()
            )
        } else {
            // Not enough beacons for trilateration
            _trilaterationStatus.value = _trilaterationStatus.value.copy(
                position = null,
                activeBeacons = activeBeacons.size
            )
        }
    }
    
    /**
     * Updates position if enough beacons are connected
     */
    private fun updatePositionIfEnoughBeacons() {
        val connectedBeacons = _uiState.value.connectedBeacons
        
        // Get current position based on selected positioning method
        val newPosition = when (_uiState.value.positioningMethod) {
            PositioningMethod.TRILATERATION -> {
                // Need at least 3 beacons for trilateration
                if (connectedBeacons.size >= 3) {
                    TrilaterationCalculator.calculatePosition(connectedBeacons)
                } else {
                    null
                }
            }
            PositioningMethod.WEIGHTED_CENTROID -> {
                // Need at least 1 beacon for weighted centroid
                if (connectedBeacons.isNotEmpty()) {
                    TrilaterationCalculator.calculateWeightedCentroid(connectedBeacons)
                } else {
                    null
                }
            }
            PositioningMethod.KALMAN_FILTER -> {
                // Need at least 1 beacon for base position
                if (connectedBeacons.isNotEmpty()) {
                    val basePosition = if (connectedBeacons.size >= 3) {
                        TrilaterationCalculator.calculatePosition(connectedBeacons)
                    } else {
                        TrilaterationCalculator.calculateWeightedCentroid(connectedBeacons)
                    }
                    
                    // Apply Kalman filter if we have a base position
                    basePosition?.let {
                        val currentTime = System.currentTimeMillis()
                        val deltaTime = if (lastUpdateTime > 0) {
                            (currentTime - lastUpdateTime) / 1000f
                        } else {
                            0.1f
                        }
                        lastUpdateTime = currentTime
                        
                        TrilaterationCalculator.applyKalmanFilter(
                            kalmanFilterState,
                            it,
                            deltaTime
                        )
                    }
                } else {
                    null
                }
            }
            PositioningMethod.FINGERPRINTING -> {
                // Need at least 1 beacon for fingerprinting
                if (connectedBeacons.isNotEmpty()) {
                    TrilaterationCalculator.calculateFingerprintPosition(
                        connectedBeacons,
                        fingerprintDatabase
                    )
                } else {
                    null
                }
            }
            PositioningMethod.FUSION -> {
                // Use all methods combined for best accuracy
                if (connectedBeacons.isNotEmpty()) {
                    val currentTime = System.currentTimeMillis()
                    val fusedPosition = TrilaterationCalculator.calculateFusedPosition(
                        connectedBeacons,
                        fingerprintDatabase,
                        fusionKalmanState,
                        lastFusionUpdate
                    )
                    lastFusionUpdate = currentTime
                    fusedPosition
                } else {
                    null
                }
            }
        }
        
        // Apply position smoothing for methods other than Kalman (which already does smoothing)
        val smoothedPosition = if (_uiState.value.positioningMethod != PositioningMethod.KALMAN_FILTER) {
            applyPositionSmoothing(newPosition)
        } else {
            newPosition
        }
        
        // Update the UI state with the new position
        _uiState.update { it.copy(userPosition = smoothedPosition) }
    }
    
    /**
     * Apply position smoothing between consecutive measurements
     */
    private fun applyPositionSmoothing(newPosition: PositionData?): PositionData? {
        if (newPosition == null) return null
        
        val smoothed = if (previousPosition != null) {
            val smoothingFactor = _uiState.value.smoothingFactor
            val x = previousPosition!!.x * (1 - smoothingFactor) + newPosition.x * smoothingFactor
            val y = previousPosition!!.y * (1 - smoothingFactor) + newPosition.y * smoothingFactor
            val accuracy = previousPosition!!.accuracy * (1 - smoothingFactor) + newPosition.accuracy * smoothingFactor
            PositionData(x, y, accuracy, algorithm = newPosition.algorithm)
        } else {
            newPosition
        }
        
        previousPosition = smoothed
        return smoothed
    }
    
    /**
     * Generate simulated fingerprint database for testing
     */
    private fun generateFingerprintDatabase() {
        val beaconPositions = beaconPositions.mapValues { (_, position) ->
            Pair(position.first, position.second)
        }
        
        fingerprintDatabase = FingerprintDatabaseGenerator.generateDatabase(
            _uiState.value.floorPlanBounds,
            beaconPositions
        )
    }
    
    /**
     * Set the positioning method
     */
    override fun setPositioningMethod(method: PositioningMethod) {
        _uiState.update { it.copy(positioningMethod = method) }
        
        // Reset positioning data when changing methods
        previousPosition = null
        lastUpdateTime = 0
        
        if (method == PositioningMethod.KALMAN_FILTER) {
            // Reset Kalman filter state
            kalmanFilterState.initialized = false
        } else if (method == PositioningMethod.FUSION) {
            // Reset fusion Kalman filter state
            fusionKalmanState.initialized = false
            lastFusionUpdate = 0
        }
    }
    
    /**
     * Set the path loss exponent
     */
    override fun updatePathLossExponent(value: Float) {
        _pathLossExponent.value = value
    }
    
    override fun stopScanning() {
        scanJob?.cancel()
        
        // Stop BLE scanning using BleManager
        bleManager.stopScanning()
        
        // Update UI state
        _uiState.update { it.copy(isScanning = false) }
    }
    
    override fun onCleared() {
        super.onCleared()
        stopScanning()
        stopPositioning()
    }
}