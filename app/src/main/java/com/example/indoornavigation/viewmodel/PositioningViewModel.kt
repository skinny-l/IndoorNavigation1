package com.example.indoornavigation.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.indoornavigation.data.models.ManagedBeacon
import com.example.indoornavigation.data.models.NavNode
import com.example.indoornavigation.data.models.Position
import com.example.indoornavigation.data.models.TrilaterationResult
import com.example.indoornavigation.service.TrilaterationService
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.pow
import kotlin.math.sqrt
import androidx.core.content.ContextCompat

/**
 * ViewModel for positioning services
 */
class PositioningViewModel(application: Application) : AndroidViewModel(application) {

    // Current position
    private val _currentPosition = MutableStateFlow<Position?>(null)
    val currentPosition: StateFlow<Position?> = _currentPosition.asStateFlow()

    // Is scanning active
    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    // Debug mode flag
    private val _isDebugMode = MutableStateFlow(false)
    val isDebugMode: StateFlow<Boolean> = _isDebugMode.asStateFlow()

    // Positioning mode
    private val _positioningMode = MutableStateFlow(PositioningMode.FUSION)
    val positioningMode: StateFlow<PositioningMode> = _positioningMode.asStateFlow()

    // Nearby beacons
    private val _nearbyBeacons = MutableStateFlow<List<ManagedBeacon>>(emptyList())
    val nearbyBeacons: StateFlow<List<ManagedBeacon>> = _nearbyBeacons.asStateFlow()

    // Trilateration service
    private val trilaterationService = TrilaterationService()
    
    // Current floor
    private val _currentFloor = MutableStateFlow(0)
    val currentFloor: StateFlow<Int> = _currentFloor.asStateFlow()
    
    // Positioning accuracy
    private val _positioningAccuracy = MutableStateFlow(0.0)
    val positioningAccuracy: StateFlow<Double> = _positioningAccuracy.asStateFlow()

    /**
     * Start scanning for beacons and wifi signals
     */
    fun startScanning() {
        if (_isScanning.value) {
            Log.d("PositioningViewModel", "Already scanning")
            return
        }
        
        _isScanning.value = true
        Log.d("PositioningViewModel", "Starting positioning scan")
        
        // Start real BLE scanning and WiFi positioning
        when (_positioningMode.value) {
            PositioningMode.BLE_ONLY -> startRealBleScanning()
            PositioningMode.WIFI_ONLY -> startWifiPositioning()
            PositioningMode.FUSION -> {
                startRealBleScanning()
                startWifiPositioning()
            }
        }
    }

    /**
     * Stop scanning
     */
    fun stopScanning() {
        _isScanning.value = false
        Log.d("PositioningViewModel", "Stopping positioning scan")
    }

    /**
     * Set position manually (for testing)
     */
    fun setPosition(position: Position) {
        _currentPosition.value = position
        _currentFloor.value = position.floor
        Log.d("PositioningViewModel", "Position set manually to: $position")
    }
    
    /**
     * Toggle debug mode
     */
    fun toggleDebugMode() {
        _isDebugMode.value = !_isDebugMode.value
    }
    
    /**
     * Set positioning mode
     */
    fun setPositioningMode(mode: PositioningMode) {
        _positioningMode.value = mode
    }
    
    /**
     * Start real BLE scanning (attempts to use actual BLE if available)
     */
    private fun startRealBleScanning() {
        viewModelScope.launch {
            try {
                val context = getApplication<Application>().applicationContext
                val bluetoothAdapter = android.bluetooth.BluetoothAdapter.getDefaultAdapter()
                
                if (bluetoothAdapter != null && bluetoothAdapter.isEnabled) {
                    Log.d("PositioningViewModel", "Bluetooth available, starting real BLE scan")
                    // Start actual BLE scanning for any beacon
                    startPublicBeaconScanning(context)
                } else {
                    Log.d("PositioningViewModel", "Bluetooth not available")
                }
            } catch (e: Exception) {
                Log.w("PositioningViewModel", "Real BLE scanning failed: ${e.message}")
            }
        }
    }
    
