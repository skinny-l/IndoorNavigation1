package com.example.indoornavigation.power

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.OnLifecycleEvent
import com.example.indoornavigation.analytics.AnalyticsManager
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.CoroutineContext

/**
 * Comprehensive battery optimization manager for efficient power management
 */
class BatteryOptimizationManager(
    private val context: Context
) : CoroutineScope, LifecycleObserver {

    private val job = SupervisorJob()
    override val coroutineContext: CoroutineContext = Dispatchers.IO + job

    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    private val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
    private val preferences: SharedPreferences = context.getSharedPreferences("battery_prefs", Context.MODE_PRIVATE)
    private val analytics = AnalyticsManager.getInstance(context)

    // Battery state tracking
    private var currentBatteryLevel = 100
    private var isCharging = false
    private var isLowPowerMode = false
    private var isDozeMode = false
    
    // Power management components
    private val powerConsumers = ConcurrentHashMap<String, PowerConsumer>()
    private var batteryOptimizationLevel = BatteryOptimizationLevel.BALANCED
    
    // Scanning intervals for different battery levels
    private val scanIntervals = mapOf(
        BatteryOptimizationLevel.PERFORMANCE to ScanIntervals(
            bleInterval = 1000L,
            wifiInterval = 5000L,
            positionUpdateInterval = 2000L
        ),
        BatteryOptimizationLevel.BALANCED to ScanIntervals(
            bleInterval = 2000L,
            wifiInterval = 10000L,
            positionUpdateInterval = 5000L
        ),
        BatteryOptimizationLevel.POWER_SAVING to ScanIntervals(
            bleInterval = 5000L,
            wifiInterval = 20000L,
            positionUpdateInterval = 10000L
        ),
        BatteryOptimizationLevel.ULTRA_POWER_SAVING to ScanIntervals(
            bleInterval = 15000L,
            wifiInterval = 60000L,
            positionUpdateInterval = 30000L
        )
    )

    init {
        loadBatterySettings()
        startBatteryMonitoring()
    }

    companion object {
        @Volatile
        private var INSTANCE: BatteryOptimizationManager? = null

        fun getInstance(context: Context): BatteryOptimizationManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: BatteryOptimizationManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    /**
     * Start monitoring battery status
     */
    private fun startBatteryMonitoring() {
        launch {
            while (isActive) {
                updateBatteryStatus()
                adjustOptimizationLevel()
                delay(30000) // Check every 30 seconds
            }
        }
    }

    /**
     * Update current battery status
     */
    private fun updateBatteryStatus() {
        try {
            val batteryStatus = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            batteryStatus?.let { intent ->
                currentBatteryLevel = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, 100)
                val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                           status == BatteryManager.BATTERY_STATUS_FULL

                // Check for low power mode
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    isLowPowerMode = powerManager.isPowerSaveMode
                }

                // Check for doze mode
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    isDozeMode = powerManager.isDeviceIdleMode
                }
            }
        } catch (e: Exception) {
            Log.e("BatteryOptimization", "Failed to update battery status", e)
        }
    }

    /**
     * Automatically adjust optimization level based on battery status
     */
    private fun adjustOptimizationLevel() {
        val newLevel = when {
            isCharging -> {
                // When charging, we can be more performance-oriented
                if (currentBatteryLevel > 50) {
                    BatteryOptimizationLevel.PERFORMANCE
                } else {
                    BatteryOptimizationLevel.BALANCED
                }
            }
            isLowPowerMode || isDozeMode -> {
                // System is in power saving mode
                BatteryOptimizationLevel.ULTRA_POWER_SAVING
            }
            currentBatteryLevel <= 15 -> {
                // Critical battery level
                BatteryOptimizationLevel.ULTRA_POWER_SAVING
            }
            currentBatteryLevel <= 30 -> {
                // Low battery level
                BatteryOptimizationLevel.POWER_SAVING
            }
            currentBatteryLevel <= 50 -> {
                // Medium battery level
                BatteryOptimizationLevel.BALANCED
            }
            else -> {
                // High battery level
                BatteryOptimizationLevel.PERFORMANCE
            }
        }

        if (newLevel != batteryOptimizationLevel) {
            setBatteryOptimizationLevel(newLevel)
        }
    }

    /**
     * Set battery optimization level
     */
    fun setBatteryOptimizationLevel(level: BatteryOptimizationLevel) {
        val previousLevel = batteryOptimizationLevel
        batteryOptimizationLevel = level
        
        preferences.edit().putString("optimization_level", level.name).apply()
        
        Log.d("BatteryOptimization", "Optimization level changed from $previousLevel to $level")
        
        // Notify all registered power consumers
        powerConsumers.values.forEach { consumer ->
            try {
                consumer.onBatteryOptimizationChanged(level)
            } catch (e: Exception) {
                Log.e("BatteryOptimization", "Failed to notify power consumer: ${consumer.name}", e)
            }
        }
        
        analytics.trackPerformance("battery_optimization_changed", level.ordinal.toDouble())
    }

    /**
     * Register a power consumer
     */
    fun registerPowerConsumer(consumer: PowerConsumer) {
        powerConsumers[consumer.name] = consumer
        consumer.onBatteryOptimizationChanged(batteryOptimizationLevel)
        Log.d("BatteryOptimization", "Registered power consumer: ${consumer.name}")
    }

    /**
     * Unregister a power consumer
     */
    fun unregisterPowerConsumer(name: String) {
        powerConsumers.remove(name)
        Log.d("BatteryOptimization", "Unregistered power consumer: $name")
    }

    /**
     * Get current scan intervals based on optimization level
     */
    fun getCurrentScanIntervals(): ScanIntervals {
        return scanIntervals[batteryOptimizationLevel] ?: scanIntervals[BatteryOptimizationLevel.BALANCED]!!
    }

    /**
     * Get BLE scan interval
     */
    fun getBleScanInterval(): Long {
        return getCurrentScanIntervals().bleInterval
    }

    /**
     * Get WiFi scan interval
     */
    fun getWifiScanInterval(): Long {
        return getCurrentScanIntervals().wifiInterval
    }

    /**
     * Get position update interval
     */
    fun getPositionUpdateInterval(): Long {
        return getCurrentScanIntervals().positionUpdateInterval
    }

    /**
     * Check if background scanning should be enabled
     */
    fun shouldEnableBackgroundScanning(): Boolean {
        return when (batteryOptimizationLevel) {
            BatteryOptimizationLevel.PERFORMANCE -> true
            BatteryOptimizationLevel.BALANCED -> !isDozeMode
            BatteryOptimizationLevel.POWER_SAVING -> false
            BatteryOptimizationLevel.ULTRA_POWER_SAVING -> false
        }
    }

    /**
     * Check if high accuracy positioning should be enabled
     */
    fun shouldEnableHighAccuracyPositioning(): Boolean {
        return when (batteryOptimizationLevel) {
            BatteryOptimizationLevel.PERFORMANCE -> true
            BatteryOptimizationLevel.BALANCED -> true
            BatteryOptimizationLevel.POWER_SAVING -> false
            BatteryOptimizationLevel.ULTRA_POWER_SAVING -> false
        }
    }

    /**
     * Check if continuous positioning should be enabled
     */
    fun shouldEnableContinuousPositioning(): Boolean {
        return when (batteryOptimizationLevel) {
            BatteryOptimizationLevel.PERFORMANCE -> true
            BatteryOptimizationLevel.BALANCED -> !isDozeMode
            BatteryOptimizationLevel.POWER_SAVING -> false
            BatteryOptimizationLevel.ULTRA_POWER_SAVING -> false
        }
    }

    /**
     * Get recommended number of concurrent BLE scans
     */
    fun getRecommendedBleScanCount(): Int {
        return when (batteryOptimizationLevel) {
            BatteryOptimizationLevel.PERFORMANCE -> 5
            BatteryOptimizationLevel.BALANCED -> 3
            BatteryOptimizationLevel.POWER_SAVING -> 2
            BatteryOptimizationLevel.ULTRA_POWER_SAVING -> 1
        }
    }

    /**
     * Get recommended WiFi scan count
     */
    fun getRecommendedWifiScanCount(): Int {
        return when (batteryOptimizationLevel) {
            BatteryOptimizationLevel.PERFORMANCE -> 10
            BatteryOptimizationLevel.BALANCED -> 6
            BatteryOptimizationLevel.POWER_SAVING -> 3
            BatteryOptimizationLevel.ULTRA_POWER_SAVING -> 1
        }
    }

    /**
     * Check if analytics should be enabled
     */
    fun shouldEnableAnalytics(): Boolean {
        return when (batteryOptimizationLevel) {
            BatteryOptimizationLevel.PERFORMANCE -> true
            BatteryOptimizationLevel.BALANCED -> true
            BatteryOptimizationLevel.POWER_SAVING -> false
            BatteryOptimizationLevel.ULTRA_POWER_SAVING -> false
        }
    }

    /**
     * Get current battery information
     */
    fun getBatteryInfo(): BatteryInfo {
        return BatteryInfo(
            level = currentBatteryLevel,
            isCharging = isCharging,
            isLowPowerMode = isLowPowerMode,
            isDozeMode = isDozeMode,
            optimizationLevel = batteryOptimizationLevel
        )
    }

    /**
     * Estimate battery impact of a feature
     */
    fun estimateBatteryImpact(feature: String): BatteryImpact {
        return when (feature.lowercase()) {
            "ble_scanning" -> when (batteryOptimizationLevel) {
                BatteryOptimizationLevel.PERFORMANCE -> BatteryImpact.HIGH
                BatteryOptimizationLevel.BALANCED -> BatteryImpact.MEDIUM
                BatteryOptimizationLevel.POWER_SAVING -> BatteryImpact.LOW
                BatteryOptimizationLevel.ULTRA_POWER_SAVING -> BatteryImpact.MINIMAL
            }
            "wifi_scanning" -> when (batteryOptimizationLevel) {
                BatteryOptimizationLevel.PERFORMANCE -> BatteryImpact.MEDIUM
                BatteryOptimizationLevel.BALANCED -> BatteryImpact.LOW
                BatteryOptimizationLevel.POWER_SAVING -> BatteryImpact.LOW
                BatteryOptimizationLevel.ULTRA_POWER_SAVING -> BatteryImpact.MINIMAL
            }
            "continuous_positioning" -> when (batteryOptimizationLevel) {
                BatteryOptimizationLevel.PERFORMANCE -> BatteryImpact.HIGH
                BatteryOptimizationLevel.BALANCED -> BatteryImpact.MEDIUM
                else -> BatteryImpact.MINIMAL
            }
            "analytics" -> BatteryImpact.LOW
            else -> BatteryImpact.MEDIUM
        }
    }

    /**
     * Load saved battery settings
     */
    private fun loadBatterySettings() {
        val savedLevel = preferences.getString("optimization_level", BatteryOptimizationLevel.BALANCED.name)
        batteryOptimizationLevel = try {
            BatteryOptimizationLevel.valueOf(savedLevel ?: BatteryOptimizationLevel.BALANCED.name)
        } catch (e: Exception) {
            BatteryOptimizationLevel.BALANCED
        }
    }

    /**
     * Lifecycle events
     */
    @OnLifecycleEvent(Lifecycle.Event.ON_RESUME)
    fun onAppResume() {
        Log.d("BatteryOptimization", "App resumed - updating battery status")
        updateBatteryStatus()
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_PAUSE)
    fun onAppPause() {
        Log.d("BatteryOptimization", "App paused - reducing power consumption")
        // Reduce power consumption when app is in background
        powerConsumers.values.forEach { consumer ->
            try {
                consumer.onAppStateChanged(false)
            } catch (e: Exception) {
                Log.e("BatteryOptimization", "Failed to notify app state change", e)
            }
        }
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_STOP)
    fun onAppStop() {
        Log.d("BatteryOptimization", "App stopped - entering background mode")
        // Further reduce power consumption
        if (!shouldEnableBackgroundScanning()) {
            powerConsumers.values.forEach { consumer ->
                try {
                    consumer.onBackgroundModeChanged(true)
                } catch (e: Exception) {
                    Log.e("BatteryOptimization", "Failed to notify background mode change", e)
                }
            }
        }
    }

    /**
     * Get battery usage statistics
     */
    fun getBatteryUsageStats(): BatteryUsageStats {
        val stats = BatteryUsageStats()
        
        powerConsumers.values.forEach { consumer ->
            try {
                val usage = consumer.getCurrentPowerUsage()
                stats.addConsumerUsage(consumer.name, usage)
            } catch (e: Exception) {
                Log.e("BatteryOptimization", "Failed to get power usage for ${consumer.name}", e)
            }
        }
        
        return stats
    }

    /**
     * Cleanup resources
     */
    fun cleanup() {
        powerConsumers.clear()
        job.cancel()
    }
}

