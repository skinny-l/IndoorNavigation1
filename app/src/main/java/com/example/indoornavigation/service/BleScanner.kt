package com.example.indoornavigation.service

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.app.ActivityCompat
import com.example.indoornavigation.data.models.ManagedBeacon
import com.example.indoornavigation.data.repository.BeaconRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Service for scanning BLE beacons and matching with registered beacons
 */
class BleScanner(
    private val context: Context,
    private val beaconRepository: BeaconRepository,
    private val trilaterationService: TrilaterationService
) {
    private val TAG = "BleScanner"
    
    // Bluetooth components
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter
    private var bluetoothLeScanner: BluetoothLeScanner? = null
    
    // Scanning state
    private var scanning = false
    private val handler = Handler(Looper.getMainLooper())
    private val SCAN_PERIOD = 5000L // Scan for 5 seconds at a time
    private val SCAN_INTERVAL = 2000L // Wait 2 seconds between scans
    
    // Latest scan results
    private val _scanResults = MutableStateFlow<Map<String, ScanResult>>(emptyMap())
    val scanResults: StateFlow<Map<String, ScanResult>> = _scanResults.asStateFlow()
    
    /**
     * Check if Bluetooth is available and enabled
     */
    fun isBluetoothEnabled(): Boolean {
        return bluetoothAdapter?.isEnabled == true
    }
    
    /**
     * Check if required permissions are granted
     */
    fun hasRequiredPermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_SCAN
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH
            ) == PackageManager.PERMISSION_GRANTED
        }
    }
    
    /**
     * Start periodic BLE scanning
     */
    fun startScanning() {
        if (!isBluetoothEnabled()) {
            Log.e(TAG, "Bluetooth is not enabled")
            return
        }
        
        if (!hasRequiredPermissions()) {
            Log.e(TAG, "Bluetooth scan permissions not granted")
            return
        }
        
        if (scanning) {
            Log.d(TAG, "Already scanning")
            return
        }
        
        bluetoothLeScanner = bluetoothAdapter?.bluetoothLeScanner
        if (bluetoothLeScanner == null) {
            Log.e(TAG, "BluetoothLeScanner is null")
            return
        }
        
        // Start periodic scanning
        handlePeriodicScan()
    }
    
    /**
     * Handle periodic scan cycle
     */
    private fun handlePeriodicScan() {
        // Start a scan
        startSingleScan()
        
        // Schedule scan stop after SCAN_PERIOD
        handler.postDelayed({
            stopScan()
            
            // Schedule next scan after SCAN_INTERVAL
            handler.postDelayed({
                // Continue scanning cycle if still enabled
                if (scanning) {
                    handlePeriodicScan()
                }
            }, SCAN_INTERVAL)
        }, SCAN_PERIOD)
    }
    
    /**
     * Start a single scan operation
     */
    private fun startSingleScan() {
        if (bluetoothLeScanner == null || !hasRequiredPermissions()) {
            return
        }
        
        // Setup scan settings for low latency
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        
        // Apply filters if needed
        val filters = mutableListOf<ScanFilter>()
        
        try {
            scanning = true
            if (hasRequiredPermissions()) {
                try {
                    bluetoothLeScanner?.startScan(filters, settings, leScanCallback)
                } catch (securityException: SecurityException) {
                    Log.e(TAG, "Missing permission for BLE scan", securityException)
                }
            }
            Log.d(TAG, "BLE scan started")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start scan", e)
        }
    }
    
    /**
     * Stop BLE scanning
     */
    fun stopScanning() {
        if (!scanning || bluetoothLeScanner == null || !hasRequiredPermissions()) {
            return
        }
        
        stopScan()
        
        // Also remove any pending scan operations
        handler.removeCallbacksAndMessages(null)
    }
    
    /**
     * Stop the current scan operation
     */
    private fun stopScan() {
        if (!hasRequiredPermissions()) return
        
        try {
            scanning = false
            if (hasRequiredPermissions()) {
                try {
                    bluetoothLeScanner?.stopScan(leScanCallback)
                } catch (securityException: SecurityException) {
                    Log.e(TAG, "Missing permission for stopping BLE scan", securityException)
                }
            }
            Log.d(TAG, "BLE scan stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop scan", e)
        }
    }
    
    /**
     * BLE scan callback
     */
    private val leScanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            processScanResult(result)
        }
        
        override fun onBatchScanResults(results: List<ScanResult>) {
            results.forEach { processScanResult(it) }
        }
        
        override fun onScanFailed(errorCode: Int) {
            Log.e(TAG, "BLE scan failed with error code: $errorCode")
        }
    }
    
    /**
     * Process BLE scan result
     */
    private fun processScanResult(result: ScanResult) {
        val device = result.device
        val address = device.address
        
        // Add to scan results
        val currentResults = _scanResults.value.toMutableMap()
        currentResults[address] = result
        _scanResults.value = currentResults
        
        // Match with registered beacons
        matchWithManagedBeacons(result)
    }
    
    /**
     * Match scan result with registered beacons
     */
    private fun matchWithManagedBeacons(result: ScanResult) {
        val address = result.device.address
        val name = try {
            result.device.name ?: "Unknown"
        } catch (securityException: SecurityException) {
            Log.e(TAG, "Missing permission to access device name", securityException)
            "Unknown"
        }
        val rssi = result.rssi
        
        // Try to find matching beacon by UUID/MAC
        val managedBeacons = beaconRepository.beacons.value
        val matchingBeacon = managedBeacons.find { 
            it.uuid.equals(address, ignoreCase = true) || 
            (it.name.isNotEmpty() && it.name.equals(name, ignoreCase = true))
        }
        
        if (matchingBeacon != null) {
            // Calculate distance using RSSI
            val txPower = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                result.txPower
            } else {
                -59 // Default value if txPower not available
            }
            
            val distance = trilaterationService.rssiToDistance(rssi, txPower)
            
            // Update beacon with new signal data
            beaconRepository.updateBeaconSignal(matchingBeacon.id, rssi, distance)
        }
    }
}