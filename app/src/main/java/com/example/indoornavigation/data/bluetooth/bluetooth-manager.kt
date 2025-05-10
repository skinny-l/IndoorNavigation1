package com.example.indoornavigation.data.bluetooth

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
import com.example.indoornavigation.data.models.Beacon
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manager class for handling Bluetooth Low Energy scanning and beacon detection
 */
class BleManager(private val context: Context) {

    private val TAG = "BleManager"
    
    // Bluetooth components
    private val bluetoothManager: BluetoothManager by lazy {
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    }
    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        bluetoothManager.adapter
    }
    private var bluetoothLeScanner: BluetoothLeScanner? = null
    
    // Scanning variables
    private var scanning = false
    private val handler = Handler(Looper.getMainLooper())
    private val SCAN_PERIOD: Long = 10000 // 10 seconds scan period
    
    // Beacon data
    private val _detectedBeacons = MutableStateFlow<Map<String, Beacon>>(emptyMap())
    val detectedBeacons: StateFlow<Map<String, Beacon>> = _detectedBeacons.asStateFlow()
    
    // Beacon cache to store recent readings
    private val beaconCache = mutableMapOf<String, Beacon>()
    
    /**
     * Check if BLE is supported and enabled
     */
    fun isBluetoothEnabled(): Boolean {
        return bluetoothAdapter?.isEnabled == true
    }
    
    /**
     * Start scanning for BLE beacons
     */
    fun startScanning() {
        if (bluetoothAdapter == null || !isBluetoothEnabled()) {
            Log.e(TAG, "Bluetooth is not enabled or not supported")
            return
        }
        
        if (ActivityCompat.checkSelfPermission(
                context,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Manifest.permission.BLUETOOTH_SCAN
                else Manifest.permission.BLUETOOTH
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e(TAG, "Bluetooth scan permission not granted")
            return
        }
        
        if (scanning) {
            Log.d(TAG, "Already scanning")
            return
        }
        
        bluetoothLeScanner = bluetoothAdapter?.bluetoothLeScanner
        
        // Setup scan settings for low latency
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        
        // Apply filters if needed
        val filters = mutableListOf<ScanFilter>()
        // Example filter for specific beacons if needed:
        // val filter = ScanFilter.Builder()
        //    .setServiceUuid(ParcelUuid(UUID.fromString("0000feaa-0000-1000-8000-00805f9b34fb")))
        //    .build()
        // filters.add(filter)
        
        // Start scanning with timeout
        handler.postDelayed({
            if (scanning) {
                scanning = false
                bluetoothLeScanner?.stopScan(leScanCallback)
                Log.d(TAG, "BLE scan stopped after timeout")
                // Restart scanning after a brief pause
                handler.postDelayed({ startScanning() }, 1000)
            }
        }, SCAN_PERIOD)
        
        scanning = true
        bluetoothLeScanner?.startScan(filters, settings, leScanCallback)
        Log.d(TAG, "BLE scan started")
    }
    
    /**
     * Stop BLE scanning
     */
    fun stopScanning() {
        if (!scanning) return
        
        if (ActivityCompat.checkSelfPermission(
                context,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Manifest.permission.BLUETOOTH_SCAN
                else Manifest.permission.BLUETOOTH
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        
        scanning = false
        bluetoothLeScanner?.stopScan(leScanCallback)
        Log.d(TAG, "BLE scan stopped")
    }
    
    /**
     * Callback for BLE scan results
     */
    private val leScanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            super.onScanResult(callbackType, result)
            processResult(result)
        }
        
        override fun onBatchScanResults(results: MutableList<ScanResult>) {
            super.onBatchScanResults(results)
            for (result in results) {
                processResult(result)
            }
        }
        
        override fun onScanFailed(errorCode: Int) {
            super.onScanFailed(errorCode)
            Log.e(TAG, "BLE scan failed with error code: $errorCode")
        }
    }
    
    /**
     * Process scan results and extract beacon information
     */
    private fun processResult(result: ScanResult) {
        val device = result.device
        val rssi = result.rssi
        val txPower = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            result.txPower
        } else {
            -59 // Default value if txPower is not available
        }
        
        // Calculate approximate distance using RSSI
        val distance = calculateDistance(rssi, txPower)
        
        // Create beacon object
        val address = device.address
        val name = device.name ?: "Unknown"
        
        val beacon = Beacon(
            id = address,
            name = name,
            rssi = rssi,
            distance = distance,
            timestamp = System.currentTimeMillis()
        )
        
        // Update beacon cache
        beaconCache[address] = beacon
        
        // Update StateFlow with the latest beacon information
        _detectedBeacons.value = HashMap(beaconCache)
    }
    
    /**
     * Calculate approximate distance using RSSI and txPower
     * using the log-distance path loss model
     */
    private fun calculateDistance(rssi: Int, txPower: Int): Double {
        if (rssi == 0 || txPower == Int.MIN_VALUE) {
            return -1.0 // Can't determine distance
        }
        
        // Path loss exponent (environment factor)
        val n = 2.0 // Free space = 2, indoors = 1.6 to 3.0 depending on obstacles
        
        // Calculate distance using log-distance path loss model
        // d = 10^((TxPower - RSSI)/(10 * n))
        return Math.pow(10.0, (txPower - rssi) / (10.0 * n))
    }
}