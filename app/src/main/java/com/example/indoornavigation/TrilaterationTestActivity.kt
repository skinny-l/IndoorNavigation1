package com.example.indoornavigation

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.MaterialTheme
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import com.example.indoornavigation.ui.TrilaterationTestScreen
import com.example.indoornavigation.viewmodel.RealBLEViewModel

class TrilaterationTestActivity : ComponentActivity() {
    
    private lateinit var viewModel: RealBLEViewModel
    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothManager.adapter
    }
    
    // Permission request launcher
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.all { it.value }) {
            // All permissions granted, check Bluetooth status
            checkBluetoothEnabled()
        } else {
            // Some permissions denied
            Toast.makeText(
                this,
                "Required permissions are needed for BLE scanning",
                Toast.LENGTH_SHORT
            ).show()
        }
    }
    
    // Bluetooth enable launcher
    private val bluetoothEnableLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            // Bluetooth is now enabled
            viewModel.startScanning()
        } else {
            // User refused to enable Bluetooth
            Toast.makeText(
                this,
                "Bluetooth is required for trilateration",
                Toast.LENGTH_SHORT
            ).show()
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize the ViewModel first
        viewModel = ViewModelProvider(this)[RealBLEViewModel::class.java]
        
        // Then check permissions
        checkAndRequestPermissions()
        
        setContent {
            MaterialTheme {
                TrilaterationTestScreen(viewModel)
            }
        }
    }
    
    private fun checkAndRequestPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.addAll(listOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT
            ))
        } else {
            permissions.addAll(listOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN
            ))
        }
        
        val permissionsToRequest = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()
        
        if (permissionsToRequest.isNotEmpty()) {
            requestPermissionLauncher.launch(permissionsToRequest)
        } else {
            // All permissions are already granted
            checkBluetoothEnabled()
        }
    }
    
    private fun checkBluetoothEnabled() {
        bluetoothAdapter?.let { adapter ->
            if (!adapter.isEnabled) {
                // Bluetooth is not enabled, prompt user to enable it
                if (ActivityCompat.checkSelfPermission(
                        this,
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                            Manifest.permission.BLUETOOTH_CONNECT
                        else
                            Manifest.permission.BLUETOOTH_ADMIN
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                    bluetoothEnableLauncher.launch(enableBtIntent)
                } else {
                    Toast.makeText(
                        this,
                        "Bluetooth permission denied",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } else {
                // Bluetooth is already enabled, start scanning
                viewModel.startScanning()
            }
        } ?: run {
            // Device doesn't support Bluetooth
            Toast.makeText(
                this,
                "This device doesn't support Bluetooth",
                Toast.LENGTH_SHORT
            ).show()
        }
    }
    
    override fun onResume() {
        super.onResume()
        // Resume scanning if we were scanning before and if Bluetooth is enabled
        if (viewModel.uiState.value.isScanning && bluetoothAdapter?.isEnabled == true) {
            viewModel.startScanning()
        }
    }
    
    override fun onPause() {
        super.onPause()
        // Stop scanning to save battery when not in foreground
        viewModel.stopScanning()
    }
}