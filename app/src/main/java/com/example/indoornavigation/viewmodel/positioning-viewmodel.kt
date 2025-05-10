package com.example.indoornavigation.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.indoornavigation.data.bluetooth.BleManager
import com.example.indoornavigation.data.models.Beacon
import com.example.indoornavigation.data.models.NavNode
import com.example.indoornavigation.data.models.Position
import com.example.indoornavigation.utils.PositioningUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * ViewModel for handling positioning logic and beacon data
 */
class PositioningViewModel(application: Application) : AndroidViewModel(application) {

    // BLE manager for scanning beacons
    private val bleManager = BleManager(application.applicationContext)
    
    // Position data
    private val _currentPosition = MutableStateFlow<Position?>(null)
    val currentPosition: StateFlow<Position?> = _currentPosition.asStateFlow()
    
    // Beacon data
    private val _nearbyBeacons = MutableStateFlow<List<Beacon>>(emptyList())
    val nearbyBeacons: StateFlow<List<Beacon>> = _nearbyBeacons.asStateFlow()
    
    // Floor data
    private val _currentFloor = MutableStateFlow(1)
    val currentFloor: StateFlow<Int> = _currentFloor.asStateFlow()
    
    // Debugging flags
    private val _isDebugMode = MutableStateFlow(false)
    val isDebugMode: StateFlow<Boolean> = _isDebugMode.asStateFlow()
    
    // Known beacon positions (in a real app, this would come from Firebase)
    private val knownBeacons = mutableMapOf<String, Position>()
    
    init {
        // Collect beacon data and calculate position
        viewModelScope.launch {
            bleManager.detectedBeacons.collectLatest { beacons ->
                processBeacons(beacons)
            }
        }
        
        // Initialize sample beacon positions for testing
        initializeKnownBeacons()
    }
    
    /**
     * Start BLE scanning
     */
    fun startScanning() {
        bleManager.startScanning()
    }
    
    /**
     * Stop BLE scanning
     */
    fun stopScanning() {
        bleManager.stopScanning()
    }
    
    /**
     * Process detected beacons and calculate position
     */
    private fun processBeacons(beacons: Map<String, Beacon>) {
        viewModelScope.launch(Dispatchers.Default) {
            // Convert to list and sort by distance
            val beaconList = beacons.values.toList()
                .sortedBy { it.distance }
            
            // Update nearby beacons StateFlow
            _nearbyBeacons.value = beaconList
            
            // Only try to calculate position if we have enough beacons
            if (beaconList.size >= 3) {
                // Filter beacons that we know the position of
                val knownBeaconsList = beaconList.filter { knownBeacons.containsKey(it.id) }
                
                if (knownBeaconsList.size >= 3) {
                    // We have at least 3 known beacons, calculate position using trilateration
                    calculatePositionByTrilateration(knownBeaconsList)
                } else if (knownBeaconsList.isNotEmpty()) {
                    // We have at least one known beacon, use weighted average
                    calculatePositionByWeightedAverage(knownBeaconsList)
                }
            }
        }
    }
    
    /**
     * Calculate position using trilateration (requires at least 3 beacons)
     */
    private fun calculatePositionByTrilateration(beacons: List<Beacon>) {
        // Get top 3 closest beacons
        val top3Beacons = beacons.take(3)
        
        // Extract positions and distances
        val beaconPositions = top3Beacons.map { knownBeacons[it.id]!! }
        val distances = top3Beacons.map { it.distance }
        
        // Use positioning utils to calculate position
        val calculatedPosition = PositioningUtils.calculatePositionByTrilateration(
            beaconPositions,
            distances
        )
        
        // Apply Kalman filter for position smoothing (implemented in PositioningUtils)
        val smoothedPosition = PositioningUtils.applyKalmanFilter(
            _currentPosition.value,
            calculatedPosition
        )
        
        // Update position
        _currentPosition.value = smoothedPosition
    }
    
    /**
     * Calculate position using weighted average (when trilateration is not possible)
     */
    private fun calculatePositionByWeightedAverage(beacons: List<Beacon>) {
        // Use positioning utils to calculate position
        val calculatedPosition = PositioningUtils.calculatePositionByWeightedAverage(
            beacons.map { knownBeacons[it.id]!! },
            beacons.map { 1.0 / (it.distance * it.distance) } // Weight inversely proportional to square of distance
        )
        
        // Apply Kalman filter for position smoothing
        val smoothedPosition = PositioningUtils.applyKalmanFilter(
            _currentPosition.value,
            calculatedPosition
        )
        
        // Update position
        _currentPosition.value = smoothedPosition
    }
    
