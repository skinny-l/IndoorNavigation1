package com.example.indoornavigation

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.MenuItem
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.findNavController
import androidx.navigation.ui.setupWithNavController

import com.example.indoornavigation.databinding.ActivityMainBinding
import com.example.indoornavigation.outdoor.OutdoorNavigationManager
import com.example.indoornavigation.positioning.BuildingDetector
import com.example.indoornavigation.utils.SharedPreferencesHelper
import com.example.indoornavigation.viewmodel.LocationStatus
import com.example.indoornavigation.viewmodel.NavigationViewModel
import com.example.indoornavigation.viewmodel.PositioningViewModel
import com.google.android.material.navigation.NavigationView
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var positioningViewModel: PositioningViewModel? = null
    private lateinit var drawerToggle: ActionBarDrawerToggle
    private lateinit var navController: NavController
    private lateinit var sharedPreferencesHelper: SharedPreferencesHelper
    
    private var navigationViewModel: NavigationViewModel? = null
    private var buildingDetector: BuildingDetector? = null
    private var buildingDetectionJob: Job? = null
    private var buildingDetectionStarted = false
    private var isDetectionActive = false
    
    // Enhanced outdoor navigation
    private var outdoorNavigationManager: OutdoorNavigationManager? = null
    
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
    
    private val REQUIRED_PERMISSIONS = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_WIFI_STATE,
            Manifest.permission.CHANGE_WIFI_STATE,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
    } else {
        arrayOf(
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_WIFI_STATE,
            Manifest.permission.CHANGE_WIFI_STATE,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
    }

    companion object {
        private const val PERMISSION_REQUEST_CODE = 1001
        private const val BLUETOOTH_REQUEST_CODE = 1002
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        try {
            Log.d("MainActivity", "Starting onCreate")
            
            // Setup shared preferences first
            sharedPreferencesHelper = SharedPreferencesHelper(this)
            applyTheme(sharedPreferencesHelper.isDarkTheme())

            // Setup view binding
            binding = ActivityMainBinding.inflate(layoutInflater)
            setContentView(binding.root)
            
            // Hide default toolbar
            supportActionBar?.hide()
            
            // Initialize ViewModels with error handling
            initializeViewModels()
            
            // Initialize BuildingDetector with error handling
            initializeBuildingDetector()
            
            Log.d("MainActivity", "Basic setup complete, initializing navigation")
            
            // Post initialization to ensure view hierarchy is ready
            binding.root.post {
                initializeNavigation()
            }
            
        } catch (e: Exception) {
            Log.e("MainActivity", "Critical error in onCreate: ${e.message}")
            e.printStackTrace()
            
            // Show error and try minimal setup
            Toast.makeText(this, "Initializing app...", Toast.LENGTH_SHORT).show()
            
            try {
                // Minimal setup
                binding = ActivityMainBinding.inflate(layoutInflater)
                setContentView(binding.root)
                supportActionBar?.hide()
                sharedPreferencesHelper = SharedPreferencesHelper(this)
                setupBasicFunctionality()
            } catch (fallbackError: Exception) {
                Log.e("MainActivity", "Fallback failed: ${fallbackError.message}")
                // Show critical error message
                Toast.makeText(this, "App initialization failed. Please restart.", Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }
    
    private fun initializeViewModels() {
        try {
            positioningViewModel = ViewModelProvider(this)[PositioningViewModel::class.java]
            navigationViewModel = ViewModelProvider(this)[NavigationViewModel::class.java]
            Log.d("MainActivity", "ViewModels initialized successfully")
        } catch (e: Exception) {
            Log.e("MainActivity", "Error initializing ViewModels: ${e.message}")
            e.printStackTrace()
            // Continue without ViewModels - basic functionality will still work
        }
    }
    
    private fun initializeBuildingDetector() {
        try {
            buildingDetector = BuildingDetector(this)
            buildingDetector?.startWiFiScanning()
            
            // Initialize enhanced outdoor navigation
            outdoorNavigationManager = OutdoorNavigationManager(this)
            
            Log.d("MainActivity", "BuildingDetector and OutdoorNavigationManager initialized successfully")
        } catch (e: Exception) {
            Log.e("MainActivity", "Error initializing BuildingDetector: ${e.message}")
            e.printStackTrace()
            // Continue without BuildingDetector
        }
    }
    
    private fun initializeNavigation() {
        try {
            Log.d("MainActivity", "Initializing navigation")
            
            // Check if nav host fragment exists
            val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment)
            if (navHostFragment != null) {
                // Initialize NavController safely
                try {
                    navController = findNavController(R.id.nav_host_fragment)
                    Log.d("MainActivity", "NavController initialized successfully")
                } catch (e: Exception) {
                    Log.e("MainActivity", "Error finding NavController: ${e.message}")
                    // Continue without NavController - drawer will still work
                }
                
                // Setup custom drawer after NavController is initialized
                setupCustomDrawer()
                
                // Setup drawer toggle
                drawerToggle = object : ActionBarDrawerToggle(
                    this, binding.drawerLayout, R.string.navigation_drawer_open, R.string.navigation_drawer_close
                ) {}
                binding.drawerLayout.addDrawerListener(drawerToggle)
                drawerToggle.syncState()
                
                // Check and request permissions
                requestPermissions()
            } else {
                Log.w("MainActivity", "NavHostFragment not found, retrying...")
                // Retry after a short delay if fragment not ready
                binding.root.postDelayed({
                    initializeNavigation()
                }, 100)
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error initializing navigation: ${e.message}")
            e.printStackTrace()
            // Setup basic functionality without navigation
            setupBasicFunctionality()
        }
    }
    
    private fun setupBasicFunctionality() {
        // Setup basic drawer without navigation
        try {
            setupCustomDrawer()
            
            // Setup drawer toggle
            drawerToggle = object : ActionBarDrawerToggle(
                this, binding.drawerLayout, R.string.navigation_drawer_open, R.string.navigation_drawer_close
            ) {}
            binding.drawerLayout.addDrawerListener(drawerToggle)
            drawerToggle.syncState()
            
            // Request permissions
            requestPermissions()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Basic setup failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun setupCustomDrawer() {
        try {
            // Find drawer components directly from the included layout
            val aboutUsLayout = findViewById<LinearLayout>(R.id.tvAboutUs)
            aboutUsLayout?.setOnClickListener {
                // Navigate to About Us screen
                binding.drawerLayout.closeDrawer(GravityCompat.START)
                showToast("Navigating to About Us")
                
                // Delay navigation slightly to allow drawer to close
                aboutUsLayout.postDelayed({
                    navigateToSafely(R.id.aboutFragment)
                }, 300)
            }

            // Setup POI Management
            val poiManagementLayout = findViewById<LinearLayout>(R.id.tvPOIManagement)
            poiManagementLayout?.setOnClickListener {
                // Close drawer and show POI management
                binding.drawerLayout.closeDrawer(GravityCompat.START)
                showToast("Opening POI Management")
                
                // Delay to allow drawer to close, then trigger POI management dialog
                poiManagementLayout.postDelayed({
                    // Find the current fragment and call POI management if it's NewMapFragment
                    val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment)
                    val currentFragment = navHostFragment?.childFragmentManager?.fragments?.firstOrNull()
                    if (currentFragment is com.example.indoornavigation.ui.map.NewMapFragment) {
                        currentFragment.showPOIManagementDialog()
                    } else {
                        showToast("Please go to the map screen first")
                    }
                }, 300)
            }

            // Setup debug mode
            val debugModeLayout = findViewById<LinearLayout>(R.id.tvDebugMode)
            debugModeLayout?.setOnClickListener {
                // Navigate to Debug screen
                binding.drawerLayout.closeDrawer(GravityCompat.START)
                showToast("Navigating to Debug Mode")
                
                // Delay navigation slightly to allow drawer to close
                debugModeLayout.postDelayed({
                    navigateToSafely(R.id.debugFragment)
                }, 300)
            }
            
            // Add menu option for compose map
            debugModeLayout?.setOnLongClickListener {
                launchComposeMap()
                true
            }

            // Setup settings
            val settingsLayout = findViewById<LinearLayout>(R.id.tvSettings)
            settingsLayout?.setOnClickListener {
                // Navigate to Settings screen
                binding.drawerLayout.closeDrawer(GravityCompat.START)
                showToast("Navigating to Settings")
                
                // Delay navigation slightly to allow drawer to close
                settingsLayout.postDelayed({
                    navigateToSafely(R.id.settingsFragment)
                }, 300)
            }

            // Setup logout
            val logoutLayout = findViewById<LinearLayout>(R.id.tvLogout)
            logoutLayout?.setOnClickListener {
                // Handle logout
                binding.drawerLayout.closeDrawer(GravityCompat.START)
                showToast("Logging out")
                signOut()
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error setting up drawer: ${e.message}")
            e.printStackTrace()
        }
    }
    
    private fun navigateToSafely(destinationId: Int) {
        try {
            navController?.let { controller ->
                // Get current destination
                val currentDestinationId = controller.currentDestination?.id
                Log.d("MainActivity", "Navigating from ${getFragmentName(currentDestinationId)} to ${getFragmentName(destinationId)}")

                // Add navigation options to manage the back stack
                val navOptions = androidx.navigation.NavOptions.Builder()
                    .setPopUpTo(controller.graph.startDestinationId, false)
                    .build()

                // Try direct navigation first
                try {
                    controller.navigate(destinationId, null, navOptions)
                    return
                } catch (e: Exception) {
                    Log.w("MainActivity", "Direct navigation failed: ${e.message}")
                }

                // Handle specific navigation paths based on current destination
                when {
                    // From main map to other destinations
                    currentDestinationId == R.id.newMapFragment && destinationId == R.id.debugFragment -> {
                        try {
                            controller.navigate(R.id.action_newMapFragment_to_debugFragment)
                        } catch (e: Exception) {
                            Log.w("MainActivity", "Specific navigation action failed: ${e.message}")
                            showToast("Navigation failed, trying alternative method")
                        }
                    }
                    
                    currentDestinationId == R.id.newMapFragment && destinationId == R.id.settingsFragment -> {
                        try {
                            controller.navigate(R.id.action_newMapFragment_to_settingsFragment)
                        } catch (e: Exception) {
                            Log.w("MainActivity", "Settings navigation failed: ${e.message}")
                            showToast("Cannot navigate to Settings at this time")
                        }
                    }
                        
                    currentDestinationId == R.id.newMapFragment && destinationId == R.id.aboutFragment -> {
                        try {
                            controller.navigate(R.id.action_newMapFragment_to_aboutFragment)
                        } catch (e: Exception) {
                            Log.w("MainActivity", "About navigation failed: ${e.message}")
                            showToast("Cannot navigate to About at this time")
                        }
                    }

                    // Use general navigation for other cases
                    else -> {
                        val fallbackNavOptions = androidx.navigation.NavOptions.Builder()
                            .setPopUpTo(R.id.newMapFragment, false)
                            .build()
                            
                        try {
                            controller.navigate(destinationId, null, fallbackNavOptions)
                        } catch (e: Exception) {
                            Log.w("MainActivity", "Fallback navigation failed: ${e.message}")
                            showToast("Navigation unavailable")
                        }
                    }
                }
            } ?: run {
                Log.w("MainActivity", "Navigation controller not initialized")
                showToast("Navigation not ready yet")
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Navigation failed completely: ${e.message}")
            e.printStackTrace()
            showToast("Navigation error")
        }
    }
    
    private fun getFragmentName(id: Int?): String {
        return when (id) {
            R.id.newMapFragment -> "Map"
            R.id.debugFragment -> "Debug"
            R.id.settingsFragment -> "Settings"
            R.id.aboutFragment -> "About"
            null -> "Unknown"
            else -> "Fragment $id"
        }
    }

    private fun applyTheme(isDarkTheme: Boolean) {
        if (isDarkTheme) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        }
    }

    private fun signOut() {
        try {
            // Clear user data
            sharedPreferencesHelper.clearUserData()
            
            // Navigate to LoginActivity
            val intent = Intent(this, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        } catch (e: Exception) {
            Log.e("MainActivity", "Error signing out: ${e.message}")
            Toast.makeText(this, "Logout failed", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun requestPermissions() {
        try {
            val permissionsToRequest = REQUIRED_PERMISSIONS.filter {
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
        } catch (e: Exception) {
            Log.e("MainActivity", "Error requesting permissions: ${e.message}")
            e.printStackTrace()
        }
    }
    
    private fun checkBluetoothEnabled() {
        try {
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
                positioningViewModel?.startScanning()
                buildingDetector?.startGPSUpdates()
                setupBuildingDetection()
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error checking Bluetooth: ${e.message}")
            e.printStackTrace()
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
                positioningViewModel?.startScanning()
                buildingDetector?.startGPSUpdates()
                setupBuildingDetection()
            } else {
                showError("Bluetooth is required for indoor positioning")
            }
        }
    }
    
    override fun onBackPressed() {
        if (binding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
            binding.drawerLayout.closeDrawer(GravityCompat.START)
        } else {
            super.onBackPressed()
        }
    }
    
    override fun onCreateOptionsMenu(menu: android.view.Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_compose_map -> {
                // Launch Compose Map Activity
                launchComposeMap()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
    
    private fun showError(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT).show()
    }
    
    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
    
    /**
     * Add a shortcut to launch the Compose Map view from anywhere in the app
     */
    private fun launchComposeMap() {
        try {
            val intent = Intent(this, ComposeMapActivity::class.java)
            startActivity(intent)
        } catch (e: Exception) {
            Log.e("MainActivity", "Error launching compose map: ${e.message}")
            showToast("Cannot open compose map at this time")
        }
    }
    
    /**
     * Setup periodic building detection and observe location status changes
     */
    private fun setupBuildingDetection() {
        try {
            // Start periodic building detection
            if (buildingDetectionStarted) return
            buildingDetectionStarted = true
            isDetectionActive = true
            
            buildingDetectionJob?.cancel()
            buildingDetectionJob = lifecycleScope.launch {
                while (true) {
                    navigationViewModel?.let { navVM ->
                        buildingDetector?.updateAppState(navVM)
                    }
                    delay(10000) // Check every 10 seconds
                    if (!isDetectionActive) break
                }
            }
            
            // Add click listener for navigate to entrance button
            findViewById<View>(R.id.navigateToEntranceButton)?.setOnClickListener {
                // Get last known location for navigation 
                val lastLocation = buildingDetector?.getLastKnownLocation()
                
                showToast("Navigating to nearest building entrance")
                
                // Use the enhanced outdoor navigation manager
                outdoorNavigationManager?.nearestEntrance?.value?.let { entranceInfo ->
                    outdoorNavigationManager?.navigateToEntrance(entranceInfo.entrance)
                } ?: run {
                    // Fallback to building navigation if entrance info not available
                    outdoorNavigationManager?.navigateToBuilding()
                }
            }
            
            // Add click listener for open maps button
            findViewById<View>(R.id.openMapsButton)?.setOnClickListener {
                outdoorNavigationManager?.navigateToBuilding()
                showToast("Opening maps application")
            }
            
            // Observe location status changes
            lifecycleScope.launch {
                navigationViewModel?.locationStatus?.collectLatest { status: LocationStatus ->
                    when (status) {
                        LocationStatus.INSIDE_BUILDING -> {
                            // Show indoor navigation UI
                            binding.outsideBuildingBanner.visibility = View.GONE 
                            
                            // Show toast only when status changes, not on every update
                            if (previousStatus != LocationStatus.INSIDE_BUILDING) {
                                previousStatus = LocationStatus.INSIDE_BUILDING
                                showToast("You are inside the building")
                            }
                        }
                        LocationStatus.OUTSIDE_BUILDING -> {
                            // Show enhanced outdoor navigation UI
                            showEnhancedOutdoorUI()
                            
                            // Show toast only when status changes
                            if (previousStatus != LocationStatus.OUTSIDE_BUILDING) {
                                previousStatus = LocationStatus.OUTSIDE_BUILDING
                                showToast("You are outside the building - Enhanced navigation available")
                            }
                        }
                        else -> {
                            // Unknown state - hide outdoor UI
                            binding.outsideBuildingBanner.visibility = View.GONE
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error setting up building detection: ${e.message}")
            e.printStackTrace()
        }
    }
    
    // Track previous status to avoid repeated toasts
    private var previousStatus = LocationStatus.UNKNOWN
    
    private fun showEnhancedOutdoorUI() {
        binding.outsideBuildingBanner.visibility = View.VISIBLE
        
        // Update distance information if available
        outdoorNavigationManager?.currentLocation?.value?.let { location ->
            val distance = com.example.indoornavigation.utils.LocationUtils.calculateDistance(
                location.latitude, location.longitude,
                3.0706, 101.6068 // Building coordinates
            )
            findViewById<TextView>(R.id.distanceText)?.text = 
                "Distance to building: ${com.example.indoornavigation.utils.LocationUtils.formatDistance(distance)}"
        } ?: run {
            findViewById<TextView>(R.id.distanceText)?.text = "Getting your location..."
        }
        
        // Start outdoor navigation service
        outdoorNavigationManager?.startOutdoorNavigation()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        try {
            isDetectionActive = false
            buildingDetectionJob?.cancel()
            buildingDetector?.shutdown()
            outdoorNavigationManager?.stopOutdoorNavigation()
            positioningViewModel?.stopScanning()
        } catch (e: Exception) {
            Log.e("MainActivity", "Error in onDestroy: ${e.message}")
        }
    }
}