/**
 * Battery optimization levels
 */
enum class BatteryOptimizationLevel {
    PERFORMANCE,
    BALANCED,
    POWER_SAVING,
    ULTRA_POWER_SAVING
}

/**
 * Battery impact levels
 */
enum class BatteryImpact {
    MINIMAL,
    LOW,
    MEDIUM,
    HIGH
}

/**
 * Scan intervals for different optimization levels
 */
data class ScanIntervals(
    val bleInterval: Long,
    val wifiInterval: Long,
    val positionUpdateInterval: Long
)

/**
 * Battery information
 */
data class BatteryInfo(
    val level: Int,
    val isCharging: Boolean,
    val isLowPowerMode: Boolean,
    val isDozeMode: Boolean,
    val optimizationLevel: BatteryOptimizationLevel
)

/**
 * Interface for components that consume power
 */
interface PowerConsumer {
    val name: String
    
    fun onBatteryOptimizationChanged(level: BatteryOptimizationLevel)
    fun onAppStateChanged(isActive: Boolean)
    fun onBackgroundModeChanged(isBackground: Boolean)
    fun getCurrentPowerUsage(): Double // Returns power usage in arbitrary units
}

/**
 * Battery usage statistics
 */
class BatteryUsageStats {
    private val consumerUsage = mutableMapOf<String, Double>()
    
    fun addConsumerUsage(consumerName: String, usage: Double) {
        consumerUsage[consumerName] = usage
    }
    
    fun getTotalUsage(): Double {
        return consumerUsage.values.sum()
    }
    
    fun getConsumerUsage(consumerName: String): Double {
        return consumerUsage[consumerName] ?: 0.0
    }
    
    fun getAllConsumerUsage(): Map<String, Double> {
        return consumerUsage.toMap()
    }
    
    fun getTopConsumers(count: Int = 5): List<Pair<String, Double>> {
        return consumerUsage.toList()
            .sortedByDescending { it.second }
            .take(count)
    }
}