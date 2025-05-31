package com.example.indoornavigation.data.optimization

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.net.wifi.WifiManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.indoornavigation.data.bluetooth.BleManager
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Service that optimizes battery consumption during positioning operations
 * by adjusting scan intervals based on movement and chosen power mode
 */
class BatteryOptimizedPositioningService(
    private val context: Context,
    private val bleManager: BleManager,
    private val wifiManager: WifiManager
) {
    private val TAG = "BatteryOptimizedService"
    
    // Positioning mode
    private var positioningMode = PositioningMode.BALANCED
    private var lastHighAccuracyScan = 0L
    
    // Motion detector for adaptive scanning
    private val motionDetector = MotionDetector(context)
    private var isMoving = false
    
    // Handler for scheduled scans
    private val handler = Handler(Looper.getMainLooper())
    private var scanRunnable: Runnable? = null
    
    /**
     * Available positioning modes with different battery usage profiles
     */
    enum class PositioningMode {
        HIGH_ACCURACY,   // Frequent scans, high battery usage
        BALANCED,        // Moderate scan frequency
        BATTERY_SAVING   // Minimal scans, lower accuracy
    }
    
    init {
        // Set up motion detection
        motionDetector.setOnMotionChangedListener { moving ->
            isMoving = moving
            updateScanIntervals()
            
            Log.d(TAG, "Motion state changed: moving = $moving")
        }
    }
    
    /**
     * Set the positioning mode to control battery consumption
     */
    fun setPositioningMode(mode: PositioningMode) {
        positioningMode = mode
        updateScanIntervals()
        
        Log.d(TAG, "Positioning mode set to: $mode")
    }
    
    /**
     * Start the battery optimization service
     */
    fun start() {
        motionDetector.start()
        updateScanIntervals()
        
        Log.d(TAG, "Battery optimized positioning started")
    }
    
    /**
     * Stop the battery optimization service
     */
    fun stop() {
        motionDetector.stop()
        scanRunnable?.let { handler.removeCallbacks(it) }
        
        Log.d(TAG, "Battery optimized positioning stopped")
    }
    
    /**
     * Update scanning intervals based on current mode and motion state
     */
    private fun updateScanIntervals() {
        // Cancel any pending scans
        scanRunnable?.let { handler.removeCallbacks(it) }
        
        // Determine scan intervals based on mode and movement
        val (scanInterval, scanDuration) = when (positioningMode) {
            PositioningMode.HIGH_ACCURACY -> {
                if (isMoving) {
                    Pair(1000L, 0L)  // Continuous scanning when moving
                } else {
                    Pair(3000L, 0L)  // Slight delay when stationary
                }
            }
            PositioningMode.BALANCED -> {
                if (isMoving) {
                    Pair(2000L, 500L)  // Regular scanning when moving
                } else {
                    Pair(10000L, 1000L)  // Reduced scanning when stationary
                }
            }
            PositioningMode.BATTERY_SAVING -> {
                if (isMoving) {
                    Pair(5000L, 1000L)  // Limited scanning when moving
                } else {
                    Pair(30000L, 2000L)  // Minimal scanning when stationary
                }
            }
        }
        
        // Apply new scan intervals to BLE manager
        bleManager.setScanPeriods(scanDuration, scanInterval)
        
        // Schedule Wi-Fi scans if needed (based on positioning mode)
        scheduleWifiScan(scanInterval * 3)  // Less frequent than BLE
        
        Log.d(TAG, "Updated scan intervals: interval=$scanInterval, duration=$scanDuration")
    }
    
    /**
     * Schedule a WiFi scan at the specified interval
     */
    private fun scheduleWifiScan(interval: Long) {
        scanRunnable = Runnable {
            // Only perform scan if WiFi is enabled
            if (wifiManager.isWifiEnabled) {
                wifiManager.startScan()
                
                // Record time of high accuracy scan
                if (positioningMode == PositioningMode.HIGH_ACCURACY) {
                    lastHighAccuracyScan = System.currentTimeMillis()
                }
            }
            
            // Schedule the next scan
            handler.postDelayed(scanRunnable!!, interval)
        }
        
        // Start the first scan
        handler.postDelayed(scanRunnable!!, interval)
    }
    
    /**
     * Get current positioning mode
     */
    fun getPositioningMode(): PositioningMode {
        return positioningMode
    }
    
    /**
     * Motion detector class that uses accelerometer to detect user movement
     */
    inner class MotionDetector(private val context: Context) : SensorEventListener {
        private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        private val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        
        private var isRegistered = false
        private var listener: ((Boolean) -> Unit)? = null
        
        private var lastX = 0f
        private var lastY = 0f
        private var lastZ = 0f
        private var lastTime = 0L
        
        // Motion detection thresholds
        private val MOVEMENT_THRESHOLD = 1.5f  // Acceleration threshold to detect movement
        private val STATIONARY_TIMEOUT = 3000L  // Time without movement to consider stationary
        private val SAMPLING_PERIOD = 500000  // Accelerometer sampling period (0.5 seconds)
        
        private var lastMovementTime = 0L
        private var isCurrentlyMoving = false
        
        // Handler for checking stationary state
        private val stationaryHandler = Handler(Looper.getMainLooper())
        private val stationaryRunnable = Runnable {
            val timeElapsed = System.currentTimeMillis() - lastMovementTime
            if (timeElapsed > STATIONARY_TIMEOUT && isCurrentlyMoving) {
                isCurrentlyMoving = false
                listener?.invoke(false)
            }
        }
        
        /**
         * Start monitoring motion
         */
        fun start() {
            if (!isRegistered) {
                sensorManager.registerListener(
                    this, 
                    accelerometer, 
                    SAMPLING_PERIOD
                )
                isRegistered = true
                
                // Initialize values
                lastTime = System.currentTimeMillis()
                lastMovementTime = lastTime
            }
        }
        
        /**
         * Stop monitoring motion
         */
        fun stop() {
            if (isRegistered) {
                sensorManager.unregisterListener(this)
                isRegistered = false
                stationaryHandler.removeCallbacks(stationaryRunnable)
            }
        }
        
        /**
         * Set listener for motion state changes
         */
        fun setOnMotionChangedListener(listener: (Boolean) -> Unit) {
            this.listener = listener
        }
        
        override fun onSensorChanged(event: SensorEvent) {
            if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
                val currentTime = System.currentTimeMillis()
                
                // Only process at certain intervals to reduce CPU usage
                if (currentTime - lastTime < 250) {
                    return
                }
                
                val x = event.values[0]
                val y = event.values[1]
                val z = event.values[2]
                
                // Calculate movement delta (excluding gravity by focusing on changes)
                val deltaX = x - lastX
                val deltaY = y - lastY
                val deltaZ = z - lastZ
                
                // Calculate movement magnitude
                val movement = sqrt(deltaX.pow(2) + deltaY.pow(2) + deltaZ.pow(2))
                
                // Update last values
                lastX = x
                lastY = y
                lastZ = z
                lastTime = currentTime
                
                // Check if movement exceeds threshold
                if (movement > MOVEMENT_THRESHOLD) {
                    lastMovementTime = currentTime
                    
                    // Notify movement if not already in moving state
                    if (!isCurrentlyMoving) {
                        isCurrentlyMoving = true
                        listener?.invoke(true)
                    }
                }
                
                // Schedule stationary check
                stationaryHandler.removeCallbacks(stationaryRunnable)
                stationaryHandler.postDelayed(stationaryRunnable, STATIONARY_TIMEOUT)
            }
        }
        
        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
            // Not needed for motion detection
        }
    }
}