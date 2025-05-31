package com.example.indoornavigation.data.wifi

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.app.ActivityCompat
import com.example.indoornavigation.data.models.Position
import com.example.indoornavigation.data.models.WifiAccessPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Manager class for Wi-Fi scanning and positioning
 */
class WifiPositioningManager(private val context: Context) {
    private val TAG = "WifiPositioningManager"
    
    // Wi-Fi components
    private val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as WifiManager
    
    // Scanning variables
    private var scanning = AtomicBoolean(false)
    private val handler = Handler(Looper.getMainLooper())
    private val SCAN_PERIOD: Long = 10000 // 10 seconds scan period
    
    // Wi-Fi access point data
    private val _detectedAccessPoints = MutableStateFlow<Map<String, WifiAccessPoint>>(emptyMap())
    val detectedAccessPoints: StateFlow<Map<String, WifiAccessPoint>> = _detectedAccessPoints.asStateFlow()
    
    // Access point cache to store recent readings
    private val accessPointCache = mutableMapOf<String, WifiAccessPoint>()
    
    // Known access point positions (in a real app, this would come from Firebase)
    private val knownAccessPoints = mutableMapOf<String, Position>()
    
    // Broadcast receiver for Wi-Fi scan results
    private val wifiScanReceiver = WifiScanReceiver { results ->
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            processResults(results)
        } else {
            Log.e(TAG, "Location permission not granted")
        }
    }
    
    init {
        // Initialize known access points for testing
        initializeKnownAccessPoints()
    }
    
    /**
     * Check if Wi-Fi is enabled
     */
    fun isWifiEnabled(): Boolean {
        return wifiManager.isWifiEnabled
    }
    
    /**
     * Start Wi-Fi scanning
     */
    fun startScanning() {
        if (!isWifiEnabled()) {
            Log.e(TAG, "Wi-Fi is not enabled")
            return
        }
        
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e(TAG, "Location permission not granted")
            return
        }
        
        if (scanning.get()) {
            Log.d(TAG, "Already scanning")
            return
        }
        
        // Register receiver
        context.registerReceiver(
            wifiScanReceiver,
            android.content.IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
        )
        
        // Start scanning with timeout
        handler.postDelayed({
            if (scanning.get()) {
                scanning.set(false)
                context.unregisterReceiver(wifiScanReceiver)
                Log.d(TAG, "Wi-Fi scan stopped after timeout")
                // Restart scanning after a brief pause
                handler.postDelayed({ startScanning() }, 1000)
            }
        }, SCAN_PERIOD)
        
        scanning.set(true)
        wifiManager.startScan()
        Log.d(TAG, "Wi-Fi scan started")
    }
    
    /**
     * Stop Wi-Fi scanning
     */
    fun stopScanning() {
        if (!scanning.get()) return
        
        try {
            context.unregisterReceiver(wifiScanReceiver)
        } catch (e: IllegalArgumentException) {
            // Receiver not registered
        }
        
        scanning.set(false)
        Log.d(TAG, "Wi-Fi scan stopped")
    }
    
    /**
     * Process scan results
     */
    private fun processResults(results: List<ScanResult>) {
        for (result in results) {
            val bssid = result.BSSID
            val ssid = result.SSID
            val rssi = result.level
            val frequency = result.frequency
            
            // Calculate approximate distance using RSSI
            val distance = calculateDistance(rssi, frequency)
            
            // Create access point object
            val accessPoint = WifiAccessPoint(
                id = bssid,
                ssid = ssid,
                rssi = rssi,
                frequency = frequency,
                distance = distance,
                timestamp = System.currentTimeMillis()
            )
            
            // Update access point cache
            accessPointCache[bssid] = accessPoint
        }
        
        // Update StateFlow with the latest access point information
        _detectedAccessPoints.value = HashMap(accessPointCache)
    }
    
    /**
     * Calculate approximate distance using RSSI and frequency
     * using the log-distance path loss model
     */
    private fun calculateDistance(rssi: Int, frequency: Int): Double {
        // Reference RSSI at 1 meter (varies by device and environment)
        val referenceRssi = -40
        
        // Path loss exponent (environment factor)
        val n = 2.7 // Free space = 2, indoors = 2.7 to 3.5 depending on obstacles
        
        // Calculate distance using log-distance path loss model
        // d = 10^((referenceRssi - RSSI)/(10 * n))
        return Math.pow(10.0, (referenceRssi - rssi) / (10.0 * n))
    }
    
    /**
     * Estimate position using Wi-Fi fingerprinting
     */
    fun estimatePosition(): Position? {
        val detectedAPs = _detectedAccessPoints.value
        
        if (detectedAPs.isEmpty()) {
            return null
        }
        
        // Filter to only known access points
        val knownDetectedAPs = detectedAPs.filter { knownAccessPoints.containsKey(it.key) }
        
        if (knownDetectedAPs.isEmpty()) {
            return null
        }
        
        // Calculate weighted average of positions
        var totalWeight = 0.0
        var weightedX = 0.0
        var weightedY = 0.0
        var floorVotes = mutableMapOf<Int, Double>()
        
        for ((bssid, ap) in knownDetectedAPs) {
            val knownPosition = knownAccessPoints[bssid] ?: continue
            
            // Weight is inversely proportional to distance (closer APs have more influence)
            val weight = 1.0 / (ap.distance * ap.distance)
            
            totalWeight += weight
            weightedX += knownPosition.x * weight
            weightedY += knownPosition.y * weight
            
            // Vote for floor
            val currentVotes = if (floorVotes.containsKey(knownPosition.floor)) 
                floorVotes[knownPosition.floor]!! else 0.0
            floorVotes[knownPosition.floor] = currentVotes + weight
        }
        
        if (totalWeight == 0.0) {
            return null
        }
        
        // Calculate final position
        val x = weightedX / totalWeight
        val y = weightedY / totalWeight
        
        // Determine floor by max votes
        val floor = floorVotes.maxByOrNull { it.value }?.key ?: 1
        
        return Position(x, y, floor)
    }
    
    /**
     * Initialize sample known access point positions
     * In a real app, this data would come from Firebase
     */
    private fun initializeKnownAccessPoints() {
        // First floor access points
        knownAccessPoints["00:11:22:33:44:55"] = Position(15.0, 10.0, 1)
        knownAccessPoints["00:11:22:33:44:56"] = Position(35.0, 10.0, 1)
        knownAccessPoints["00:11:22:33:44:57"] = Position(25.0, 25.0, 1)
        knownAccessPoints["00:11:22:33:44:58"] = Position(45.0, 35.0, 1)
        
        // Second floor access points
        knownAccessPoints["00:11:22:33:44:59"] = Position(15.0, 10.0, 2)
        knownAccessPoints["00:11:22:33:44:60"] = Position(35.0, 10.0, 2)
        knownAccessPoints["00:11:22:33:44:61"] = Position(25.0, 25.0, 2)
    }
}