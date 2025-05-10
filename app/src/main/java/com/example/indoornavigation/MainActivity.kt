package com.example.indoornavigation

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.MenuItem
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.NavController
import androidx.navigation.findNavController
import androidx.navigation.ui.setupWithNavController
import com.example.indoornavigation.databinding.ActivityMainBinding
import com.example.indoornavigation.viewmodel.PositioningViewModel
import com.google.android.material.navigation.NavigationView
import com.google.android.material.snackbar.Snackbar

class MainActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {

    private lateinit var binding: ActivityMainBinding
    private lateinit var positioningViewModel: PositioningViewModel
    private lateinit var drawerToggle: ActionBarDrawerToggle
    private lateinit var navController: NavController
    
    private val BLUETOOTH_PERMISSIONS = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
    } else {
        arrayOf(
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
    }
    
    companion object {
        private const val PERMISSION_REQUEST_CODE = 1001
        private const val BLUETOOTH_REQUEST_CODE = 1002
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Setup view binding
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Setup toolbar
        setSupportActionBar(binding.toolbar)
        
        // Setup drawer
        drawerToggle = ActionBarDrawerToggle(
            this, binding.drawerLayout, binding.toolbar,
            R.string.navigation_drawer_open, R.string.navigation_drawer_close
        )
        binding.drawerLayout.addDrawerListener(drawerToggle)
        drawerToggle.syncState()
        
        binding.navView.setNavigationItemSelectedListener(this)
        
        // Initialize ViewModel
        positioningViewModel = ViewModelProvider(this)[PositioningViewModel::class.java]
        
        // Check and request permissions
        requestPermissions()
        
        // Setup bottom navigation with NavController
        binding.bottomNavView.post {
            navController = findNavController(R.id.nav_host_fragment)
            binding.bottomNavView.setupWithNavController(navController)
        }
    }
    
    private fun requestPermissions() {
        val permissionsToRequest = BLUETOOTH_PERMISSIONS.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()
        
        if (permissionsToRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                this,
                permissionsToRequest,
                PERMISSION_REQUEST_CODE
            )
        } else {
            checkBluetoothEnabled()
        }
    }
    
    private fun checkBluetoothEnabled() {
        val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        if (bluetoothAdapter == null) {
            // Device doesn't support Bluetooth
            showError("This device doesn't support Bluetooth")
            return
        }
        
        if (!bluetoothAdapter.isEnabled) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                startActivityForResult(enableBtIntent, BLUETOOTH_REQUEST_CODE)
            }
        } else {
            // Bluetooth is enabled, start BLE scanning
            positioningViewModel.startScanning()
        }
    }
    
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                checkBluetoothEnabled()
            } else {
                showError("Required permissions are not granted")
            }
        }
    }
    
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        if (requestCode == BLUETOOTH_REQUEST_CODE) {
            if (resultCode == RESULT_OK) {
                // Bluetooth is now enabled, start BLE scanning
                positioningViewModel.startScanning()
            } else {
                showError("Bluetooth is required for indoor positioning")
            }
        }
    }
    
    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        if (!this::navController.isInitialized) {
            showError("Navigation not ready yet")
            return true
        }
        
        try {
            when (item.itemId) {
                R.id.nav_map -> {
                    navController.navigate(R.id.mapFragment)
                }
                R.id.nav_debug -> {
                    navController.navigate(R.id.debugFragment)
                }
                R.id.nav_settings -> {
                    navController.navigate(R.id.settingsFragment)
                }
            }
        } catch (e: Exception) {
            // Handle navigation error
            showError("Error navigating to selected item: ${e.message}")
        }
        
        binding.drawerLayout.closeDrawer(GravityCompat.START)
        return true
    }
    
    override fun onBackPressed() {
        if (binding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
            binding.drawerLayout.closeDrawer(GravityCompat.START)
        } else {
            super.onBackPressed()
        }
    }
    
    private fun showError(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT).show()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        positioningViewModel.stopScanning()
    }
}