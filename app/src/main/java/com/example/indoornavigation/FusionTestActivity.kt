package com.example.indoornavigation

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.ui.Modifier
import androidx.core.app.ActivityCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.indoornavigation.ui.FusionTestScreen
import com.example.indoornavigation.ui.theme.IndoorNavigationTheme
import com.example.indoornavigation.viewmodel.FusionTestViewModel

class FusionTestActivity : ComponentActivity() {
    private val bluetoothManager by lazy {
        getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    }
    
    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        bluetoothManager.adapter
    }
    
    // Request Bluetooth permissions using the new ActivityResult API
    private val requestBluetooth = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { /* Optional: handle result */ }
    
    // Request location permissions
    private val requestLocationPermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        // Check if all permissions are granted
        val allGranted = permissions.entries.all { it.value }
        
        if (allGranted) {
            // All permissions granted, now check Bluetooth
            checkBluetoothEnabled()
        } else {
            // Show a message that the app requires these permissions
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Request permissions before setting content
        requestRequiredPermissions()
        
        setContent {
            IndoorNavigationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colors.background
                ) {
                    // Create viewmodel with application context to enable real BLE scanning
                    val fusionViewModel: FusionTestViewModel = viewModel(
                        factory = FusionTestViewModel.Factory(application)
                    )
                    
                    FusionTestScreen(viewModel = fusionViewModel)
                }
            }
        }
    }
    
    /**
     * Check and request all required permissions for BLE scanning
     */
    private fun requestRequiredPermissions() {
        // For Android 12 (API 31) and above, we need BLUETOOTH_SCAN and BLUETOOTH_CONNECT
        // For older versions, ACCESS_FINE_LOCATION is required for BLE scanning
        val requiredPermissions = mutableListOf<String>().apply {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(Manifest.permission.BLUETOOTH_SCAN)
                add(Manifest.permission.BLUETOOTH_CONNECT)
            } else {
                add(Manifest.permission.BLUETOOTH)
                add(Manifest.permission.BLUETOOTH_ADMIN)
            }
        }
        
        // Filter only permissions that are not granted
        val permissionsToRequest = requiredPermissions.filter {
            ActivityCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        
        // Request permissions if any are not granted
        if (permissionsToRequest.isNotEmpty()) {
            requestLocationPermissions.launch(permissionsToRequest.toTypedArray())
        } else {
            // All permissions already granted, check Bluetooth
            checkBluetoothEnabled()
        }
    }
    
    /**
     * Check if Bluetooth is enabled and prompt user if it's not
     */
    private fun checkBluetoothEnabled() {
        bluetoothAdapter?.let { adapter ->
            if (!adapter.isEnabled) {
                // Bluetooth is not enabled, request to enable it
                val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (ActivityCompat.checkSelfPermission(
                            this,
                            Manifest.permission.BLUETOOTH_CONNECT
                        ) == PackageManager.PERMISSION_GRANTED
                    ) {
                        requestBluetooth.launch(enableBtIntent)
                    }
                } else {
                    requestBluetooth.launch(enableBtIntent)
                }
            }
        }
    }
    
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        // This is called when permissions are requested using the old API
        // We're using the new ActivityResult API so we don't handle anything here
    }
}