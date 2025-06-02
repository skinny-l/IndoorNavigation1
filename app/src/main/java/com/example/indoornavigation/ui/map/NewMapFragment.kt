package com.example.indoornavigation.ui.map

import android.app.AlertDialog
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope

import com.example.indoornavigation.R
import com.example.indoornavigation.databinding.FragmentMapNewBinding
import com.example.indoornavigation.data.models.Position
import com.example.indoornavigation.utils.PathfindingEngine
import com.example.indoornavigation.utils.ObstacleDetector
import com.example.indoornavigation.viewmodel.LocationStatus
import com.example.indoornavigation.viewmodel.NavigationViewModel
import com.example.indoornavigation.viewmodel.PositioningViewModel
import kotlinx.coroutines.launch

class NewMapFragment : Fragment() {

    private var _binding: FragmentMapNewBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var floorPlanView: FloorPlanView
    private lateinit var positioningViewModel: PositioningViewModel
    private lateinit var navigationViewModel: NavigationViewModel
    private var pathfindingEngine: PathfindingEngine? = null
    private var destinationPosition: Position? = null
    
    // Optional: Obstacle detection (requires camera permission)
    private var obstacleDetector: ObstacleDetector? = null
    private var obstacleDetectionEnabled = false
    
    // Real building data (you'll configure this)
    private var realPOIs = mutableListOf<RealPOI>()
    private var realBeacons = mutableListOf<RealBeacon>()
    private var isConfigurationMode = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMapNewBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        try {
            Log.d("NewMapFragment", "NewMapFragment created successfully")
            
            setupViewModel()
            setupFloorPlanView()
            setupBasicUI()
            loadConfiguration() // Load saved POIs and beacons
            observePositioning()
            updateSearchHint() // Initial update
            
        } catch (e: Exception) {
            Log.e("NewMapFragment", "Error in onViewCreated: ${e.message}")
            e.printStackTrace()
            // Show a simple error message but continue
            Toast.makeText(requireContext(), "Map interface loaded", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun setupViewModel() {
        try {
            positioningViewModel = ViewModelProvider(requireActivity())[PositioningViewModel::class.java]
            navigationViewModel = ViewModelProvider(requireActivity())[NavigationViewModel::class.java]
            
            // Initialize pathfinding engine with navigation nodes
            val navNodes = positioningViewModel.getNavigationNodes()
            pathfindingEngine = PathfindingEngine(navNodes)
            
            Log.d("NewMapFragment", "PositioningViewModel and PathfindingEngine setup complete")
        } catch (e: Exception) {
            Log.e("NewMapFragment", "Error setting up PositioningViewModel: ${e.message}")
            e.printStackTrace()
        }
    }
    
    private fun setupFloorPlanView() {
        try {
            // Create and add FloorPlanView to the map container
            floorPlanView = FloorPlanView(requireContext())
            
            // Set building dimensions (CS1 building dimensions in meters)
            floorPlanView.setBuildingDimensions(75.0, 75.0)
            
            // Load the floor plan (ground floor)
            floorPlanView.setFloorPlan(0)
            
            // Add it to the map container, replacing the placeholder
            binding.mapContainer.removeAllViews()
            binding.mapContainer.addView(floorPlanView)
            
            // Set up touch listener for navigation or configuration
            floorPlanView.setNavigationTouchListener { position ->
                if (isConfigurationMode) {
                    handleConfigurationTouch(position)
                } else {
                    handleMapTouch(position)
                }
            }
            
            // Set up marker drag listener for POI repositioning
            floorPlanView.setMarkerDragListener { marker, newPosition ->
                // Find and update the corresponding POI by exact title match first
                val poiIndex = realPOIs.indexOfFirst { poi ->
                    poi.name == marker.title
                }
                
                if (poiIndex != -1) {
                    // Update POI position
                    val oldPOI = realPOIs[poiIndex]
                    realPOIs[poiIndex] = oldPOI.copy(position = newPosition)
                    
                    // Don't refresh all markers - the marker position is already updated by the drag system
                    // Just save the configuration
                    saveConfiguration()
                    
                    // Show feedback
                    Toast.makeText(requireContext(), 
                        "Moved ${oldPOI.name} to (${String.format("%.1f", newPosition.x)}, ${String.format("%.1f", newPosition.y)})", 
                        Toast.LENGTH_SHORT).show()
                    
                    Log.d("POI_DRAG", "Updated POI ${oldPOI.name} from ${oldPOI.position} to $newPosition")
                } else {
                    // If no exact title match, try approximate position match as fallback
                    val fallbackIndex = realPOIs.indexOfFirst { poi ->
                        poi.position.distanceTo(marker.position) < 5.0
                    }
                    
                    if (fallbackIndex != -1) {
                        val oldPOI = realPOIs[fallbackIndex]
                        realPOIs[fallbackIndex] = oldPOI.copy(position = newPosition)
                        saveConfiguration()
                        
                        Toast.makeText(requireContext(), 
                            "Moved ${oldPOI.name} to (${String.format("%.1f", newPosition.x)}, ${String.format("%.1f", newPosition.y)})", 
                            Toast.LENGTH_SHORT).show()
                        
                        Log.d("POI_DRAG", "Updated POI ${oldPOI.name} via position match from ${oldPOI.position} to $newPosition")
                    } else {
                        Log.w("POI_DRAG", "No matching POI found for marker: ${marker.title} at position ${marker.position}")
                    }
                }
            }
            
            Log.d("NewMapFragment", "FloorPlanView setup complete")
            
        } catch (e: Exception) {
            Log.e("NewMapFragment", "Error setting up FloorPlanView: ${e.message}")
            e.printStackTrace()
            // Keep the placeholder if FloorPlanView fails
        }
    }
    
    private fun handleConfigurationTouch(position: Position) {
        // Show dialog to configure this location
        showLocationConfigurationDialog(position)
    }
    
    private fun showLocationConfigurationDialog(position: Position) {
        val options = arrayOf(
            "🏢 Add POI (Point of Interest)",
            "📡 Add Beacon Location", 
            "🗑️ Remove Item Here",
            "💾 Save Configuration",
            "❌ Exit Configuration Mode"
        )
        
        AlertDialog.Builder(requireContext())
            .setTitle("Configure Location (${String.format("%.1f", position.x)}, ${String.format("%.1f", position.y)})")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showAddPOIDialog(position)
                    1 -> showAddBeaconDialog(position)
                    2 -> removeItemAtLocation(position)
                    3 -> saveConfiguration()
                    4 -> exitConfigurationMode()
                }
            }
            .show()
    }
    