    /**
     * Start WiFi positioning with public access points
     */
    private fun startWifiPositioning() {
        viewModelScope.launch {
            while (_isScanning.value) {
                try {
                    // Scan for WiFi networks
                    scanPublicWiFiNetworks()
                    
                    // Get detected WiFi APs
                    val allWifiAPs = _detectedWifiAPs.value
                    
                    // Calculate position if we have enough access points
                    if (allWifiAPs.size >= 3) {
                        val trilaterationResult = trilaterationService.calculatePosition(allWifiAPs)
                        trilaterationResult?.let { result ->
                            _currentPosition.value = result.position
                            _currentFloor.value = result.position.floor
                            _positioningAccuracy.value = result.accuracy
                            
                            Log.d("PositioningViewModel", "WiFi position: ${result.position}, accuracy: ${result.accuracy}")
                        }
                    }
                } catch (e: Exception) {
                    Log.e("PositioningViewModel", "Error in WiFi positioning: ${e.message}")
                    e.printStackTrace()
                }
                
                // Update every 5 seconds
                delay(5000)
            }
        }
    }
    
    // Detected public WiFi APs
    private val _detectedWifiAPs = MutableStateFlow<List<ManagedBeacon>>(emptyList())
    val detectedWifiAPs: StateFlow<List<ManagedBeacon>> = _detectedWifiAPs.asStateFlow()
    
    /**
     * Scan for public WiFi networks and use them for positioning
     */
    private fun scanPublicWiFiNetworks() {
        try {
            val context = getApplication<Application>().applicationContext
            val wifiManager = context.getSystemService(android.content.Context.WIFI_SERVICE) as android.net.wifi.WifiManager
            
            // Check WiFi permission
            val hasWifiPermission = androidx.core.content.ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.ACCESS_FINE_LOCATION
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            
            if (hasWifiPermission && wifiManager.isWifiEnabled) {
                val scanResults = wifiManager.scanResults
                val wifiBeacons = mutableListOf<ManagedBeacon>()
                
                scanResults.forEach { result ->
                    val wifiBeacon = ManagedBeacon(
                        id = "wifi_${result.BSSID}",
                        uuid = result.BSSID,
                        x = 40.0, // Estimated position - will be updated through fingerprinting
                        y = 30.0,
                        floor = 0,
                        name = "WiFi: ${result.SSID}",
                        lastRssi = result.level,
                        lastDistance = trilaterationService.rssiToDistance(result.level, -20), // WiFi has stronger signal
                        lastSeen = System.currentTimeMillis()
                    )
                    wifiBeacons.add(wifiBeacon)
                }
                
                _detectedWifiAPs.value = wifiBeacons
                Log.d("PositioningViewModel", "Detected ${wifiBeacons.size} WiFi networks")
            }
        } catch (e: Exception) {
            Log.w("PositioningViewModel", "WiFi scanning error: ${e.message}")
        }
    }
    
