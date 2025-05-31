package com.example.indoornavigation.positioning

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import com.example.indoornavigation.data.models.Position
import kotlinx.coroutines.*
import kotlin.math.cos
import kotlin.math.sin

/**
 * Fallback positioning system that uses dead reckoning via
 * accelerometer and compass when primary positioning fails
 */
class FallbackPositioningSystem(
    private val context: Context,
    initialPosition: Position
) {
    private var lastKnownPosition = initialPosition.copy()
    private val deadReckoning = DeadReckoningCalculator()
    private val coroutineScope = CoroutineScope(Dispatchers.Default)
    
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
    private val stepCounter = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
    
    private var trackingJob: Job? = null
    private var positionUpdateCallback: ((Position) -> Unit)? = null
    
    // Sensor data
    private val accelerometerReading = FloatArray(3)
    private val magnetometerReading = FloatArray(3)
    private var lastStepCount = 0
    private var currentHeading = 0f
    private val rotationMatrix = FloatArray(9)
    private val orientationAngles = FloatArray(3)
    private val accelRingBuffer = FloatArray(10)  // Buffer for recent accelerometer readings
    private var accelRingCounter = 0
    private var totalAcceleration = 0f
    private var lastAccelTimestamp = 0L
    private var lastStepTimestamp = 0L
    private var lastAccelValues = floatArrayOf(0f, 0f, 0f)
    private val STEP_THRESHOLD = 11f       // Minimum acceleration for step
    private val STEP_DELAY_MS = 300        // Minimum time between steps
    
    // Confidence in the fallback position (decreases over time)
    private var confidence = 0.5f
    private val confidenceDecayRate = 0.01f // Reduce confidence by 1% per update
    
    // Sensor event listener
    private val sensorListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            when (event.sensor.type) {
                Sensor.TYPE_ACCELEROMETER -> {
                    System.arraycopy(event.values, 0, accelerometerReading, 0, accelerometerReading.size)
                    updateOrientationAngles()
                    lastAccelValues = event.values.clone()
                    lastAccelTimestamp = event.timestamp
                }
                Sensor.TYPE_MAGNETIC_FIELD -> {
                    System.arraycopy(event.values, 0, magnetometerReading, 0, magnetometerReading.size)
                    updateOrientationAngles()
                }
                Sensor.TYPE_STEP_COUNTER -> {
                    val steps = event.values[0].toInt()
                    if (lastStepCount > 0) {
                        val stepsDelta = steps - lastStepCount
                        if (stepsDelta > 0) {
                            updatePosition(stepsDelta)
                        }
                    }
                    lastStepCount = steps
                }
            }
        }
        
        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
            // Not needed for this implementation
        }
    }
    
    /**
     * Start tracking position using fallback methods
     */
    fun startTracking(callback: (Position) -> Unit) {
        positionUpdateCallback = callback
        
        // Register sensor listeners
        sensorManager.registerListener(
            sensorListener,
            accelerometer,
            SensorManager.SENSOR_DELAY_NORMAL
        )
        sensorManager.registerListener(
            sensorListener,
            magnetometer,
            SensorManager.SENSOR_DELAY_NORMAL
        )
        sensorManager.registerListener(
            sensorListener,
            stepCounter,
            SensorManager.SENSOR_DELAY_NORMAL
        )
        
        // Start tracking job
        trackingJob = coroutineScope.launch {
            while (isActive) {
                // If no step counter detected, use accelerometer-based step detection
                if (stepCounter == null) {
                    val stepCount = detectStepsFromAccelerometer()
                    if (stepCount > 0) {
                        updatePosition(stepCount)
                    }
                }
                
                // Decay confidence over time
                confidence = (confidence - confidenceDecayRate).coerceAtLeast(0.05f)
                
                delay(1000) // Update every second
            }
        }
    }
    
    /**
     * Stop tracking
     */
    fun stopTracking() {
        trackingJob?.cancel()
        sensorManager.unregisterListener(sensorListener)
    }
    
    /**
     * Update orientation angles using accelerometer and magnetometer
     */
    private fun updateOrientationAngles() {
        // Update rotation matrix, which is needed to update orientation angles
        SensorManager.getRotationMatrix(
            rotationMatrix,
            null,
            accelerometerReading,
            magnetometerReading
        )
        
        // Get orientation angles from rotation matrix
        SensorManager.getOrientation(rotationMatrix, orientationAngles)
        
        // Convert radians to degrees for easier understanding
        currentHeading = Math.toDegrees(orientationAngles[0].toDouble()).toFloat()
        
        // Normalize to 0-360 degrees
        if (currentHeading < 0) {
            currentHeading += 360f
        }
    }
    
    /**
     * Update position based on steps and heading
     */
    private fun updatePosition(steps: Int) {
        // Simple dead reckoning calculation
        val newPosition = deadReckoning.calculateNewPosition(
            lastKnownPosition,
            steps,
            currentHeading
        )
        
        // Update last known position
        lastKnownPosition = newPosition
        
        // Notify callback with new position
        positionUpdateCallback?.invoke(newPosition)
    }
    
    /**
     * Detect steps using accelerometer data
     * This is a simplified implementation and would need to be more sophisticated
     * in a real app
     */
    private fun detectStepsFromAccelerometer(): Int {
        if (lastAccelValues[0] == 0f && lastAccelValues[1] == 0f && lastAccelValues[2] == 0f) {
            return 0  // No accelerometer data available
        }
        
        // Calculate magnitude of acceleration
        val magnitude = Math.sqrt(
            (lastAccelValues[0] * lastAccelValues[0] +
             lastAccelValues[1] * lastAccelValues[1] +
             lastAccelValues[2] * lastAccelValues[2]).toDouble()
        ).toFloat()
        
        // Add to ring buffer and calculate moving average
        accelRingBuffer[accelRingCounter] = magnitude
        accelRingCounter = (accelRingCounter + 1) % accelRingBuffer.size
        
        // Calculate total acceleration to get average
        totalAcceleration = 0f
        accelRingBuffer.forEach { totalAcceleration += it }
        val avgAcceleration = totalAcceleration / accelRingBuffer.size
        
        val currentTime = System.currentTimeMillis()
        
        // Detect steps when acceleration is above threshold
        // and sufficient time has passed since last step
        if (magnitude > avgAcceleration + STEP_THRESHOLD &&
            (currentTime - lastStepTimestamp) > STEP_DELAY_MS) {
            lastStepTimestamp = currentTime
            return 1  // One step detected
        }
        
        return 0  // No step detected
    }
    
    /**
     * Calculator for dead reckoning
     */
    inner class DeadReckoningCalculator {
        private val stepLengthMeters = 0.75f // Average step length
        
        /**
         * Calculate new position based on step count and heading
         */
        fun calculateNewPosition(
            currentPosition: Position,
            steps: Int,
            heading: Float
        ): Position {
            // Convert heading to radians
            val headingRadians = Math.toRadians(heading.toDouble())
            
            // Calculate distance walked
            val distanceMeters = steps * stepLengthMeters
            
            // Calculate x and y offsets
            val xOffset = distanceMeters * sin(headingRadians)
            val yOffset = distanceMeters * cos(headingRadians)
            
            // Update position
            return Position(
                x = currentPosition.x + xOffset,
                y = currentPosition.y + yOffset,
                floor = currentPosition.floor
            )
        }
    }
}
