package com.example.indoornavigation.data.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import com.example.indoornavigation.data.models.Position
import com.example.indoornavigation.data.models.StepData
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manager for dead reckoning using device sensors
 * Uses accelerometer for step detection and magnetometer + gyroscope for heading
 */
class DeadReckoningManager(private val context: Context) : SensorEventListener {
    
    private val TAG = "DeadReckoningManager"
    
    // Sensor manager and sensors
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
    private val gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    private val rotationVector = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    
    // Sensor data
    private val accelerometerData = FloatArray(3)
    private val magnetometerData = FloatArray(3)
    private val rotationMatrix = FloatArray(9)
    private val orientationAngles = FloatArray(3)
    
    // Step detection
    private var lastAcceleration = 0f
    private var stepCount = 0
    private var STEP_THRESHOLD = 1.0f // Customizable threshold for step detection
    private var STEP_LENGTH = 0.75 // Average step length in meters (customizable)
    
    // Heading
    private var heading = 0.0 // Current heading in radians
    
    // Dead reckoning state
    private var isRunning = false
    private var lastPosition: Position? = null
    private var lastStepTimestamp = 0L
    
    // Step data
    private val _stepData = MutableStateFlow<StepData?>(null)
    val stepData: StateFlow<StepData?> = _stepData.asStateFlow()
    
    // Position updates from dead reckoning
    private val _estimatedPosition = MutableStateFlow<Position?>(null)
    val estimatedPosition: StateFlow<Position?> = _estimatedPosition.asStateFlow()
    
    /**
     * Start dead reckoning
     */
    fun start() {
        if (isRunning) return
        
        // Register listeners
        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME)
        sensorManager.registerListener(this, magnetometer, SensorManager.SENSOR_DELAY_GAME)
        sensorManager.registerListener(this, rotationVector, SensorManager.SENSOR_DELAY_GAME)
        
        isRunning = true
        Log.d(TAG, "Dead reckoning started")
    }
    
    /**
     * Stop dead reckoning
     */
    fun stop() {
        if (!isRunning) return
        
        // Unregister listeners
        sensorManager.unregisterListener(this)
        
        isRunning = false
        Log.d(TAG, "Dead reckoning stopped")
    }
    
    /**
     * Set initial position for dead reckoning
     */
    fun setInitialPosition(position: Position) {
        lastPosition = position
        _estimatedPosition.value = position
        Log.d(TAG, "Initial position set: $position")
    }
    
    /**
     * Update position from external source (to correct drift)
     */
    fun updatePosition(position: Position) {
        lastPosition = position
        _estimatedPosition.value = position
        Log.d(TAG, "Position updated from external source: $position")
    }
    
    /**
     * Customize step detection parameters
     */
    fun setStepParameters(threshold: Float, stepLength: Double) {
        this.STEP_THRESHOLD = threshold
        this.STEP_LENGTH = stepLength
    }
    
    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                System.arraycopy(event.values, 0, accelerometerData, 0, 3)
                detectStep(event)
            }
            Sensor.TYPE_MAGNETIC_FIELD -> {
                System.arraycopy(event.values, 0, magnetometerData, 0, 3)
                updateHeading()
            }
            Sensor.TYPE_ROTATION_VECTOR -> {
                // Extract rotation from rotation vector
                SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                SensorManager.getOrientation(rotationMatrix, orientationAngles)
                heading = orientationAngles[0].toDouble() // Azimuth in radians
            }
        }
    }
    
    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
        // Handle accuracy changes if needed
    }
    
    /**
     * Detect steps using accelerometer data
     */
    private fun detectStep(event: SensorEvent) {
        // Calculate magnitude of acceleration
        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]
        val acceleration = kotlin.math.sqrt((x * x + y * y + z * z).toDouble()).toFloat()
        
        // Detect step using peak detection
        val delta = acceleration - lastAcceleration
        lastAcceleration = acceleration
        
        // Ensure minimum time between steps (prevent double counting)
        val now = System.currentTimeMillis()
        if (now - lastStepTimestamp < 250) {
            return
        }
        
        // Check for step pattern (crossing threshold)
        if (delta > STEP_THRESHOLD) {
            stepCount++
            lastStepTimestamp = now
            
            // Update position based on step
            updatePositionWithStep()
            
            // Update step data
            _stepData.value = StepData(
                count = stepCount,
                heading = Math.toDegrees(heading).toFloat(),
                timestamp = now
            )
            
            Log.d(TAG, "Step detected: $stepCount, Heading: ${Math.toDegrees(heading)}")
        }
    }
    
    /**
     * Update heading using magnetometer and accelerometer
     */
    private fun updateHeading() {
        // Calculate rotation matrix
        SensorManager.getRotationMatrix(
            rotationMatrix,
            null,
            accelerometerData,
            magnetometerData
        )
        
        // Get orientation angles
        SensorManager.getOrientation(rotationMatrix, orientationAngles)
        
        // Update heading (azimuth)
        heading = orientationAngles[0].toDouble() // in radians
    }
    
    /**
     * Update position based on detected step
     */
    private fun updatePositionWithStep() {
        val currentPosition = lastPosition ?: return
        
        // Calculate new position using step length and heading
        val dx = STEP_LENGTH * sin(heading)
        val dy = STEP_LENGTH * cos(heading)
        
        // Update position
        val newPosition = Position(
            x = currentPosition.x + dx,
            y = currentPosition.y + dy,
            floor = currentPosition.floor
        )
        
        lastPosition = newPosition
        _estimatedPosition.value = newPosition
    }
    
    /**
     * Check if device has required sensors
     */
    fun hasSensors(): Boolean {
        return accelerometer != null && (magnetometer != null || rotationVector != null)
    }
}