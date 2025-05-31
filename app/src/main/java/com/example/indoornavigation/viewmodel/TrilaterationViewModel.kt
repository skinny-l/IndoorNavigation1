package com.example.indoornavigation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.indoornavigation.data.BeaconInfo
import com.example.indoornavigation.data.PositionData
import com.example.indoornavigation.data.PositioningMethod
import com.example.indoornavigation.data.TrilaterationCalculator
import com.example.indoornavigation.data.TrilaterationState
import com.example.indoornavigation.data.models.ManagedBeacon
import com.example.indoornavigation.data.models.Position
import com.example.indoornavigation.data.models.TrilaterationStatus
import com.example.indoornavigation.ui.TrilaterationViewModelInterface
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.UUID
import kotlin.random.Random

class TrilaterationViewModel : ViewModel(), TrilaterationViewModelInterface {
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
    
    private var scanningJob: Job? = null
    private var positioningJob: Job? = null
    
    init {
        // Initialize with simulated beacons with strong RSSI values and meaningful names
        val availableBeacons = listOf(
            BeaconInfo("beacon1", "iBeacon-FA31", "FA:31:85:7D:CB:E5", 1, 1, rssi = -50, position = Pair(0f, 0f)),
            BeaconInfo("beacon2", "BLE-Sensor-A42F", "A4:2F:EB:91:C3:18", 1, 2, rssi = -55, position = Pair(10f, 0f)),
            BeaconInfo("beacon3", "Eddystone-8BC2", "8B:C2:57:3E:F6:90", 1, 3, rssi = -60, position = Pair(5f, 8f)),
            BeaconInfo("beacon4", "RadiusNetwork-E701", "E7:01:12:AB:5D:F3", 1, 4, rssi = -65, position = Pair(0f, 8f)),
            BeaconInfo("beacon5", "AltBeacon-D835", "D8:35:4C:19:73:02", 1, 5, rssi = -70, position = Pair(10f, 8f)),
            // Add more varied beacons to simulate real-world discovery
            BeaconInfo("beacon6", "ESP32-BLE-5AC6", "5A:C6:44:E1:B7:90", 1, 6, rssi = -68, position = Pair(5f, 3f)),
            BeaconInfo("beacon7", "RuuviTag-9D2", "9D:2A:BB:30:F4:CE", 1, 7, rssi = -72, position = Pair(2f, 6f)),
            BeaconInfo("beacon8", "Xiaomi-Mi-Band", "C3:44:E5:12:7B:9F", 1, 8, rssi = -75, position = Pair(8f, 1f)),
            BeaconInfo("beacon9", "Tile-Tracker", "A1:B2:C3:D4:E5:F6", 1, 9, rssi = -78, position = Pair(7f, 9f)),
            BeaconInfo("beacon10", "JY-MCU-HC06", "21:4D:9A:7C:F1:B8", 1, 10, rssi = -82, position = Pair(3f, 2f))
        )
        _uiState.update { it.copy(availableBeacons = availableBeacons) }
        
        // Initialize with some managed beacons with matching names
        val initialBeacons = listOf(
            ManagedBeacon(
                id = "beacon1",
                uuid = "FA:31:85:7D:CB:E5",
                x = 0.0,
                y = 0.0,
                floor = 0,
                name = "iBeacon-FA31"
            ),
            ManagedBeacon(
                id = "beacon2",
                uuid = "A4:2F:EB:91:C3:18",
                x = 10.0,
                y = 0.0,
                floor = 0,
                name = "BLE-Sensor-A42F"
            ),
            ManagedBeacon(
                id = "beacon3",
                uuid = "8B:C2:57:3E:F6:90",
                x = 5.0,
                y = 8.0,
                floor = 0,
                name = "Eddystone-8BC2"
            )
        )
        _managedBeacons.value = initialBeacons
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
                // Update beacon signals with simulated data
                if (_demoMode.value) {
                    updateBeaconSignals()
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
        scanningJob?.cancel()
        
        // Reset positioning if needed
        _uiState.update { it.copy(isScanning = false) }
    }
    
    /**
     * Simulates finding beacons with changing RSSI values, as if they were real BLE beacons
     */
    private fun simulateBeaconScanning() {
        // Get the current list of beacons
        val currentBeacons = _uiState.value.availableBeacons.toMutableList()
        
        // Random chance to add a user's own beacon (simulating discovery of new beacons)
        val shouldAddNewBeacon = Random.nextDouble() < 0.01 // 1% chance per scan
        
        if (shouldAddNewBeacon) {
            // Create a user's own beacon with realistic MAC address and name
            val macAddress = generateRandomMACAddress()
            val shortMAC = macAddress.takeLast(4)
            val beaconTypes = listOf("HM-10", "nRF52", "CC2541", "BT05", "HC-08")
            val beaconType = beaconTypes.random()
            val beaconName = "UserBeacon-$beaconType-$shortMAC"
            
            val posX = Random.nextFloat() * 10f
            val posY = Random.nextFloat() * 10f
            val rssi = Random.nextInt(-75, -45)
            
            val newBeacon = BeaconInfo(
                id = "user_beacon_${System.currentTimeMillis()}",
                name = beaconName,
                uuid = macAddress,
                major = 1,
                minor = Random.nextInt(1, 10),
                rssi = rssi,
                position = Pair(posX, posY)
            )
            
            currentBeacons.add(newBeacon)
        }
        
        // If we have no beacons at all, create initial beacons with strong signal
        if (currentBeacons.isEmpty()) {
            val newBeacons = listOf(
                BeaconInfo("beacon1", "iBeacon-FA31", "FA:31:85:7D:CB:E5", 1, 1, rssi = -50, position = Pair(0f, 0f)),
                BeaconInfo("beacon2", "BLE-Sensor-A42F", "A4:2F:EB:91:C3:18", 1, 2, rssi = -55, position = Pair(10f, 0f)),
                BeaconInfo("beacon3", "Eddystone-8BC2", "8B:C2:57:3E:F6:90", 1, 3, rssi = -60, position = Pair(5f, 8f))
            )
            _uiState.update { it.copy(availableBeacons = newBeacons) }
            return
        }
        
        // Update RSSI values with some random variation but keep them strong
        val updatedBeacons = currentBeacons.map { beacon ->
            // Use a much stronger base RSSI to ensure beacons stay visible
            val baseRSSI = -50 - (beacon.id.substringAfter("beacon").toIntOrNull() ?: 0) * 2
            
            // Add some random variation to simulate real-world signal fluctuations
            val rssiVariation = Random.nextInt(-3, 4) // -3 to +3 dBm variation
            val newRSSI = baseRSSI + rssiVariation
            
            // Calculate distance based on the new RSSI
            val distance = TrilaterationCalculator.calculateDistance(
                newRSSI, 
                beacon.txPower, 
                _uiState.value.pathLossExponent
            )
            
            beacon.copy(rssi = newRSSI, distance = distance)
        }
        
        _uiState.update { it.copy(availableBeacons = updatedBeacons) }
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
        
        // Also update the BeaconInfo and connected beacons data for the scanning UI
        if (_demoMode.value) {
            val beaconMapping = _managedBeacons.value.associate { 
                it.id to it 
            }
            
            _uiState.update { state ->
                val updatedAvailableBeacons = state.availableBeacons.map { beacon ->
                    val managedBeacon = beaconMapping[beacon.id]
                    if (managedBeacon != null) {
                        beacon.copy(
                            rssi = managedBeacon.lastRssi,
                            distance = managedBeacon.lastDistance.toFloat()
                        )
                    } else {
                        beacon
                    }
                }
                
                state.copy(availableBeacons = updatedAvailableBeacons)
            }
        }
    }
    
    /**
     * Update beacon signals with simulated data (for demo mode)
     */
    private fun updateBeaconSignals() {
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
                    val realDistance = kotlin.math.sqrt(dx * dx + dy * dy)
                    
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
    
    override fun connectBeacon(beaconId: String) {
        val state = _uiState.value
        val beacon = state.availableBeacons.find { it.id == beaconId }
        
        // Skip if beacon not found
        if (beacon == null) return
        
        val updatedBeacon = beacon.copy(isConnected = true)
        
        val updatedAvailable = state.availableBeacons.map {
            if (it.id == beaconId) updatedBeacon else it
        }
        
        val connectedBeacons = if (state.connectedBeacons.size < 3) {
            state.connectedBeacons + updatedBeacon
        } else {
            state.connectedBeacons
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
    
    override fun startScanning() {
        scanningJob?.cancel()
        scanningJob = viewModelScope.launch {
            _uiState.update { it.copy(isScanning = true) }
            
            while (isActive) {
                simulateBeaconScanning()
                updatePositionIfEnoughBeacons()
                delay(1000) // Update every second
            }
        }
    }
    
    override fun stopScanning() {
        scanningJob?.cancel()
        _uiState.update { it.copy(isScanning = false) }
    }
    
    /**
     * Updates position if enough beacons are connected
     */
    private fun updatePositionIfEnoughBeacons() {
        val connectedBeacons = _uiState.value.connectedBeacons
        if (connectedBeacons.size >= 3) {
            val position = TrilaterationCalculator.calculatePosition(connectedBeacons)
            _uiState.update { it.copy(userPosition = position) }
        } else {
            _uiState.update { it.copy(userPosition = null) }
        }
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
        stopPositioning()
    }
    
    /**
     * Generate a random MAC address
     */
    private fun generateRandomMACAddress(): String {
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