    /**
     * Scan for any available beacons and use them for positioning
     */
    private fun startPublicBeaconScanning(context: android.content.Context) {
        viewModelScope.launch {
            while (_isScanning.value) {
                try {
                    // Check permissions
                    val hasPermission = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                        androidx.core.content.ContextCompat.checkSelfPermission(
                            context, android.Manifest.permission.BLUETOOTH_SCAN
                        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                    } else {
                        androidx.core.content.ContextCompat.checkSelfPermission(
                            context, android.Manifest.permission.BLUETOOTH
                        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                    }
                    
                    if (hasPermission) {
                        // Scan for any BLE devices
                        val bluetoothAdapter = android.bluetooth.BluetoothAdapter.getDefaultAdapter()
                        val scanner = bluetoothAdapter?.bluetoothLeScanner
                        
                        if (scanner != null) {
                            Log.d("PositioningViewModel", "Scanning for public beacons...")
                            
                            // Create scan callback
                            val scanCallback = object : android.bluetooth.le.ScanCallback() {
                                override fun onScanResult(callbackType: Int, result: android.bluetooth.le.ScanResult) {
                                    // Process any detected beacon
                                    processPublicBeacon(result)
                                }
                                
                                override fun onBatchScanResults(results: MutableList<android.bluetooth.le.ScanResult>) {
                                    results.forEach { processPublicBeacon(it) }
                                }
                                
                                override fun onScanFailed(errorCode: Int) {
                                    Log.w("PositioningViewModel", "BLE scan failed: $errorCode")
                                }
                            }
                            
                            // Start scanning
                            val settings = android.bluetooth.le.ScanSettings.Builder()
                                .setScanMode(android.bluetooth.le.ScanSettings.SCAN_MODE_LOW_LATENCY)
                                .build()
                            
                            scanner.startScan(emptyList(), settings, scanCallback)
                            
                            // Stop scanning after 5 seconds
                            delay(5000)
                            scanner.stopScan(scanCallback)
                        }
                    }
                } catch (e: Exception) {
                    Log.w("PositioningViewModel", "Public beacon scanning error: ${e.message}")
                }
                
                // Scan every 10 seconds
                delay(10000)
            }
        }
    }
    
    /**
     * Process detected public beacon for positioning
     */
    private fun processPublicBeacon(result: android.bluetooth.le.ScanResult) {
        try {
            val device = result.device
            val rssi = result.rssi
            val macAddress = device.address
            val name = try {
                device.name ?: "Unknown Device"
            } catch (e: SecurityException) {
                "Unknown Device"
            }
            
            // Create beacon entry for this public device
            val publicBeacon = ManagedBeacon(
                id = "public_$macAddress",
                uuid = macAddress,
                x = 40.0, // Default position - will be updated through fingerprinting
                y = 30.0,
                floor = 0,
                name = "Public: $name",
                lastRssi = rssi,
                lastDistance = trilaterationService.rssiToDistance(rssi),
                lastSeen = System.currentTimeMillis()
            )
            
            // Add to detected beacons list
            val currentBeacons = _nearbyBeacons.value.toMutableList()
            val existingIndex = currentBeacons.indexOfFirst { it.id == publicBeacon.id }
            
            if (existingIndex >= 0) {
                // Update existing beacon
                currentBeacons[existingIndex] = publicBeacon
            } else {
                // Add new beacon
                currentBeacons.add(publicBeacon)
                Log.d("PositioningViewModel", "Detected new public beacon: $name ($macAddress) RSSI: $rssi")
            }
            
            // Keep only recent beacons (last 30 seconds)
            val now = System.currentTimeMillis()
            val recentBeacons = currentBeacons.filter { (now - it.lastSeen) < 30000 }
            
            _nearbyBeacons.value = recentBeacons
            
            // If we have 3+ beacons, try positioning
            if (recentBeacons.size >= 3) {
                tryPublicBeaconPositioning(recentBeacons)
            }
            
        } catch (e: Exception) {
            Log.w("PositioningViewModel", "Error processing public beacon: ${e.message}")
        }
    }
    
    /**
     * Attempt positioning using public beacons with estimated positions
     */
    private fun tryPublicBeaconPositioning(beacons: List<ManagedBeacon>) {
        try {
            // For public beacons, we don't know exact positions
            // We can use WiFi fingerprinting or proximity-based positioning
            
            // Simple approach: Use signal strength to estimate relative positions
            val positionedBeacons = estimateBeaconPositions(beacons)
            
            // Try trilateration with estimated positions
            val result = trilaterationService.calculatePosition(positionedBeacons)
            result?.let {
                _currentPosition.value = it.position
                _currentFloor.value = it.position.floor
                _positioningAccuracy.value = it.accuracy + 2.0 // Lower accuracy for public beacons
                
                Log.d("PositioningViewModel", "Public beacon position: ${it.position}, accuracy: ${it.accuracy + 2.0}")
            }
        } catch (e: Exception) {
            Log.w("PositioningViewModel", "Error in public beacon positioning: ${e.message}")
        }
    }
    
    /**
     * Estimate beacon positions based on signal strength and patterns
     */
    private fun estimateBeaconPositions(beacons: List<ManagedBeacon>): List<ManagedBeacon> {
        // This is a simplified approach - in a real system you'd use:
        // 1. WiFi fingerprinting database
        // 2. Crowdsourced beacon positions
        // 3. Machine learning for position estimation
        
        val estimatedBeacons = mutableListOf<ManagedBeacon>()
        
        beacons.forEachIndexed { index, beacon ->
            // Distribute beacons around the building based on signal strength
            val distance = trilaterationService.rssiToDistance(beacon.lastRssi)
            
            // Estimate position based on index and signal strength
            val angle = (index * 360.0 / beacons.size) * Math.PI / 180.0
            val x = 40.0 + (distance / 2.0) * Math.cos(angle)  // Center around building center
            val y = 30.0 + (distance / 2.0) * Math.sin(angle)
            
            // Constrain to building bounds
            val constrainedX = x.coerceIn(5.0, 75.0)
            val constrainedY = y.coerceIn(5.0, 55.0)
            
            estimatedBeacons.add(beacon.copy(
                x = constrainedX,
                y = constrainedY
            ))
        }
        
        return estimatedBeacons
    }
    
    /**
     * Get navigation nodes for pathfinding - Enhanced with detailed corridor navigation
     */
    fun getNavigationNodes(): Map<String, NavNode> {
        // Create a comprehensive navigation graph for ground floor with detailed corridors
        val nodes = mutableMapOf<String, NavNode>()
        
        // Main structural nodes
        val entranceNode = NavNode("entrance", Position(35.0, 55.0, 0))
        val centralHallNode = NavNode("central_hall", Position(40.0, 30.0, 0))
        val elevatorNode = NavNode("elevator", Position(40.0, 45.0, 0))
        val emergencyExitNode = NavNode("emergency_exit", Position(70.0, 55.0, 0))
        
        // Detailed corridor waypoints to avoid walls and follow actual paths
        val corridor1A = NavNode("corridor_1a", Position(25.0, 50.0, 0))
        val corridor1B = NavNode("corridor_1b", Position(25.0, 45.0, 0))
        val corridor1C = NavNode("corridor_1c", Position(25.0, 40.0, 0))
        val corridor1D = NavNode("corridor_1d", Position(25.0, 35.0, 0))
        
        val corridor2A = NavNode("corridor_2a", Position(30.0, 35.0, 0))
        val corridor2B = NavNode("corridor_2b", Position(35.0, 35.0, 0))
        val corridor2C = NavNode("corridor_2c", Position(40.0, 35.0, 0))
        val corridor2D = NavNode("corridor_2d", Position(45.0, 35.0, 0))
        
        val corridor3A = NavNode("corridor_3a", Position(50.0, 35.0, 0))
        val corridor3B = NavNode("corridor_3b", Position(55.0, 35.0, 0))
        val corridor3C = NavNode("corridor_3c", Position(55.0, 40.0, 0))
        val corridor3D = NavNode("corridor_3d", Position(55.0, 45.0, 0))
        
        // Intersection nodes for complex turns
        val intersection1 = NavNode("intersection_1", Position(35.0, 45.0, 0))
        val intersection2 = NavNode("intersection_2", Position(45.0, 45.0, 0))
        val intersection3 = NavNode("intersection_3", Position(35.0, 40.0, 0))
        
        // Stair access points
        val stairANode = NavNode("stair_a", Position(25.0, 35.0, 0))
        val stairBNode = NavNode("stair_b", Position(55.0, 45.0, 0))
        
        // Near-POI access points (for areas near walls)
        val nearRestroom = NavNode("near_restroom", Position(45.0, 50.0, 0))
        val nearLabArea = NavNode("near_lab_area", Position(60.0, 25.0, 0))
        val nearOfficeArea = NavNode("near_office_area", Position(20.0, 25.0, 0))
        
        // Create realistic connections following actual building layout
        // Entrance connections
        entranceNode.connections.addAll(listOf("corridor_1a", "intersection_1"))
        
        // Corridor 1 (Left side) - follows wall avoiding obstacles
        corridor1A.connections.addAll(listOf("entrance", "corridor_1b"))
        corridor1B.connections.addAll(listOf("corridor_1a", "corridor_1c"))
        corridor1C.connections.addAll(listOf("corridor_1b", "corridor_1d", "corridor_2a"))
        corridor1D.connections.addAll(listOf("corridor_1c", "stair_a"))
        
        // Corridor 2 (Central horizontal) - main pathway
        corridor2A.connections.addAll(listOf("corridor_1c", "corridor_2b"))
        corridor2B.connections.addAll(listOf("corridor_2a", "intersection_3", "corridor_2c"))
        corridor2C.connections.addAll(listOf("corridor_2b", "central_hall", "corridor_2d"))
        corridor2D.connections.addAll(listOf("corridor_2c", "corridor_3a"))
        
        // Corridor 3 (Right side) - follows wall
        corridor3A.connections.addAll(listOf("corridor_2d", "corridor_3b"))
        corridor3B.connections.addAll(listOf("corridor_3a", "corridor_3c", "near_lab_area"))
        corridor3C.connections.addAll(listOf("corridor_3b", "corridor_3d"))
        corridor3D.connections.addAll(listOf("corridor_3c", "stair_b", "intersection_2"))
        
        // Central hall connections
        centralHallNode.connections.addAll(listOf("corridor_2c", "intersection_3", "elevator"))
        
        // Intersection connections for complex routing
        intersection1.connections.addAll(listOf("entrance", "elevator", "near_restroom"))
        intersection2.connections.addAll(listOf("elevator", "corridor_3d", "emergency_exit"))
        intersection3.connections.addAll(listOf("corridor_2b", "central_hall"))
        
        // Elevator and stairs
        elevatorNode.connections.addAll(listOf("central_hall", "intersection_1", "intersection_2"))
        stairANode.connections.addAll(listOf("corridor_1d", "near_office_area"))
        stairBNode.connections.addAll(listOf("corridor_3d"))
        
        // Emergency exit
        emergencyExitNode.connections.addAll(listOf("intersection_2"))
        
        // POI access points
        nearRestroom.connections.addAll(listOf("intersection_1"))
        nearLabArea.connections.addAll(listOf("corridor_3b"))
        nearOfficeArea.connections.addAll(listOf("stair_a"))
        
        // Add all nodes to map
        nodes["entrance"] = entranceNode
        nodes["central_hall"] = centralHallNode
        nodes["corridor_1a"] = corridor1A
        nodes["corridor_1b"] = corridor1B
        nodes["corridor_1c"] = corridor1C
        nodes["corridor_1d"] = corridor1D
        nodes["corridor_2a"] = corridor2A
        nodes["corridor_2b"] = corridor2B
        nodes["corridor_2c"] = corridor2C
        nodes["corridor_2d"] = corridor2D
        nodes["corridor_3a"] = corridor3A
        nodes["corridor_3b"] = corridor3B
        nodes["corridor_3c"] = corridor3C
        nodes["corridor_3d"] = corridor3D
        nodes["intersection_1"] = intersection1
        nodes["intersection_2"] = intersection2
        nodes["intersection_3"] = intersection3
        nodes["elevator"] = elevatorNode
        nodes["stair_a"] = stairANode
        nodes["stair_b"] = stairBNode
        nodes["emergency_exit"] = emergencyExitNode
        nodes["near_restroom"] = nearRestroom
        nodes["near_lab_area"] = nearLabArea
        nodes["near_office_area"] = nearOfficeArea
        
        return nodes
    }
    
    /**
     * Set current floor
     */
    fun setCurrentFloor(floor: Int) {
        _currentFloor.value = floor
    }

    override fun onCleared() {
        super.onCleared()
        stopScanning()
    }
    
    /**
     * Positioning modes
     */
    enum class PositioningMode {
        BLE_ONLY,    // Use only BLE beacons for positioning
        WIFI_ONLY,   // Use only Wi-Fi access points for positioning
        FUSION       // Use sensor fusion of BLE and Wi-Fi
    }
}
