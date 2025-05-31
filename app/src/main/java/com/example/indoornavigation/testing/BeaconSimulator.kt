package com.example.indoornavigation.testing

import android.content.Context
import android.util.Log
import com.example.indoornavigation.data.models.Beacon
import com.example.indoornavigation.data.models.Position
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Beacon simulator for testing positioning algorithms
 * Simulates BLE beacon signals based on user position
 */
class BeaconSimulator(private val context: Context) {

    private val TAG = "BeaconSimulator"
    private val simulatedBeacons = mutableListOf<SimulatedBeacon>()
    private val coroutineScope = CoroutineScope(Dispatchers.Default)
    
    // Current user position for distance calculations
    private var userPosition: Position? = null
    
    // Update interval in milliseconds
    private var updateInterval = 1000L
    
    // Flag to track if simulation is running
    private var isRunning = false
    
    // Simulated beacons flow
    private val _beacons = MutableStateFlow<Map<String, Beacon>>(emptyMap())
    val beacons: StateFlow<Map<String, Beacon>> = _beacons.asStateFlow()
    
    /**
     * Represents a simulated beacon
     */
    data class SimulatedBeacon(
        val uuid: String,
        val major: Int,
        val minor: Int,
        val position: Position,
        val txPower: Int = -59, // Default txPower at 1m
        val name: String = "SimBeacon-$major-$minor"
    )
    
    /**
     * Start the beacon simulation
     */
    fun start() {
        if (isRunning) return
        isRunning = true
        
        coroutineScope.launch {
            while (isRunning) {
                updateBeaconReadings()
                delay(updateInterval)
            }
        }
        
        Log.d(TAG, "Beacon simulation started with ${simulatedBeacons.size} beacons")
    }
    
    /**
     * Stop the beacon simulation
     */
    fun stop() {
        isRunning = false
        Log.d(TAG, "Beacon simulation stopped")
    }
    
    /**
     * Update the current user position
     */
    fun updateUserPosition(position: Position) {
        userPosition = position
        // Immediate update of readings when position changes
        if (isRunning) {
            coroutineScope.launch {
                updateBeaconReadings()
            }
        }
    }
    
    /**
     * Add a simulated beacon to the environment
     */
    fun addSimulatedBeacon(beacon: SimulatedBeacon) {
        simulatedBeacons.add(beacon)
        Log.d(TAG, "Added simulated beacon: UUID=${beacon.uuid}, major=${beacon.major}, minor=${beacon.minor} at position ${beacon.position}")
        
        // Update readings immediately if running
        if (isRunning) {
            coroutineScope.launch {
                updateBeaconReadings()
            }
        }
    }
    
    /**
     * Add multiple simulated beacons
     */
    fun addSimulatedBeacons(beacons: List<SimulatedBeacon>) {
        simulatedBeacons.addAll(beacons)
        Log.d(TAG, "Added ${beacons.size} simulated beacons")
        
        // Update readings immediately if running
        if (isRunning) {
            coroutineScope.launch {
                updateBeaconReadings()
            }
        }
    }
    
    /**
     * Remove a simulated beacon
     */
    fun removeSimulatedBeacon(uuid: String, major: Int, minor: Int) {
        val initialSize = simulatedBeacons.size
        simulatedBeacons.removeAll { 
            it.uuid == uuid && it.major == major && it.minor == minor 
        }
        
        val removed = initialSize - simulatedBeacons.size
        if (removed > 0) {
            Log.d(TAG, "Removed $removed simulated beacon(s)")
        }
    }
    
    /**
     * Clear all simulated beacons
     */
    fun clearSimulatedBeacons() {
        simulatedBeacons.clear()
        _beacons.value = emptyMap()
        Log.d(TAG, "Cleared all simulated beacons")
    }
    
    /**
     * Set the update interval for the simulation
     */
    fun setUpdateInterval(intervalMs: Long) {
        updateInterval = intervalMs.coerceAtLeast(100) // Minimum 100ms
    }
    
