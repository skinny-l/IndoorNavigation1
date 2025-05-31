package com.example.indoornavigation.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.indoornavigation.MainActivity
import com.example.indoornavigation.R
import com.example.indoornavigation.data.bluetooth.BleManager
import com.example.indoornavigation.data.fusion.SensorFusionManager
import com.example.indoornavigation.data.models.Position
import com.example.indoornavigation.data.optimization.AdaptiveScanningManager
import com.example.indoornavigation.data.sensors.DeadReckoningManager
import com.example.indoornavigation.data.wifi.WifiPositioningManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Foreground service for continuous positioning
 * Ensures positioning continues when app is in background
 */
class PositioningService : Service() {

    private val TAG = "PositioningService"
    
    // Notification configuration
    private val NOTIFICATION_ID = 1001
    private val CHANNEL_ID = "positioning_channel"
    
    // Binder for client communication
    private val binder = LocalBinder()
    
    // Positioning components
    private lateinit var bleManager: BleManager
    private lateinit var wifiManager: WifiPositioningManager
    private lateinit var sensorFusionManager: SensorFusionManager
    private lateinit var deadReckoningManager: DeadReckoningManager
    private lateinit var adaptiveScanningManager: AdaptiveScanningManager
    
    // Coroutine scope
    private val serviceScope = CoroutineScope(Dispatchers.Default + Job())
    
    // Positioning data
    private val _currentPosition = MutableStateFlow<Position?>(null)
    val currentPosition: StateFlow<Position?> = _currentPosition.asStateFlow()
    
    // Service state
    private var isRunning = false
    private var useAdaptiveScanning = true
    
    override fun onCreate() {
        super.onCreate()
        
        // Initialize positioning components
        initializeComponents()
        
        // Initialize coroutines
        setupCoroutines()
        
        Log.d(TAG, "Positioning service created")
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Start as foreground service
        startForeground(NOTIFICATION_ID, createNotification())
        
        // Start positioning
        startPositioning()
        
        return START_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder {
        return binder
    }
    
    override fun onDestroy() {
        super.onDestroy()
        
        // Stop positioning
        stopPositioning()
        
        Log.d(TAG, "Positioning service destroyed")
    }
    
    /**
     * Initialize positioning components
     */
    private fun initializeComponents() {
        bleManager = BleManager(applicationContext)
        wifiManager = WifiPositioningManager(applicationContext)
        deadReckoningManager = DeadReckoningManager(applicationContext)
        adaptiveScanningManager = AdaptiveScanningManager(applicationContext)
        
        // Initialize sensor fusion with other components
        sensorFusionManager = SensorFusionManager(
            bleManager,
            wifiManager,
            serviceScope,
            applicationContext
        )
    }
    
    /**
     * Setup coroutines for data collection
     */
    private fun setupCoroutines() {
        // Collect position updates from sensor fusion
        serviceScope.launch {
            sensorFusionManager.fusedPosition.collectLatest { position ->
                position?.let {
                    _currentPosition.value = it
                    updateNotification(it)
                    
                    // Update dead reckoning with new position
                    deadReckoningManager.updatePosition(it)
                }
            }
        }
        
        // Collect dead reckoning updates when no BLE/WiFi signals
        serviceScope.launch {
            deadReckoningManager.estimatedPosition.collectLatest { position ->
                // Only use dead reckoning when no position from other sensors
                if (_currentPosition.value == null) {
                    _currentPosition.value = position
                    updateNotification(position)
                }
            }
        }
        
        // Adaptive scanning interval manager
        serviceScope.launch {
            adaptiveScanningManager.currentScanInterval.collectLatest { interval ->
                if (useAdaptiveScanning && isRunning) {
                    // Restart scanning with new interval
                    restartScanningWithInterval(interval)
                }
            }
        }
    }
    
    /**
     * Start positioning
     */
    fun startPositioning() {
        if (isRunning) return
        
        // Start sensor fusion
        sensorFusionManager.startScanning()
        
        // Start dead reckoning
        deadReckoningManager.start()
        
        // Start adaptive scanning manager
        adaptiveScanningManager.start()
        
        isRunning = true
        Log.d(TAG, "Positioning started")
    }
    
    /**
     * Stop positioning
     */
    fun stopPositioning() {
        if (!isRunning) return
        
        // Stop sensor fusion
        sensorFusionManager.stopScanning()
        
        // Stop dead reckoning
        deadReckoningManager.stop()
        
        // Stop adaptive scanning manager
        adaptiveScanningManager.stop()
        
        isRunning = false
        Log.d(TAG, "Positioning stopped")
    }
    
    /**
     * Restart scanning with new interval
     */
    private fun restartScanningWithInterval(interval: Long) {
        // Stop current scanning
        sensorFusionManager.stopScanning()
        
        // Start adaptive scanning job
        serviceScope.launch {
            while (isRunning && useAdaptiveScanning) {
                // Start scanning
                sensorFusionManager.startScanning()
                
                // Wait for scan duration
                delay(2000) // Scan for 2 seconds
                
                // Stop scanning
                sensorFusionManager.stopScanning()
                
                // Wait for interval between scans
                // Use the latest interval from adaptive manager
                val currentInterval = adaptiveScanningManager.getCurrentScanInterval()
                delay(currentInterval)
            }
        }
    }
    
    /**
     * Set adaptive scanning enabled/disabled
     */
    fun setAdaptiveScanning(enabled: Boolean) {
        useAdaptiveScanning = enabled
        
        if (isRunning) {
            if (enabled) {
                // Restart with adaptive scanning
                restartScanningWithInterval(adaptiveScanningManager.getCurrentScanInterval())
            } else {
                // Stop adaptive scanning job and start continuous scanning
                sensorFusionManager.startScanning()
            }
        }
    }
    
    /**
     * Create the service notification
     */
    private fun createNotification(): Notification {
        // Create notification channel
        createNotificationChannel()
        
        // Notification intent
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        )
        
        // Build notification
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.positioning_active))
            .setSmallIcon(R.drawable.ic_location)
            .setContentIntent(pendingIntent)
            .build()
    }
    
    /**
     * Create notification channel
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = getString(R.string.positioning_channel_name)
            val description = getString(R.string.positioning_channel_description)
            val importance = NotificationManager.IMPORTANCE_LOW
            
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                this.description = description
            }
            
            // Register channel
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    /**
     * Update notification with current position
     */
    private fun updateNotification(position: Position?) {
        if (position == null) return
        
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        // Create updated notification
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.current_position, 
                position.x.toInt(), position.y.toInt(), position.floor))
            .setSmallIcon(R.drawable.ic_location)
            .build()
        
        // Update the notification
        notificationManager.notify(NOTIFICATION_ID, notification)
    }
    
    /**
     * Local binder class
     */
    inner class LocalBinder : Binder() {
        fun getService(): PositioningService = this@PositioningService
    }
}
