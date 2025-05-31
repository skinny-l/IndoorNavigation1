package com.example.indoornavigation.data.bluetooth

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.*
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelUuid
import android.util.Log
import androidx.core.app.ActivityCompat
import com.example.indoornavigation.data.models.Beacon
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.pow

/**
 * Manager class for BLE scanning and beacon detection
 */
class BleManager(private val context: Context) {
    private val TAG = "BleManager"
    
    // BLE scanner
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter = bluetoothManager.adapter
    private var bleScanner: BluetoothLeScanner? = null
    
    // Detected beacons map (id -> Beacon)
    private val _detectedBeacons = MutableStateFlow<Map<String, Beacon>>(emptyMap())
    val detectedBeacons: StateFlow<Map<String, Beacon>> = _detectedBeacons.asStateFlow()
    
    // Scan settings
    private var scanSettings = ScanSettings.Builder()
        .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
        .setReportDelay(0) // Report results immediately
        .build()
    
    // No filters - scan all BLE devices to maximize discovery
    private val scanFilters = emptyList<ScanFilter>()
    
    // Scan callback
    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            processResult(result)
        }
        
        override fun onBatchScanResults(results: List<ScanResult>) {
            results.forEach { processResult(it) }
        }
        
        override fun onScanFailed(errorCode: Int) {
            Log.e(TAG, "BLE scan failed with error code: $errorCode")
        }
    }
    
    /**
     * Start BLE scanning for beacons
     */
    fun startScanning() {
        // Check if Bluetooth is enabled
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            Log.e(TAG, "Bluetooth is not enabled")
            return
        }
        
        // Check for necessary permissions
        if (!hasRequiredPermissions()) {
            Log.e(TAG, "Missing required permissions for BLE scanning")
            return
        }
        
        try {
            // Get scanner instance
            bleScanner = bluetoothAdapter.bluetoothLeScanner
            
            // Check if scanner is available
            if (bleScanner == null) {
                Log.e(TAG, "BluetoothLeScanner is not available")
                return
            }
            
            // Start scanning
            if (hasRequiredPermissions()) {
                try {
                    bleScanner?.startScan(scanFilters, scanSettings, scanCallback)
                    Log.d(TAG, "BLE scanning started")
                } catch (e: SecurityException) {
                    Log.e(TAG, "Security exception when starting BLE scan", e)
                } catch (e: IllegalStateException) {
                    Log.e(TAG, "Bluetooth adapter is not turned on or is not available", e)
                }
            } else {
                Log.e(TAG, "Missing permissions for BLE scanning")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start BLE scanning: ${e.message}")
        }
    }
    
    /**
     * Stop BLE scanning
     */
    fun stopScanning() {
        try {
            if (bleScanner == null) {
                Log.d(TAG, "BluetoothLeScanner is not available, no need to stop scanning")
                return
            }
            
            if (hasRequiredPermissions()) {
                try {
                    bleScanner?.stopScan(scanCallback)
                    Log.d(TAG, "BLE scanning stopped")
                } catch (e: SecurityException) {
                    Log.e(TAG, "Security exception when stopping BLE scan", e)
                } catch (e: IllegalStateException) {
                    Log.e(TAG, "Bluetooth adapter is not turned on or is not available", e)
                }
            } else {
                Log.e(TAG, "Missing permissions for BLE scanning")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping BLE scan: ${e.message}")
        }
    }
    
    /**
     * Process scan result and update beacons map
     */
    private fun processResult(result: ScanResult) {
        val device = result.device
        val rssi = result.rssi
        
        // Get the device address
        val deviceAddress = try {
            if (hasRequiredPermissions()) {
                device.address
            } else {
                "unknown_address"
            }
        } catch (e: SecurityException) {
            "unknown_address"
        }
        
        // Get the device name - don't skip unnamed devices, they could be beacons too
        val deviceName = try {
            if (hasRequiredPermissions()) {
                device.name ?: "Unknown-${deviceAddress.takeLast(4)}"
            } else {
                "Unknown-Device"
            }
        } catch (e: SecurityException) {
            // Handle permission denial
            "Unknown-Device"
        }
        
        // Calculate distance based on RSSI
        val distance = calculateDistance(rssi)
        
        // Create or update beacon object
        val beacon = Beacon(
            id = deviceAddress,
            name = deviceName,
            rssi = rssi,
            distance = distance,
            timestamp = System.currentTimeMillis()
        )
        
        // Log the detected beacon
        Log.d("BleManager", "Detected beacon: ${deviceName} | RSSI: ${rssi} | Distance: ${String.format("%.2f", distance)}m")
        
        // Update beacons map
        val currentBeacons = _detectedBeacons.value.toMutableMap()
        currentBeacons[deviceAddress] = beacon
        _detectedBeacons.value = currentBeacons
    }
    
    /**
     * Calculate distance from RSSI
     */
    private fun calculateDistance(rssi: Int): Double {
        val txPower = -59 // Calibrated signal power at 1 meter (typical value)
        val n = 2.0 // Path loss exponent (2.0 for free space)
        
        return 10.0.pow((txPower - rssi) / (10.0 * n))
    }
    
    /**
     * Check if we have the necessary permissions for BLE scanning
     */
    private fun hasRequiredPermissions(): Boolean {
        // Check location permission (required for BLE scanning)
        val hasLocationPermission = ActivityCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        
        // For Android S and above, we need additional BLE permissions
        val hasBluetoothPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_SCAN
            ) == PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH
            ) == PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_ADMIN
            ) == PackageManager.PERMISSION_GRANTED
        }
        
        return hasLocationPermission && hasBluetoothPermissions
    }
    
    /**
     * Set scan periods for battery optimization
     * 
     * @param scanDuration Duration of each scan in milliseconds (0 for continuous)
     * @param scanInterval Interval between scans in milliseconds
     */
    fun setScanPeriods(scanDuration: Long, scanInterval: Long) {
        // Update scan settings based on provided parameters
        val scanMode = when {
            scanInterval < 1500 -> ScanSettings.SCAN_MODE_LOW_LATENCY   // Highest power, fastest discovery
            scanInterval < 5000 -> ScanSettings.SCAN_MODE_BALANCED       // Medium power and latency
            else -> ScanSettings.SCAN_MODE_LOW_POWER                    // Lowest power, slower discovery
        }
        
        // Create new scan settings with updated mode
        scanSettings = ScanSettings.Builder()
            .setScanMode(scanMode)
            .setReportDelay(0) // Report results immediately
            .build()
        
        Log.d(TAG, "Updated scan settings: mode=$scanMode, duration=$scanDuration, interval=$scanInterval")
        
        // If duration is 0, scanning is continuous (handled by the BatteryOptimizedPositioningService)
    }
}