    /**
     * Update the beacon readings based on user position
     */
    private fun updateBeaconReadings() {
        val position = userPosition ?: return
        val currentBeacons = mutableMapOf<String, Beacon>()
        
        for (simBeacon in simulatedBeacons) {
            // Only process beacons on the same floor
            if (simBeacon.position.floor == position.floor) {
                // Calculate distance
                val distance = calculateDistance(position, simBeacon.position)
                
                // Calculate RSSI using path loss model
                val rssi = calculateRssi(simBeacon, distance)
                
                // Create beacon object with calculated values
                val beaconId = "${simBeacon.uuid}-${simBeacon.major}-${simBeacon.minor}"
                val beacon = Beacon(
                    id = beaconId,
                    name = simBeacon.name,
                    rssi = rssi,
                    distance = distance,
                    timestamp = System.currentTimeMillis()
                )
                
                currentBeacons[beaconId] = beacon
            }
        }
        
        // Update the beacons flow
        _beacons.value = currentBeacons
    }
    
    /**
     * Calculate RSSI based on distance from beacon
     */
    private fun calculateRssi(beacon: SimulatedBeacon, distance: Double): Int {
        // Path loss model: RSSI = TxPower - 10 * n * log10(d)
        // where n is path loss exponent (typically 2-4 indoors)
        val pathLossExponent = 2.5
        val calculatedRssi = beacon.txPower - (10 * pathLossExponent * log10(distance.coerceAtLeast(0.1))).toInt()
        
        // Add random noise to simulate real-world conditions
        val noise = Random.nextInt(-3, 4) // -3 to +3 dB noise
        
        return (calculatedRssi + noise).coerceIn(-100, -30) // Typical RSSI range
    }
    
    /**
     * Calculate distance between two positions
     */
    private fun calculateDistance(p1: Position, p2: Position): Double {
        // Calculate 2D distance (assuming same floor)
        val dx = p2.x - p1.x
        val dy = p2.y - p1.y
        
        return sqrt(dx * dx + dy * dy)
    }
    
    /**
     * Get a list of all simulated beacons
     */
    fun getSimulatedBeacons(): List<SimulatedBeacon> {
        return simulatedBeacons.toList()
    }
    
    /**
     * Create a development test setup with beacons across a sample building
     * Used for testing when not physically in the building
     */
    fun createDevTestSetup(floor: Int = 0): List<SimulatedBeacon> {
        // Clear any existing beacons
        clearSimulatedBeacons()
        
        // Create a grid of beacons across the building
        val beacons = createBeaconGrid(
            startX = 10.0, 
            startY = 10.0, 
            width = 60.0, 
            height = 40.0, 
            floor = floor,
            spacingX = 15.0,
            spacingY = 15.0
        )
        
        // Simulate a user position in the middle of the building
        val defaultPosition = Position(30.0, 20.0, floor)
        updateUserPosition(defaultPosition)
        
        // Start simulating
        start()
        
        Log.d(TAG, "Created development test setup with ${beacons.size} beacons")
        return beacons
    }
    
    /**
     * Create a grid of beacons across an area for testing
     * 
     * @param startX X coordinate of the top-left corner
     * @param startY Y coordinate of the top-left corner
     * @param width Width of the area in meters
     * @param height Height of the area in meters
     * @param floor Floor number
     * @param spacingX Spacing between beacons in X direction (meters)
     * @param spacingY Spacing between beacons in Y direction (meters)
     * @param baseUUID Base UUID to use for the beacons
     * @return List of created beacons
     */
    fun createBeaconGrid(
        startX: Double, 
        startY: Double, 
        width: Double, 
        height: Double, 
        floor: Int,
        spacingX: Double = 5.0,
        spacingY: Double = 5.0,
        baseUUID: String = "f7826da6-4fa2-4e98-8024-bc5b71e0893e"
    ): List<SimulatedBeacon> {
        val beacons = mutableListOf<SimulatedBeacon>()
        var major = 1
        
        // Create grid of beacons
        var y = startY
        while (y < startY + height) {
            var x = startX
            var minor = 1
            
            while (x < startX + width) {
                val beacon = SimulatedBeacon(
                    uuid = baseUUID,
                    major = major,
                    minor = minor,
                    position = Position(x, y, floor),
                    name = "GridBeacon-$major-$minor"
                )
                
                beacons.add(beacon)
                x += spacingX
                minor++
            }
            
            y += spacingY
            major++
        }
        
        // Add the created beacons to the simulator
        addSimulatedBeacons(beacons)
        
        return beacons
    }
}