    /**
     * Set current floor
     */
    fun setCurrentFloor(floor: Int) {
        _currentFloor.value = floor
    }
    
    /**
     * Toggle debug mode
     */
    fun toggleDebugMode() {
        _isDebugMode.value = !_isDebugMode.value
    }
    
    /**
     * Get navigation nodes for pathfinding
     * In a real app, this would come from Firebase or a local database
     */
    fun getNavigationNodes(): Map<String, NavNode> {
        // Create a simple navigation graph
        val nodes = mutableMapOf<String, NavNode>()
        
        // First floor nodes - covering key areas and hallways
        val node1 = NavNode("node_1_1", Position(10.0, 15.0, 1), mutableListOf("node_1_5"))
        val node2 = NavNode("node_1_2", Position(30.0, 15.0, 1), mutableListOf("node_1_5", "node_1_6"))
        val node3 = NavNode("node_1_3", Position(20.0, 30.0, 1), mutableListOf("node_1_5", "node_1_6"))
        val node4 = NavNode("node_1_4", Position(40.0, 40.0, 1), mutableListOf("node_1_6"))
        val node5 = NavNode("node_1_5", Position(20.0, 20.0, 1), mutableListOf("node_1_1", "node_1_2", "node_1_3", "node_1_9"))
        val node6 = NavNode("node_1_6", Position(30.0, 30.0, 1), mutableListOf("node_1_2", "node_1_3", "node_1_4"))
        val node7 = NavNode("node_1_7", Position(10.0, 40.0, 1), mutableListOf("node_1_3"))
        val node8 = NavNode("node_1_8", Position(40.0, 10.0, 1), mutableListOf("node_1_2"))
        
        // Connection point to elevator
        val node9 = NavNode("node_1_9", Position(25.0, 25.0, 1), mutableListOf("node_1_5", "node_2_6"))
        
        // Second floor nodes
        val node10 = NavNode("node_2_1", Position(10.0, 15.0, 2), mutableListOf("node_2_4"))
        val node11 = NavNode("node_2_2", Position(30.0, 15.0, 2), mutableListOf("node_2_4"))
        val node12 = NavNode("node_2_3", Position(20.0, 30.0, 2), mutableListOf("node_2_5"))
        val node13 = NavNode("node_2_4", Position(20.0, 20.0, 2), mutableListOf("node_2_1", "node_2_2", "node_2_6"))
        val node14 = NavNode("node_2_5", Position(30.0, 30.0, 2), mutableListOf("node_2_3", "node_2_6"))
        
        // Connection point to elevator
        val node15 = NavNode("node_2_6", Position(25.0, 25.0, 2), mutableListOf("node_2_4", "node_2_5", "node_1_9"))
        
        // Add all nodes to map
        nodes["node_1_1"] = node1
        nodes["node_1_2"] = node2
        nodes["node_1_3"] = node3
        nodes["node_1_4"] = node4
        nodes["node_1_5"] = node5
        nodes["node_1_6"] = node6
        nodes["node_1_7"] = node7
        nodes["node_1_8"] = node8
        nodes["node_1_9"] = node9
        nodes["node_2_1"] = node10
        nodes["node_2_2"] = node11
        nodes["node_2_3"] = node12
        nodes["node_2_4"] = node13
        nodes["node_2_5"] = node14
        nodes["node_2_6"] = node15
        
        return nodes
    }
    
    /**
     * Initialize sample known beacon positions
     * In a real app, this data would come from Firebase
     */
    private fun initializeKnownBeacons() {
        // First floor beacons
        knownBeacons["00:11:22:33:44:55"] = Position(10.0, 15.0, 1)
        knownBeacons["00:11:22:33:44:56"] = Position(30.0, 15.0, 1)
        knownBeacons["00:11:22:33:44:57"] = Position(20.0, 30.0, 1)
        knownBeacons["00:11:22:33:44:58"] = Position(40.0, 40.0, 1)
        
        // Second floor beacons
        knownBeacons["00:11:22:33:44:59"] = Position(10.0, 15.0, 2)
        knownBeacons["00:11:22:33:44:60"] = Position(30.0, 15.0, 2)
        knownBeacons["00:11:22:33:44:61"] = Position(20.0, 30.0, 2)
    }
}