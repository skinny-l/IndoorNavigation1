package com.example.indoornavigation.data.optimization

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Manager for adaptive scanning rates based on user movement
 * Reduces scanning frequency when user is stationary to conserve battery
 */
class AdaptiveScanningManager(private val context: Context) : SensorEventListener {
    
    private val TAG = "AdaptiveScanningManager"
    
    // Sensor manager and accelerometer
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    
    // Movement detection
    private var isMoving = AtomicBoolean(false)
    private var lastAcceleration = 0f
    private var movementThreshold = 1.5f  // Customizable threshold
    private var stationaryCounter = 0
    private val STATIONARY_THRESHOLD = 10  // Number of readings to determine stationary state
    
    // Scan intervals (in milliseconds)
    private var highFrequencyInterval = 1000L  // 1 second (when moving)
    private var lowFrequencyInterval = 5000L   // 5 seconds (when stationary)
    
    // Current scan interval
    private val _currentScanInterval = MutableStateFlow(highFrequencyInterval)
    val currentScanInterval: StateFlow<Long> = _currentScanInterval.asStateFlow()
    
    // Movement state
    private val _movementState = MutableStateFlow(MovementState.UNKNOWN)
    val movementState: StateFlow<MovementState> = _movementState.asStateFlow()
    
    /**
     * Start monitoring movement
     */
    fun start() {
        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL)
        Log.d(TAG, "Adaptive scanning started")
    }
    
    /**
     * Stop monitoring movement
     */
    fun stop() {
        sensorManager.unregisterListener(this)
        Log.d(TAG, "Adaptive scanning stopped")
    }
    
    /**
     * Set scan intervals
     */
    fun setScanIntervals(highFrequency: Long, lowFrequency: Long) {
        this.highFrequencyInterval = highFrequency
        this.lowFrequencyInterval = lowFrequency
        
        // Update current interval if needed
        if (isMoving.get()) {
            _currentScanInterval.value = highFrequencyInterval
        } else {
            _currentScanInterval.value = lowFrequencyInterval
        }
    }
    
    /**
     * Set movement threshold
     */
    fun setMovementThreshold(threshold: Float) {
        this.movementThreshold = threshold
    }
    
    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            detectMovement(event)
        }
    }
    
    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
        // Not used
    }
    
    /**
     * Detect movement using accelerometer data
     */
    private fun detectMovement(event: SensorEvent) {
        // Calculate magnitude of acceleration
        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]
        val acceleration = sqrt((x * x + y * y + z * z).toDouble()).toFloat()
        
        // Calculate acceleration delta
        val delta = Math.abs(acceleration - lastAcceleration)
        lastAcceleration = acceleration
        
        // Check for movement
        if (delta > movementThreshold) {
            // Movement detected
            stationaryCounter = 0
            
            if (!isMoving.get()) {
                isMoving.set(true)
                _currentScanInterval.value = highFrequencyInterval
                _movementState.value = MovementState.MOVING
                Log.d(TAG, "Movement detected, switching to high frequency scanning")
            }
        } else {
            // Check if stationary
            stationaryCounter++
            
            if (stationaryCounter >= STATIONARY_THRESHOLD && isMoving.get()) {
                isMoving.set(false)
                _currentScanInterval.value = lowFrequencyInterval
                _movementState.value = MovementState.STATIONARY
                Log.d(TAG, "Stationary state detected, switching to low frequency scanning")
            }
        }
    }
    
    /**
     * Get current scan interval based on movement state
     */
    fun getCurrentScanInterval(): Long {
        return _currentScanInterval.value
    }
    
    /**
     * Movement states
     */
    enum class MovementState {
        UNKNOWN,
        MOVING,
        STATIONARY
    }
}