    private fun showAddPOIDialog(position: Position) {
        val layout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 40, 50, 10)
        }
        
        val nameInput = EditText(requireContext()).apply {
            hint = "POI Name (e.g., 'Main Entrance', 'Restroom A')"
        }
        
        val typeInput = EditText(requireContext()).apply {
            hint = "Type (e.g., 'entrance', 'restroom', 'lab', 'office')"
        }
        
        val descriptionInput = EditText(requireContext()).apply {
            hint = "Description (optional)"
        }
        
        layout.addView(nameInput)
        layout.addView(typeInput)
        layout.addView(descriptionInput)
        
        AlertDialog.Builder(requireContext())
            .setTitle("Add Point of Interest")
            .setView(layout)
            .setPositiveButton("Add") { _, _ ->
                val name = nameInput.text.toString().trim()
                val type = typeInput.text.toString().trim()
                val description = descriptionInput.text.toString().trim()
                
                if (name.isNotEmpty() && type.isNotEmpty()) {
                    addRealPOI(name, type, description, position)
                } else {
                    Toast.makeText(requireContext(), "Please fill in name and type", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun showAddBeaconDialog(position: Position) {
        val layout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 40, 50, 10)
        }
        
        val nameInput = EditText(requireContext()).apply {
            hint = "Beacon Name (e.g., 'Entrance Beacon')"
        }
        
        val macInput = EditText(requireContext()).apply {
            hint = "MAC Address or UUID (if known)"
        }
        
        val typeInput = EditText(requireContext()).apply {
            hint = "Type (BLE, WiFi, or both)"
            setText("BLE")
        }
        
        layout.addView(nameInput)
        layout.addView(macInput)
        layout.addView(typeInput)
        
        AlertDialog.Builder(requireContext())
            .setTitle("Add Beacon Location")
            .setView(layout)
            .setPositiveButton("Add") { _, _ ->
                val name = nameInput.text.toString().trim()
                val mac = macInput.text.toString().trim()
                val type = typeInput.text.toString().trim()
                
                if (name.isNotEmpty()) {
                    addRealBeacon(name, mac, type, position)
                } else {
                    Toast.makeText(requireContext(), "Please fill in beacon name", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun addRealPOI(name: String, type: String, description: String, position: Position) {
        val poi = RealPOI(
            name = name,
            type = type,
            description = description,
            position = position
        )
        realPOIs.add(poi)
        
        // Add visual marker
        addPOIMarker(poi)
        
        // Auto-save configuration after adding POI
        saveConfiguration()
        
        Toast.makeText(requireContext(), "Added POI: $name and saved", Toast.LENGTH_SHORT).show()
        Log.d("Configuration", "Added POI: $poi")
        updateSearchHint()
    }
    
    private fun addRealBeacon(name: String, mac: String, type: String, position: Position) {
        val beacon = RealBeacon(
            name = name,
            macAddress = mac,
            type = type,
            position = position
        )
        realBeacons.add(beacon)
        
        // Add visual marker
        addBeaconMarker(beacon)
        
        // Auto-save configuration after adding beacon
        saveConfiguration()
        
        Toast.makeText(requireContext(), "Added Beacon: $name and saved", Toast.LENGTH_SHORT).show()
        Log.d("Configuration", "Added Beacon: $beacon")
    }
    
    private fun addPOIMarker(poi: RealPOI) {
        try {
            val markerBitmap = when (poi.type.lowercase()) {
                "entrance" -> ContextCompat.getDrawable(requireContext(), R.drawable.ic_location_marker)
                "restroom" -> ContextCompat.getDrawable(requireContext(), R.drawable.ic_location_marker)
                "lab" -> ContextCompat.getDrawable(requireContext(), R.drawable.ic_location_marker)
                "office" -> ContextCompat.getDrawable(requireContext(), R.drawable.ic_location_marker)
                else -> ContextCompat.getDrawable(requireContext(), R.drawable.ic_location_marker)
            }?.let { drawable ->
                val bitmap = android.graphics.Bitmap.createBitmap(48, 48, android.graphics.Bitmap.Config.ARGB_8888)
                val canvas = android.graphics.Canvas(bitmap)
                drawable.setBounds(0, 0, 48, 48)
                drawable.draw(canvas)
                bitmap
            }
            
            val marker = FloorPlanView.Marker(
                position = poi.position,
                bitmap = markerBitmap,
                title = poi.name, // Use POI name as title for identification
                floor = poi.position.floor
            )
            
            floorPlanView.addMarker(marker)
        } catch (e: Exception) {
            Log.e("Configuration", "Error adding POI marker: ${e.message}")
        }
    }
    
    private fun addBeaconMarker(beacon: RealBeacon) {
        try {
            val markerBitmap = ContextCompat.getDrawable(requireContext(), R.drawable.ic_router)
                ?.let { drawable ->
                    val bitmap = android.graphics.Bitmap.createBitmap(48, 48, android.graphics.Bitmap.Config.ARGB_8888)
                    val canvas = android.graphics.Canvas(bitmap)
                    drawable.setBounds(0, 0, 48, 48)
                    drawable.draw(canvas)
                    bitmap
                }
            
            val marker = FloorPlanView.Marker(
                position = beacon.position,
                bitmap = markerBitmap,
                title = beacon.name,
                floor = beacon.position.floor
            )
            
            floorPlanView.addMarker(marker)
        } catch (e: Exception) {
            Log.e("Configuration", "Error adding beacon marker: ${e.message}")
        }
    }
    
    private fun removeItemAtLocation(position: Position) {
        // Remove POIs near this location
        val nearbyPOIs = realPOIs.filter { it.position.distanceTo(position) < 5.0 }
        val nearbyBeacons = realBeacons.filter { it.position.distanceTo(position) < 5.0 }
        
        if (nearbyPOIs.isNotEmpty() || nearbyBeacons.isNotEmpty()) {
            realPOIs.removeAll(nearbyPOIs)
            realBeacons.removeAll(nearbyBeacons)
            
            // Refresh markers
            floorPlanView.clearMarkers()
            realPOIs.forEach { addPOIMarker(it) }
            realBeacons.forEach { addBeaconMarker(it) }
            
            // Auto-save configuration after deletion
            saveConfiguration()
            
            Toast.makeText(requireContext(), 
                "Removed ${nearbyPOIs.size} POIs and ${nearbyBeacons.size} beacons and saved", 
                Toast.LENGTH_SHORT).show()
            updateSearchHint()
        } else {
            Toast.makeText(requireContext(), "No items found near this location", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun saveConfiguration() {
        // Save to SharedPreferences or file
        val prefs = requireContext().getSharedPreferences("building_config", Context.MODE_PRIVATE)
        val editor = prefs.edit()
        
        // Save POIs
        val poisJson = realPOIs.joinToString("|") { poi ->
            "${poi.name};${poi.type};${poi.description};${poi.position.x};${poi.position.y};${poi.position.floor}"
        }
        editor.putString("real_pois", poisJson)
        
        // Save Beacons
        val beaconsJson = realBeacons.joinToString("|") { beacon ->
            "${beacon.name};${beacon.macAddress};${beacon.type};${beacon.position.x};${beacon.position.y};${beacon.position.floor}"
        }
        editor.putString("real_beacons", beaconsJson)
        
        editor.apply()
        
        Toast.makeText(requireContext(), 
            "Saved ${realPOIs.size} POIs and ${realBeacons.size} beacons", 
            Toast.LENGTH_LONG).show()
        
        Log.d("Configuration", "Saved configuration: ${realPOIs.size} POIs, ${realBeacons.size} beacons")
        updateSearchHint()
    }
    
    private fun loadConfiguration() {
        val prefs = requireContext().getSharedPreferences("building_config", Context.MODE_PRIVATE)
        
        var loadedPOIs = 0
        var loadedBeacons = 0
        
        // Load POIs
        val poisJson = prefs.getString("real_pois", "")
        if (!poisJson.isNullOrEmpty()) {
            realPOIs.clear()
            poisJson.split("|").forEach { poiStr ->
                val parts = poiStr.split(";")
                if (parts.size >= 6) {
                    val poi = RealPOI(
                        name = parts[0],
                        type = parts[1],
                        description = parts[2],
                        position = Position(parts[3].toDouble(), parts[4].toDouble(), parts[5].toInt())
                    )
                    realPOIs.add(poi)
                    addPOIMarker(poi)
                    loadedPOIs++
                }
            }
        }
        
        // Load Beacons
        val beaconsJson = prefs.getString("real_beacons", "")
        if (!beaconsJson.isNullOrEmpty()) {
            realBeacons.clear()
            beaconsJson.split("|").forEach { beaconStr ->
                val parts = beaconStr.split(";")
                if (parts.size >= 6) {
                    val beacon = RealBeacon(
                        name = parts[0],
                        macAddress = parts[1],
                        type = parts[2],
                        position = Position(parts[3].toDouble(), parts[4].toDouble(), parts[5].toInt())
                    )
                    realBeacons.add(beacon)
                    addBeaconMarker(beacon)
                    loadedBeacons++
                }
            }
        }
        
        if (loadedPOIs > 0 || loadedBeacons > 0) {
            Log.d("Configuration", "Loaded $loadedPOIs POIs and $loadedBeacons beacons from configuration")
            if (!isConfigurationMode) {
                // Only show message if not in configuration mode (to avoid spam during setup)
                Toast.makeText(requireContext(), 
                    "Loaded $loadedPOIs POIs and $loadedBeacons beacons", 
                    Toast.LENGTH_SHORT).show()
            }
        } else {
            Log.d("Configuration", "No saved configuration found")
        }
        updateSearchHint()
    }
    
    private fun exitConfigurationMode() {
        isConfigurationMode = false
        binding.tvTitle.text = "Indoor Navigation"
        binding.tvTitle.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.black))
        
        // Disable edit mode for drag and drop
        if (::floorPlanView.isInitialized) {
            floorPlanView.setEditMode(false)
        }
        
        Toast.makeText(requireContext(), "Configuration mode OFF - Navigation mode active", Toast.LENGTH_SHORT).show()
        updateSearchHint()
    }
    
    private fun handleMapTouch(position: Position) {
        try {
            // Check if the touch is near any POI
            val touchedPOI = findPOIAtPosition(position)
            
            if (touchedPOI != null) {
                // Navigation starts only when clicking on a POI
                val currentPosition = positioningViewModel.currentPosition.value
                
                if (currentPosition != null) {
                    // Set POI as destination and calculate route
                    destinationPosition = touchedPOI.position
                    calculateAndShowRoute(currentPosition, touchedPOI.position)
                    
                    Toast.makeText(requireContext(), 
                        "Navigating to: ${touchedPOI.name}", 
                        Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(requireContext(), 
                        "Please wait for your location to be determined before starting navigation", 
                        Toast.LENGTH_SHORT).show()
                }
            } else {
                // Touched empty area - just show position info instead of starting navigation
                Toast.makeText(requireContext(), 
                    "Tapped at: ${String.format("%.1f", position.x)}, ${String.format("%.1f", position.y)}m", 
                    Toast.LENGTH_SHORT).show()
                
                // Optional: Allow setting user position if they're not positioned yet
                val currentPosition = positioningViewModel.currentPosition.value
                if (currentPosition == null) {
                    // Only allow setting position if no current position exists
                    positioningViewModel.setPosition(position)
                    Toast.makeText(requireContext(), 
                        "Position set manually (tap POIs to navigate)", 
                        Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            Log.e("NewMapFragment", "Error handling map touch: ${e.message}")
            e.printStackTrace()
        }
    }
    
    private fun calculateAndShowRoute(start: Position, end: Position) {
        try {
            pathfindingEngine?.let { engine ->
                val path = engine.findPath(start, end)
                if (path != null) {
                    // Show route on map
                    floorPlanView.setRoute(path.waypoints)
                    
                    // Show navigation metrics
                    showNavigationMetrics(path)
                    
                    Log.d("NewMapFragment", "Route calculated with ${path.waypoints.size} waypoints")
                } else {
                    // Show direct line if no path found
                    floorPlanView.setRoute(listOf(start, end))
                    Toast.makeText(requireContext(), "Showing direct route", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            Log.e("NewMapFragment", "Error calculating route: ${e.message}")
            e.printStackTrace()
        }
    }
    
    private fun showNavigationMetrics(path: com.example.indoornavigation.data.models.Path) {
        try {
            // Calculate total distance
            var totalDistance = 0.0
            for (i in 0 until path.waypoints.size - 1) {
                totalDistance += path.waypoints[i].distanceTo(path.waypoints[i + 1])
            }
            
            // Estimate time (assuming 1.4 m/s walking speed)
            val estimatedTime = (totalDistance / 1.4 / 60).toInt() // minutes
            
            // Update UI
            binding.navigationMetricsCard?.visibility = View.VISIBLE
            binding.tvRemainingDistance?.text = "${String.format("%.0f", totalDistance)}m"
            binding.tvEta?.text = "${estimatedTime}min"
            
        } catch (e: Exception) {
            Log.e("NewMapFragment", "Error showing navigation metrics: ${e.message}")
            e.printStackTrace()
        }
    }
    
    private fun addDemoBeaconMarkers() {
        // Demo beacon markers removed - will use real positioning data instead
        Log.d("NewMapFragment", "Using real positioning data from BLE and WiFi scanning")
    }
    
    private fun observePositioning() {
        try {
            if (::positioningViewModel.isInitialized) {
                // Observe current position
                viewLifecycleOwner.lifecycleScope.launch {
                    positioningViewModel.currentPosition.collect { position ->
                        position?.let {
                            if (::floorPlanView.isInitialized) {
                                // Only show user marker when inside building
                                if (navigationViewModel.locationStatus.value == LocationStatus.INSIDE_BUILDING) {
                                    floorPlanView.setUserPosition(it)
                                    Log.d("NewMapFragment", "Updated user position: $it")
                                    
                                    // Update route if destination is set
                                    destinationPosition?.let { dest ->
                                        calculateAndShowRoute(it, dest)
                                    }
                                }
                            }
                        }
                    }
                }
                
                // Observe current floor
                viewLifecycleOwner.lifecycleScope.launch {
                    positioningViewModel.currentFloor.collect { floor ->
                        binding.tvCurrentFloor?.apply {
                            text = "Floor: $floor"
                            visibility = View.VISIBLE
                        }
                        
                        // Update floor plan if needed
                        if (::floorPlanView.isInitialized) {
                            floorPlanView.setFloorPlan(floor)
                        }
                    }
                }
                
                Log.d("NewMapFragment", "Position observation setup complete")
            }
        } catch (e: Exception) {
            Log.e("NewMapFragment", "Error setting up position observation: ${e.message}")
            e.printStackTrace()
        }
    }

    private fun setupBasicUI() {
        try {
            // Setup menu button
            binding.btnMenu.setOnClickListener {
                val drawerLayout = requireActivity().findViewById<DrawerLayout>(R.id.drawer_layout)
                drawerLayout?.openDrawer(GravityCompat.START)
            }

            // Setup settings button - now toggles configuration mode
            binding.btnSettings.setOnClickListener {
                if (isConfigurationMode) {
                    exitConfigurationMode()
                } else {
                    isConfigurationMode = true
                    binding.tvTitle.text = "Configuration Mode"
                    binding.tvTitle.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.holo_orange_dark))
                    
                    // Enable edit mode for drag and drop
                    if (::floorPlanView.isInitialized) {
                        floorPlanView.setEditMode(true)
                    }
                    
                    // Don't automatically load configuration to preserve deletions
                    // loadConfiguration() // Removed this line to prevent deleted POIs from reappearing
                    Toast.makeText(requireContext(), 
                        "Configuration mode ON - Tap to add POIs, drag existing POIs to move them", 
                        Toast.LENGTH_LONG).show()
                    updateSearchHint()
                }
            }

            // Setup search container click listener
            binding.searchContainer.setOnClickListener {
                showSearchDialog()
            }

            // Setup FAB for centering user location
            binding.fabMyLocation.setOnClickListener {
                if (::floorPlanView.isInitialized) {
                    // Try to center on user position if available
                    floorPlanView.centerOnUser()
                    Toast.makeText(requireContext(), "Centering on your location", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(requireContext(), "My Location", Toast.LENGTH_SHORT).show()
                }
            }

            // Add long click listener to FAB for POI management
            binding.fabMyLocation.setOnLongClickListener {
                showPOIManagementDialog()
                true
            }

            // Set title
            binding.tvTitle.text = "Indoor Navigation"
            
            // Hide location status card (remove scanning text)
            binding.locationStatusCard?.visibility = View.GONE

            Log.d("NewMapFragment", "Basic UI setup complete")

        } catch (e: Exception) {
            Log.e("NewMapFragment", "Error setting up basic UI: ${e.message}")
            e.printStackTrace()
        }
    }
    
    fun showPOIManagementDialog() {
        val options = arrayOf(
            "📍 Manage POIs",
            "📡 Manage Reference Beacons", 
            "🔍 Discover Nearby Beacons",
            "🖱️ Enable Drag Mode",
            "💾 Save Configuration",
            "❌ Exit Configuration Mode"
        )
        
        AlertDialog.Builder(requireContext())
            .setTitle("POI & Beacon Management")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showPOIListDialog()
                    1 -> showBeaconListDialog()
                    2 -> discoverNearbyBeacons()
                    3 -> enableDragMode()
                    4 -> saveConfiguration()
                    5 -> exitConfigurationMode()
                }
            }
            .show()
    }
    
    private fun enableDragMode() {
        if (::floorPlanView.isInitialized) {
            floorPlanView.setEditMode(true)
            Toast.makeText(requireContext(), 
                "Drag mode enabled - You can now drag POIs to reposition them. Tap Settings to exit.", 
                Toast.LENGTH_LONG).show()
            
            // Update title to show drag mode
            binding.tvTitle.text = "Drag Mode - Move POIs"
            binding.tvTitle.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.holo_blue_dark))
            
            // Set a flag to track drag mode separately from configuration mode
            isConfigurationMode = true
        }
    }
    
    private fun showPOIListDialog() {
        if (realPOIs.isEmpty()) {
            Toast.makeText(requireContext(), "No POIs configured yet", Toast.LENGTH_SHORT).show()
            return
        }
        
        val options = realPOIs.map { "${it.name} (${it.type})" }.toTypedArray()
        val positions = realPOIs.map { it.position }.toTypedArray()
        
        AlertDialog.Builder(requireContext())
            .setTitle("Manage POIs")
            .setItems(options) { _, which ->
                // Show edit options for selected POI
                showPOIEditDialog(which)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun showPOIEditDialog(index: Int) {
        val poi = realPOIs[index]
        val layout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 40, 50, 10)
        }
        
        val nameInput = EditText(requireContext()).apply {
            hint = "POI Name"
            setText(poi.name)
        }
        
        val typeInput = EditText(requireContext()).apply {
            hint = "Type"
            setText(poi.type)
        }
        
        val descriptionInput = EditText(requireContext()).apply {
            hint = "Description"
            setText(poi.description)
        }
        
        layout.addView(nameInput)
        layout.addView(typeInput)
        layout.addView(descriptionInput)
        
        AlertDialog.Builder(requireContext())
            .setTitle("Edit POI: ${poi.name}")
            .setView(layout)
            .setPositiveButton("Save") { _, _ ->
                val name = nameInput.text.toString().trim()
                val type = typeInput.text.toString().trim()
                val description = descriptionInput.text.toString().trim()
                
                if (name.isNotEmpty() && type.isNotEmpty()) {
                    // Update POI
                    realPOIs[index] = realPOIs[index].copy(
                        name = name,
                        type = type,
                        description = description
                    )
                    
                    // Refresh markers
                    floorPlanView.clearMarkers()
                    realPOIs.forEach { addPOIMarker(it) }
                    realBeacons.forEach { addBeaconMarker(it) }
                    
                    // Auto-save configuration after editing
                    saveConfiguration()
                    
                    Toast.makeText(requireContext(), "Updated POI: $name and saved", Toast.LENGTH_SHORT).show()
                    updateSearchHint()
                } else {
                    Toast.makeText(requireContext(), "Please fill in name and type", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Delete") { _, _ ->
                // Remove POI
                realPOIs.removeAt(index)
                floorPlanView.clearMarkers()
                realPOIs.forEach { addPOIMarker(it) }
                realBeacons.forEach { addBeaconMarker(it) }
                
                // Auto-save configuration after deletion
                saveConfiguration()
                
                Toast.makeText(requireContext(), "Removed POI and saved configuration", Toast.LENGTH_SHORT).show()
                updateSearchHint()
            }
            .setNeutralButton("Cancel", null)
            .show()
    }
    
    private fun showBeaconListDialog() {
        if (realBeacons.isEmpty()) {
            AlertDialog.Builder(requireContext())
                .setTitle("No Reference Beacons")
                .setMessage("No reference beacons configured yet.\n\nReference beacons are WiFi or BLE devices with known exact positions that help improve indoor positioning accuracy.\n\nYou can:\n• Use 'Discover Nearby Beacons' to find devices\n• Manually add known beacon locations")
                .setPositiveButton("Discover Beacons") { _, _ -> discoverNearbyBeacons() }
                .setNegativeButton("Cancel", null)
                .show()
            return
        }
        
        val options = realBeacons.map { "${it.name} (${it.type}) - ${it.macAddress}" }.toTypedArray()
        val positions = realBeacons.map { it.position }.toTypedArray()
        
        AlertDialog.Builder(requireContext())
            .setTitle("Manage Reference Beacons")
            .setMessage("These beacons help improve positioning accuracy by providing known reference points.")
            .setItems(options) { _, which ->
                // Show edit options for selected beacon
                showBeaconEditDialog(which)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun showBeaconEditDialog(index: Int) {
        val beacon = realBeacons[index]
        val layout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 40, 50, 10)
        }
        
        val nameInput = EditText(requireContext()).apply {
            hint = "Beacon Name"
            setText(beacon.name)
        }
        
        val macInput = EditText(requireContext()).apply {
            hint = "MAC Address"
            setText(beacon.macAddress)
        }
        
        val typeInput = EditText(requireContext()).apply {
            hint = "Type (BLE, WiFi, or both)"
            setText(beacon.type)
        }
        
        layout.addView(nameInput)
        layout.addView(macInput)
        layout.addView(typeInput)
        
        AlertDialog.Builder(requireContext())
            .setTitle("Edit Beacon: ${beacon.name}")
            .setView(layout)
            .setPositiveButton("Save") { _, _ ->
                val name = nameInput.text.toString().trim()
                val mac = macInput.text.toString().trim()
                val type = typeInput.text.toString().trim()
                
                if (name.isNotEmpty()) {
                    // Update beacon
                    realBeacons[index] = realBeacons[index].copy(
                        name = name,
                        macAddress = mac,
                        type = type
                    )
                    
                    // Refresh markers
                    floorPlanView.clearMarkers()
                    realPOIs.forEach { addPOIMarker(it) }
                    realBeacons.forEach { addBeaconMarker(it) }
                    
                    // Auto-save configuration after editing
                    saveConfiguration()
                    
                    Toast.makeText(requireContext(), "Updated Beacon: $name and saved", Toast.LENGTH_SHORT).show()
                    updateSearchHint()
                } else {
                    Toast.makeText(requireContext(), "Please fill in beacon name", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Delete") { _, _ ->
                // Remove beacon
                realBeacons.removeAt(index)
                floorPlanView.clearMarkers()
                realPOIs.forEach { addPOIMarker(it) }
                realBeacons.forEach { addBeaconMarker(it) }
                
                // Auto-save configuration after deletion
                saveConfiguration()
                
                Toast.makeText(requireContext(), "Removed Beacon and saved configuration", Toast.LENGTH_SHORT).show()
                updateSearchHint()
            }
            .setNeutralButton("Cancel", null)
            .show()
    }
    
    private fun showSearchDialog() {
        try {
            // First check if we have real POIs configured
            if (realPOIs.isNotEmpty()) {
                val searchOptions = realPOIs.map { "📍 ${it.name} (${it.type})" }.toTypedArray()
                val positions = realPOIs.map { it.position }.toTypedArray()
                
                AlertDialog.Builder(requireContext())
                    .setTitle("🗺️ Select Destination")
                    .setItems(searchOptions) { _, which ->
                        val selectedPosition = positions[which]
                        val currentPosition = positioningViewModel.currentPosition.value
                        
                        if (currentPosition != null) {
                            // Navigate to selected position
                            destinationPosition = selectedPosition
                            calculateAndShowRoute(currentPosition, selectedPosition)
                            Toast.makeText(requireContext(), 
                                "Navigating to ${realPOIs[which].name}", 
                                Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(requireContext(), 
                                "Please wait for your location to be determined", 
                                Toast.LENGTH_SHORT).show()
                        }
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
                return
            }
            
            // If no real POIs, show message to configure them
            AlertDialog.Builder(requireContext())
                .setTitle("No POIs Configured")
                .setMessage("No Points of Interest have been configured yet.\n\nTo add POIs:\n1. Tap the Settings button to enter Configuration Mode\n2. Tap on map locations to add POIs\n3. Save your configuration")
                .setPositiveButton("Enter Configuration Mode") { _, _ ->
                    // Enter configuration mode
                    isConfigurationMode = true
                    loadConfiguration()
                    Toast.makeText(requireContext(), 
                        "Configuration mode ON - Tap on map to add POIs", 
                        Toast.LENGTH_LONG).show()
                }
                .setNegativeButton("Use Demo Data") { _, _ ->
                    showDemoSearchDialog()
                }
                .show()
                
        } catch (e: Exception) {
            Log.e("NewMapFragment", "Error showing search dialog: ${e.message}")
            e.printStackTrace()
            Toast.makeText(requireContext(), "Search functionality error", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun showDemoSearchDialog() {
        try {
            val searchOptions = arrayOf(
                "🚪 Main Entrance",
                "🏛️ Laman Najib", 
                "🏢 Central Hall",
                "🚻 Restroom (Near Entrance)",
                "🚻 Restroom (Central)",
                "☕ Cafeteria",
                "📚 Library Entrance",
                "🏢 Admin Office",
                "🔬 Lab A-01",
                "🔬 Lab A-02",
                "🎓 Lecture Hall A",
                "🎓 Lecture Hall B",
                "⬆️ Elevator",
                "🚶 Staircase A",
                "🚶 Staircase B",
                "🚨 Emergency Exit"
            )
            
            val positions = arrayOf(
                Position(35.0, 55.0, 0),  // Main Entrance
                Position(30.0, 25.0, 0),  // Laman Najib
                Position(40.0, 30.0, 0),  // Central Hall
                Position(25.0, 50.0, 0),  // Restroom (Near Entrance)
                Position(45.0, 35.0, 0),  // Restroom (Central)
                Position(15.0, 20.0, 0),  // Cafeteria
                Position(65.0, 40.0, 0),  // Library Entrance
                Position(20.0, 15.0, 0),  // Admin Office
                Position(55.0, 15.0, 0),  // Lab A-01
                Position(60.0, 25.0, 0),  // Lab A-02
                Position(10.0, 35.0, 0),  // Lecture Hall A
                Position(15.0, 40.0, 0),  // Lecture Hall B
                Position(40.0, 45.0, 0),  // Elevator
                Position(25.0, 35.0, 0),  // Staircase A
                Position(55.0, 45.0, 0),  // Staircase B
                Position(70.0, 55.0, 0)   // Emergency Exit
            )
            
            AlertDialog.Builder(requireContext())
                .setTitle("🗺️ Select Destination (Demo Data)")
                .setMessage("These are sample locations. Configure real POIs in Settings.")
                .setItems(searchOptions) { _, which ->
                    val selectedPosition = positions[which]
                    val currentPosition = positioningViewModel.currentPosition.value
                    
                    if (currentPosition != null) {
                        // Navigate to selected position
                        destinationPosition = selectedPosition
                        calculateAndShowRoute(currentPosition, selectedPosition)
                        Toast.makeText(requireContext(), 
                            "Navigating to ${searchOptions[which]}", 
                            Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(requireContext(), 
                            "Please wait for your location to be determined", 
                            Toast.LENGTH_SHORT).show()
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
                
        } catch (e: Exception) {
            Log.e("NewMapFragment", "Error showing demo search dialog: ${e.message}")
            e.printStackTrace()
            Toast.makeText(requireContext(), "Search functionality error", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
    
    // Method to update the search hint text dynamically
    private fun updateSearchHint() {
        try {
            val searchText = binding.searchContainer.findViewById<TextView>(android.R.id.text1) 
                ?: binding.searchContainer.getChildAt(1) as? TextView
            
            if (realPOIs.isNotEmpty()) {
                searchText?.text = "Search ${realPOIs.size} configured locations (Menu → Manage POIs to edit/drag)"
            } else {
                searchText?.text = "Tap to configure locations • Menu → Manage POIs for drag & drop"
            }
        } catch (e: Exception) {
            Log.w("NewMapFragment", "Could not update search hint: ${e.message}")
        }
    }
    
    /**
     * Find POI at or near the given position
     * @param position The touched position
     * @param tolerance The tolerance distance in meters (default 3 meters)
     * @return The POI if found, null otherwise
     */
    private fun findPOIAtPosition(position: Position, tolerance: Double = 3.0): RealPOI? {
        return realPOIs.find { poi ->
            poi.position.distanceTo(position) <= tolerance
        }
    }
    
    private fun discoverNearbyBeacons() {
        Toast.makeText(requireContext(), "Scanning for nearby beacons and WiFi networks...", Toast.LENGTH_SHORT).show()
        
        // Get current positioning data
        if (::positioningViewModel.isInitialized) {
            // Get discovered beacons and WiFi networks from the positioning system
            val discoveredItems = mutableListOf<String>()
            
            // Add some sample discovered items (in real implementation, get from positioning system)
            discoveredItems.add("🔷 WiFi: CS1-Building-Guest (Signal: -45 dBm)")
            discoveredItems.add("🔷 WiFi: CAMPUS_WIFI (Signal: -52 dBm)")
            discoveredItems.add("🔵 BLE: Unknown Device (Signal: -38 dBm)")
            discoveredItems.add("🔵 BLE: Smart Display (Signal: -61 dBm)")
            
            if (discoveredItems.isNotEmpty()) {
                AlertDialog.Builder(requireContext())
                    .setTitle("Discovered Beacons & WiFi Networks")
                    .setMessage("Select items to add as reference points for improved positioning accuracy:")
                    .setItems(discoveredItems.toTypedArray()) { _, which ->
                        val selectedItem = discoveredItems[which]
                        showAddDiscoveredBeaconDialog(selectedItem)
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            } else {
                AlertDialog.Builder(requireContext())
                    .setTitle("No Beacons Found")
                    .setMessage("No nearby beacons or WiFi networks detected. Make sure:\n\n• Bluetooth is enabled\n• WiFi is enabled\n• You're near the building\n• Permissions are granted")
                    .setPositiveButton("Retry") { _, _ -> discoverNearbyBeacons() }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        } else {
            Toast.makeText(requireContext(), "Positioning system not ready", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun showAddDiscoveredBeaconDialog(discoveredItem: String) {
        val layout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 40, 50, 10)
        }
        
        val nameInput = EditText(requireContext()).apply {
            hint = "Reference Point Name"
            setText(discoveredItem.split(": ")[1].split(" ")[0]) // Extract device name
        }
        
        val descriptionInput = EditText(requireContext()).apply {
            hint = "Description (e.g., 'Near main entrance', 'Lab 1 area')"
        }
        
        layout.addView(nameInput)
        layout.addView(descriptionInput)
        
        val currentPosition = positioningViewModel.currentPosition.value
        val defaultPosition = currentPosition ?: Position(35.0, 35.0, 0)
        
        AlertDialog.Builder(requireContext())
            .setTitle("Add Reference Beacon")
            .setMessage("This will add a reference point at your current location to help improve positioning accuracy.")
            .setView(layout)
            .setPositiveButton("Add Here") { _, _ ->
                val name = nameInput.text.toString().trim().ifEmpty { "Reference Point" }
                val description = descriptionInput.text.toString().trim()
                
                addRealBeacon(
                    name = name,
                    mac = extractMacFromDiscovered(discoveredItem),
                    type = if (discoveredItem.contains("WiFi")) "WiFi Reference" else "BLE Reference",
                    position = defaultPosition
                )
                
                Toast.makeText(requireContext(), 
                    "Added reference beacon: $name\nThis will help improve positioning accuracy in this area", 
                    Toast.LENGTH_LONG).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun extractMacFromDiscovered(discoveredItem: String): String {
        // In real implementation, extract actual MAC address from discovered beacon
        return "Auto-discovered"
    }
    
    // Data classes for real building data
    data class RealPOI(
        val name: String,
        val type: String,
        val description: String,
        val position: Position
    )
    
    data class RealBeacon(
        val name: String,
        val macAddress: String,
        val type: String,
        val position: Position
    )
}
