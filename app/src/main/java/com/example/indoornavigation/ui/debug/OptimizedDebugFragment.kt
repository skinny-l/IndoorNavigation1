package com.example.indoornavigation.ui.debug

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.SeekBar
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.indoornavigation.R
import com.example.indoornavigation.data.models.Beacon
import com.example.indoornavigation.data.models.Position
import com.example.indoornavigation.data.models.WifiAccessPoint
import com.example.indoornavigation.data.wifi.WifiPositioningManager
import com.example.indoornavigation.databinding.FragmentOptimizedDebugBinding
import com.example.indoornavigation.ui.common.BaseFragment
import com.example.indoornavigation.ui.debug.adapter.AccessPointAdapter
import com.example.indoornavigation.ui.debug.adapter.BeaconAdapter
import com.example.indoornavigation.ui.debug.calibration.BeaconCalibrationActivity
import com.example.indoornavigation.viewmodel.PositioningViewModel
import com.example.indoornavigation.viewmodel.RealBLEViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlin.math.abs
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Optimized Debug Fragment that combines features from previous debug implementations
 * with enhanced modular design and improved visualization capabilities
 */
class OptimizedDebugFragment : BaseFragment() {

    companion object {
        private const val TAG = "OptimizedDebugFragment"
        private const val MAX_POSITION_HISTORY = 30
    }

    private var _binding: FragmentOptimizedDebugBinding? = null
    private val binding get() = _binding!!

    private val positioningViewModel: PositioningViewModel by activityViewModels()
    private val bleViewModel: RealBLEViewModel by activityViewModels()

    private lateinit var beaconAdapter: BeaconAdapter
    private lateinit var accessPointAdapter: AccessPointAdapter
    private lateinit var wifiManager: WifiPositioningManager

    private var isScanning = false
    private var visualizationMode = EnhancedFloorPlanView.VISUALIZATION_POSITION_ACCURACY // 0: position accuracy, 1: movement history
    
    // Path loss exponent for distance calculation (default: 1.8)
    private var pathLossExponent = 1.8f

    // Track connected beacons for trilateration
    private val connectedBeacons = mutableSetOf<String>()

    // Selected positioning method (0=Auto, 1=Trilateration, 2=Weighted Centroid, 3=Kalman Filter, 4=Fingerprinting, 5=Sensor Fusion)
    private var selectedPositioningMethod = 0

    // For Kalman filter state persistence
    private val kalmanStateX = KalmanFilter()
    private val kalmanStateY = KalmanFilter()

    // For fingerprinting simulation
    private val fingerprints = mutableListOf<Fingerprint>()

    // Selected grid size (0=2x2m, 1=3x3m, 2=5x5m, 3=10x10m, 4=20x20m, 5=50x50m)
    private var selectedGridSize = 0 // Default to 2x2m

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        wifiManager = WifiPositioningManager(requireContext().applicationContext)
        _binding = FragmentOptimizedDebugBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        try {
            Log.d(TAG, "Setting up FloorPlanView")
            setupFloorPlanView()

            Log.d(TAG, "Setting up SignalAdapters")
            setupSignalAdapters()

            Log.d(TAG, "Setting up ExpandableSections")
            setupExpandableSections()

            Log.d(TAG, "Setting up Positioning Method Selector")
            setupPositioningMethodSelector()

            Log.d(TAG, "Setting up Grid Size Selector")
            setupGridSizeSelector()

            Log.d(TAG, "Setting up Beacon Calibration Button")
            setupBeaconCalibrationButton()

            Log.d(TAG, "Observing Position Updates")
            observePositionUpdates()

            Log.d(TAG, "Observing Signal Updates")
            observeSignalUpdates()

            // Set up visualization controls
            setupVisualizationControls()

            // Set up positioning method selector
            setupPositioningMethodSelector()

            // Auto start BLE scanning when fragment is created
            try {
                startBleScanning()
            } catch (e: Exception) {
                Log.e(TAG, "Error starting BLE scanning: ${e.message}", e)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in onViewCreated: ${e.message}", e)
            Toast.makeText(context, "Error setting up debug view: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun setupFloorPlanView() {
        updateGridSize(selectedGridSize)
        binding.floorPlanView.setVisualizationMode(EnhancedFloorPlanView.VISUALIZATION_POSITION_ACCURACY)
        binding.floorPlanView.setShowDistanceRings(true)
        binding.floorPlanView.setShowAccuracy(true)
    }

    private fun setupSignalAdapters() {
        try {
            // Setup beacon adapter with connect/disconnect functionality
            beaconAdapter = BeaconAdapter(object : BeaconAdapter.BeaconActionListener {
                override fun onConnectBeacon(beaconId: String) {
                    bleViewModel.connectBeacon(beaconId)
                    showNotification("Connected to beacon $beaconId")
                    beaconAdapter.updateConnectedState(beaconId, true)
                    connectedBeacons.add(beaconId)
                    Log.d(TAG, "Beacon connected: $beaconId, total connected: ${connectedBeacons.size}")
                    
                    // Start trilateration automatically when 3 beacons are connected
                    if (connectedBeacons.size >= 3) {
                        startBLETrilateration()
                    }
                }

                override fun onDisconnectBeacon(beaconId: String) {
                    bleViewModel.disconnectBeacon(beaconId)
                    showNotification("Disconnected from beacon $beaconId")
                    beaconAdapter.updateConnectedState(beaconId, false)
                    connectedBeacons.remove(beaconId)
                    Log.d(TAG, "Beacon disconnected: $beaconId, total connected: ${connectedBeacons.size}")
                }
            })
            binding.recyclerBeacons.layoutManager = LinearLayoutManager(requireContext())
            binding.recyclerBeacons.adapter = beaconAdapter
            Log.d(TAG, "Beacon adapter setup complete")

            // Setup WiFi adapter
            accessPointAdapter = AccessPointAdapter()
            binding.recyclerWifi.layoutManager = LinearLayoutManager(requireContext())
            binding.recyclerWifi.adapter = accessPointAdapter
            Log.d(TAG, "WiFi adapter setup complete")
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up adapters: ${e.message}", e)
            Toast.makeText(context, "Error setting up adapters: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun setupExpandableSections() {
        // Beacon section toggle
        binding.beaconsSectionHeader.setOnClickListener {
            binding.recyclerBeacons.visibility = if (binding.recyclerBeacons.visibility == View.VISIBLE) View.GONE else View.VISIBLE
            binding.beaconSectionIcon.setImageResource(
                if (binding.recyclerBeacons.visibility == View.VISIBLE) R.drawable.ic_chevron_down else R.drawable.ic_chevron_right
            )
        }

        // WiFi section toggle
        binding.wifiSectionHeader.setOnClickListener {
            binding.recyclerWifi.visibility = if (binding.recyclerWifi.visibility == View.VISIBLE) View.GONE else View.VISIBLE
            binding.wifiSectionIcon.setImageResource(
                if (binding.recyclerWifi.visibility == View.VISIBLE) R.drawable.ic_chevron_down else R.drawable.ic_chevron_right
            )
        }
    }

    /**
     * Set up the visualization controls
     */
    private fun setupVisualizationControls() {
        try {
            // Visualization mode switch
            binding.switchVisualizationMode.setOnCheckedChangeListener { _, isChecked ->
                visualizationMode = if (isChecked) {
                    EnhancedFloorPlanView.VISUALIZATION_MOVEMENT_HISTORY
                } else {
                    EnhancedFloorPlanView.VISUALIZATION_POSITION_ACCURACY
                }
                
                binding.floorPlanView.setVisualizationMode(visualizationMode)
                val modeName = if (isChecked) "Movement Trail" else "Position Accuracy"
                showNotification("View mode: $modeName")
            }
    
            // Clear trail button
            binding.btnClearTrail.setOnClickListener {
                binding.floorPlanView.clearPositionHistory()
                showNotification("Position history cleared")
            }
            
            // Path loss exponent slider
            binding.txtPathLossValue.text = "Path Loss Exponent: ${String.format("%.1f", pathLossExponent)}"
            binding.seekBarPathLoss.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    pathLossExponent = progress / 10f
                    binding.txtPathLossValue.text = "Path Loss Exponent: ${String.format("%.1f", pathLossExponent)}"
                    bleViewModel.updatePathLossExponent(pathLossExponent)
                }
                
                override fun onStartTrackingTouch(seekBar: SeekBar?) {
                    // Not used
                }
                
                override fun onStopTrackingTouch(seekBar: SeekBar?) {
                    showNotification("Path loss exponent updated: ${String.format("%.1f", pathLossExponent)}")
                }
            })
            

        } catch (e: Exception) {
            Log.e(TAG, "Error setting up visualization controls: ${e.message}")
        }
    }

    /**
     * Set up the beacon calibration button
     */
    private fun setupBeaconCalibrationButton() {
        binding.btnBeaconCalibration.setOnClickListener {
            try {
                // Launch the beacon calibration activity
                val intent = Intent(requireContext(), BeaconCalibrationActivity::class.java)
                if (intent.resolveActivity(requireContext().packageManager) != null) {
                    startActivity(intent)
                    showNotification("Starting beacon calibration")
                } else {
                    showNotification("Beacon calibration not available")
                    Log.e(TAG, "BeaconCalibrationActivity not found in package")
                }
            } catch (e: SecurityException) {
                Log.e(TAG, "Security exception starting calibration: ${e.message}", e)
                showNotification("Permission denied for calibration")
            } catch (e: Exception) {
                Log.e(TAG, "Error starting calibration: ${e.message}", e)
                showNotification("Error: ${e.message}")
            }
        }
    }

    /**
     * Set up the positioning method selector
     */
    private fun setupPositioningMethodSelector() {
        try {
            // Create adapter for the spinner
            val adapter = ArrayAdapter.createFromResource(
                requireContext(),
                R.array.positioning_methods,
                android.R.layout.simple_spinner_item
            )
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)

            // Apply adapter to spinner
            binding.positioningMethodSpinner?.adapter = adapter

            // Set default selection
            binding.positioningMethodSpinner?.setSelection(0) // Auto (Best Available)

            // Set listener for selection changes
            binding.positioningMethodSpinner?.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    selectedPositioningMethod = position
                    Log.d(TAG, "Selected positioning method: $position")

                    // If we already have enough beacons connected, update the visualization
                    if (connectedBeacons.size >= 1) {
                        startBLETrilateration()
                    }
                }

                override fun onNothingSelected(parent: AdapterView<*>?) {
                    // Do nothing
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up positioning method selector: ${e.message}")
        }
    }

    /**
     * Set up the grid size selector
     */
    private fun setupGridSizeSelector() {
        try {
            // Create adapter for the spinner
            val adapter = ArrayAdapter.createFromResource(
                requireContext(),
                R.array.grid_size_options,
                android.R.layout.simple_spinner_item
            )
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)

            // Apply adapter to spinner
            binding.gridSizeSpinner?.adapter = adapter

            // Set default selection
            binding.gridSizeSpinner?.setSelection(0) // 2x2m (Very Small Room)

            // Set listener for selection changes
            binding.gridSizeSpinner?.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    selectedGridSize = position
                    updateGridSize(position)
                    showNotification("Grid size updated")
                }

                override fun onNothingSelected(parent: AdapterView<*>?) {
                    // Do nothing
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up grid size selector: ${e.message}")
        }
    }

    /**
     * Update the grid size based on selection
     */
    private fun updateGridSize(selection: Int) {
        val gridSize = when (selection) {
            0 -> 2f to 2f     // Very small room (2x2m)
            1 -> 3f to 3f     // Desk area (3x3m)
            2 -> 5f to 5f     // Small room (5x5m)
            3 -> 10f to 10f   // Medium room (10x10m)
            4 -> 20f to 20f   // Large room (20x20m)
            5 -> 50f to 50f   // Building floor (50x50m)
            else -> 5f to 5f  // Default to small room
        }
        
        binding.floorPlanView.setFloorPlanDimensions(gridSize.first, gridSize.second)
        Log.d(TAG, "Grid size updated to ${gridSize.first}x${gridSize.second} meters")
    }

    private fun observePositionUpdates() {
        viewLifecycleOwner.lifecycleScope.launch {
            positioningViewModel.currentPosition.collectLatest { position ->
                position?.let {
                    binding.positionText.text = "Position: X: ${it.x.toInt()}, Y: ${it.y.toInt()}"
                    binding.floorText.text = "Floor: ${it.floor}"
                    binding.floorPlanView.updateUserPosition(it, 2.0f)
                } ?: run {
                    binding.positionText.text = "Position: Unknown"
                }
            }
        }

        // Observe floor changes
        viewLifecycleOwner.lifecycleScope.launch {
            positioningViewModel.currentFloor.collectLatest { floor ->
                val position = floor - 1
                if (position >= 0) {
                    // Removed floor selector code
                }
            }
        }

        // Set status text
        binding.statusText.text = "Active"
        binding.statusText.setTextColor(ContextCompat.getColor(requireContext(), R.color.purple_500))
    }

    private fun observeSignalUpdates() {
        // Observe real beacon data from BLE ViewModel
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                bleViewModel.uiState.collectLatest { state ->
                    if (state.availableBeacons.isNotEmpty()) {
                        val beacons = mutableListOf<Beacon>()

                        // Convert available beacons to our beacon model
                        state.availableBeacons.forEach { beaconInfo ->
                            // Handle position if available
                            val position = if (beaconInfo.position != null) {
                                try {
                                    Position(
                                        x = beaconInfo.position.first.toDouble(),
                                        y = beaconInfo.position.second.toDouble(),
                                        floor = positioningViewModel.currentFloor.value
                                    )
                                } catch (e: Exception) {
                                    Log.e(TAG, "Error converting position: ${e.message}")
                                    null
                                }
                            } else {
                                // Assign default positions to beacons if they don't have one 
                                // This ensures all beacons are displayed on the map
                                val index = state.availableBeacons.indexOf(beaconInfo)
                                val defaultX = when (index % 3) {
                                    0 -> 10.0
                                    1 -> 30.0
                                    else -> 20.0
                                }
                                val defaultY = when (index % 3) {
                                    0 -> 10.0
                                    1 -> 30.0
                                    else -> 20.0
                                }
                                Position(
                                    x = defaultX,
                                    y = defaultY,
                                    floor = positioningViewModel.currentFloor.value
                                )
                            }

                            // Calculate signal strength label
                            val signalStrength = when {
                                beaconInfo.rssi > -70 -> "strong"
                                beaconInfo.rssi > -85 -> "medium"
                                else -> "weak"
                            }

                            // Create beacon object with explicitly converted types
                            try {
                                // Explicitly convert distance to Double
                                val distanceDouble = beaconInfo.distance.toDouble()

                                beacons.add(
                                    Beacon(
                                        id = beaconInfo.id,
                                        name = beaconInfo.name,
                                        rssi = beaconInfo.rssi,
                                        distance = distanceDouble,
                                        position = position,
                                        signalStrength = signalStrength
                                    )
                                )
                            } catch (e: Exception) {
                                Log.e(TAG, "Error creating beacon: ${e.message}")
                            }
                        }

                        // Update adapters
                        beaconAdapter.submitList(beacons)

                        // Update counter text in view
                        binding.beaconCountText?.text = "Detected Beacons (${beacons.size})"

                        // Update floor plan visualization only with connected beacons
                        try {
                            // Log connected beacons for debugging
                            val connected = beacons.filter { connectedBeacons.contains(it.id) }
                            Log.d(TAG, "Connected beacons: ${connected.size} - IDs: ${connected.map { it.id }}")
                            
                            // Make sure all connected beacons have positions
                            val connectedWithPositions = connected.map { beacon ->
                                if (beacon.position == null) {
                                    // Assign a position if missing
                                    val index = connected.indexOf(beacon)
                                    val defaultX = when (index % 3) {
                                        0 -> 1.0
                                        1 -> 3.0
                                        else -> 2.0
                                    }
                                    val defaultY = when (index % 3) {
                                        0 -> 1.0
                                        1 -> 3.0
                                        else -> 2.0
                                    }
                                    beacon.copy(
                                        position = Position(
                                            x = defaultX,
                                            y = defaultY,
                                            floor = positioningViewModel.currentFloor.value
                                        )
                                    )
                                } else {
                                    beacon
                                }
                            }
                            
                            // Create beacon data for display
                            val beaconData = connectedWithPositions.map { beacon ->
                                EnhancedFloorPlanView.BeaconData(
                                    position = beacon.position!!,
                                    distance = beacon.distance,
                                    rssi = beacon.rssi
                                )
                            }
                            
                            // Log final beacon data
                            Log.d(TAG, "Displaying ${beaconData.size} beacons on map")
                            
                            binding.floorPlanView.updateBeacons(beaconData)
                            
                            // Update connected beacons count in the UI
                            binding.beaconCountText?.text = "Detected Beacons (${beacons.size}, ${connected.size} connected)"
                        } catch (e: Exception) {
                            Log.e(TAG, "Error updating floor plan: ${e.message}")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error observing beacons: ${e.message}")
            }
        }

        // Observe WiFi access points - using real data
        viewLifecycleOwner.lifecycleScope.launch {
            // Use a placeholder message until we get real scan results
            binding.wifiCountText?.text = "WiFi Access Points (scanning...)"

            // Start WiFi scanning
            wifiManager.startScanning()

            // Observe detected access points
            wifiManager.detectedAccessPoints.collectLatest { accessPointMap ->
                val accessPoints = accessPointMap.values.toList()
                accessPointAdapter.submitList(accessPoints)
                binding.wifiCountText?.text = "WiFi Access Points (${accessPoints.size})"

                Log.d(TAG, "WiFi Update: ${accessPoints.size} access points detected")
            }
        }
    }

    /**
     * Calculate approximate distance from RSSI using log-distance path loss model
     */
    private fun calculateDistanceFromRssi(rssi: Int): Double {
        val txPower = -59 // Reference RSSI at 1 meter
        // Use the current path loss exponent from slider
        if (rssi == 0) {
            return 0.0
        }

        val ratio = txPower - rssi
        return Math.pow(10.0, ratio / (10.0 * pathLossExponent))
    }

    /**
     * Start the BLE scanning process
     */
    private fun startBleScanning() {
        try {
            isScanning = true
            positioningViewModel.startScanning()
            bleViewModel.startScanning()
            binding.btnToggleScanning.text = "Stop Scanning"
            binding.btnToggleScanning.backgroundTintList = ContextCompat.getColorStateList(requireContext(), R.color.red_warning)
            Log.d(TAG, "BLE scanning started")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start BLE scanning: ${e.message}", e)
            Toast.makeText(requireContext(), "BLE error: ${e.message}", Toast.LENGTH_SHORT).show()
            isScanning = false
            binding.btnToggleScanning.text = "Start Beacon Scanning"
            binding.btnToggleScanning.backgroundTintList = ContextCompat.getColorStateList(requireContext(), R.color.yellow_signal)
        }
    }

    /**
     * Stop the BLE scanning process
     */
    private fun stopBleScanning() {
        try {
            isScanning = false
            positioningViewModel.stopScanning()
            bleViewModel.stopScanning()
            binding.btnToggleScanning.text = "Start Beacon Scanning"
            binding.btnToggleScanning.backgroundTintList = ContextCompat.getColorStateList(requireContext(), R.color.yellow_signal)
            Log.d(TAG, "BLE scanning stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop BLE scanning: ${e.message}", e)
        }
    }

    /**
     * Show a notification snackbar
     */
    private fun showNotification(message: String) {
        binding.notificationContainer.visibility = View.VISIBLE
        binding.notificationText.text = message
        binding.notificationSubtext.visibility = View.GONE

        // Hide after 3 seconds
        binding.root.postDelayed({
            binding.notificationContainer.visibility = View.GONE
        }, 3000)
    }

    /**
     * Show notification with subtext
     */
    private fun showNotificationWithSubtext(message: String, subtext: String) {
        binding.notificationContainer.visibility = View.VISIBLE
        binding.notificationText.text = message
        binding.notificationSubtext.text = subtext
        binding.notificationSubtext.visibility = View.VISIBLE

        // Hide after 3 seconds
        binding.root.postDelayed({
            binding.notificationContainer.visibility = View.GONE
        }, 3000)
    }

    /**
     * Start BLE trilateration with the currently connected beacons
     */
    private fun startBLETrilateration() {
        try {
            // Set visualization mode to position accuracy
            visualizationMode = EnhancedFloorPlanView.VISUALIZATION_POSITION_ACCURACY
            binding.floorPlanView.setShowDistanceRings(true)
            binding.floorPlanView.setShowAccuracy(true)
            binding.floorPlanView.setVisualizationMode(visualizationMode)

            // Calculate position using connected beacons
            val connectedBeaconsList = beaconAdapter.currentList.filter { connectedBeacons.contains(it.id) }
            Log.d(TAG, "Positioning with ${connectedBeaconsList.size} connected beacons, method: $selectedPositioningMethod")

            // Update beacons on map
            binding.floorPlanView.updateBeacons(connectedBeaconsList.mapNotNull { beacon ->
                beacon.position?.let { pos ->
                    EnhancedFloorPlanView.BeaconData(
                        position = pos,
                        distance = beacon.distance,
                        rssi = beacon.rssi
                    )
                }
            })

            // Determine which positioning method to use based on selection and available beacons
            val methodToUse = when {
                // Auto mode - choose best method based on available beacons
                selectedPositioningMethod == 0 -> {
                    when {
                        connectedBeaconsList.size >= 3 -> 1 // Trilateration
                        connectedBeaconsList.size > 0 -> 2 // Weighted Centroid
                        else -> 2 // Default to weighted centroid if no beacons
                    }
                }
                // Use selected method if possible
                else -> selectedPositioningMethod
            }

            // Apply the selected method
            when (methodToUse) {
                1 -> { // Trilateration
                    if (connectedBeaconsList.size >= 3) {
                        binding.floorPlanView.setPositioningMethod("Trilateration")
                        simulateTrilateration(connectedBeaconsList)
                    } else {
                        showNotification("Need at least 3 beacons for Trilateration")
                        binding.floorPlanView.setPositioningMethod("Insufficient Beacons")
                    }
                }
                2 -> { // Weighted Centroid
                    if (connectedBeaconsList.isNotEmpty()) {
                        binding.floorPlanView.setPositioningMethod("Weighted Centroid")
                        simulateWeightedCentroid(connectedBeaconsList)
                    } else {
                        showNotification("Need at least 1 beacon for Weighted Centroid")
                        binding.floorPlanView.setPositioningMethod("No Beacons Available")
                    }
                }
                3 -> { // Kalman Filter
                    if (connectedBeaconsList.isNotEmpty()) {
                        binding.floorPlanView.setPositioningMethod("Kalman Filter")
                        simulateKalmanFilter(connectedBeaconsList)
                    } else {
                        showNotification("Need at least 1 beacon for Kalman Filter")
                        binding.floorPlanView.setPositioningMethod("No Beacons Available")
                    }
                }
                4 -> { // Fingerprinting
                    binding.floorPlanView.setPositioningMethod("Fingerprinting")
                    simulateFingerprinting(connectedBeaconsList)
                }
                5 -> { // Sensor Fusion
                    if (connectedBeaconsList.isNotEmpty()) {
                        binding.floorPlanView.setPositioningMethod("Sensor Fusion")
                        simulateSensorFusion(connectedBeaconsList)
                    } else {
                        showNotification("Need at least 1 beacon for Sensor Fusion")
                        binding.floorPlanView.setPositioningMethod("No Beacons Available")
                    }
                }
            }

            // Show notification
            showNotificationWithSubtext(
                "Positioning Active",
                "Using ${connectedBeaconsList.size} beacons for positioning"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error starting trilateration: ${e.message}")
            showNotification("Error: ${e.message}")
        }
    }

    /**
     * Simulate trilateration calculation using beacon positions and distances
     */
    private fun simulateTrilateration(beacons: List<Beacon>) {
        try {
            // Need at least 3 beacons with positions
            val beaconsWithPosition = beacons.filter { it.position != null }
            if (beaconsWithPosition.size < 3) {
                showNotification("Not enough beacons with known positions")
                return
            }

            // Get the first 3 beacons
            val b1 = beaconsWithPosition[0]
            val b2 = beaconsWithPosition[1]
            val b3 = beaconsWithPosition[2]

            // Get positions and distances
            val p1 = b1.position!!
            val p2 = b2.position!!
            val p3 = b3.position!!

            val r1 = b1.distance.coerceAtLeast(0.1) // Ensure positive distance
            val r2 = b2.distance.coerceAtLeast(0.1)
            val r3 = b3.distance.coerceAtLeast(0.1)

            // Calculate midpoint as an approximation
            // In a real app, this would use proper trilateration math
            val posX = (p1.x + p2.x + p3.x) / 3
            val posY = (p1.y + p2.y + p3.y) / 3

            // Calculate accuracy as the average distance from the position to each beacon
            val accuracy = (r1 + r2 + r3) / 3

            // Create a position object
            val calculatedPosition = Position(posX, posY, positioningViewModel.currentFloor.value)

            // Update the visualization with the calculated position
            binding.floorPlanView.updateUserPosition(calculatedPosition, accuracy.toFloat(), "Trilateration")

            // Show details
            val message = "Calculated position: (${posX.toInt()}, ${posY.toInt()}), Accuracy: ${accuracy.toFloat()}"
            showNotification(message)

        } catch (e: Exception) {
            Log.e(TAG, "Error in trilateration: ${e.message}")
            showNotification("Trilateration error: ${e.message}")
        }
    }

    /**
     * Simulate weighted centroid calculation for fewer than 3 beacons
     */
    private fun simulateWeightedCentroid(beacons: List<Beacon>) {
        try {
            // Need at least 1 beacon with position
            val beaconsWithPosition = beacons.filter { it.position != null }
            if (beaconsWithPosition.isEmpty()) {
                showNotification("No beacons with known positions")
                return
            }

            // Calculate weighted centroid
            var sumX = 0.0
            var sumY = 0.0
            var sumWeights = 0.0

            beaconsWithPosition.forEach { beacon ->
                // Enhanced weighting - use inverse square of distance to make closer beacons have even more influence
                // This makes the user position more responsive to movement near a beacon
                val weight = 1.0 / Math.pow(beacon.distance.coerceAtLeast(0.1), 2.0).toDouble()  
                beacon.position?.let {
                    sumX += it.x * weight
                    sumY += it.y * weight
                    sumWeights += weight
                }
            }

            val posX = sumX / sumWeights
            val posY = sumY / sumWeights

            // Calculate average distance as accuracy estimate
            val accuracy = beaconsWithPosition.map { it.distance }.average().toFloat()

            // Create a position object
            val calculatedPosition = Position(posX, posY, positioningViewModel.currentFloor.value)

            // Update the visualization with the calculated position
            binding.floorPlanView.updateUserPosition(calculatedPosition, accuracy, "Weighted Centroid")

            // Show details
            val message = "Centroid position: (${posX.toInt()}, ${posY.toInt()}), Accuracy: $accuracy"
            showNotification(message)

        } catch (e: Exception) {
            Log.e(TAG, "Error in weighted centroid: ${e.message}")
            showNotification("Calculation error: ${e.message}")
        }
    }

    /**
     * Simulate Kalman filter positioning
     */
    private fun simulateKalmanFilter(beacons: List<Beacon>) {
        try {
            // First get a raw position estimate using weighted centroid
            val beaconsWithPosition = beacons.filter { it.position != null }
            if (beaconsWithPosition.isEmpty()) {
                showNotification("No beacons with known positions")
                return
            }

            // Calculate weighted centroid for raw estimate
            var sumX = 0.0
            var sumY = 0.0
            var sumWeights = 0.0

            beaconsWithPosition.forEach { beacon ->
                // Weight is inversely proportional to distance
                val weight = 1.0 / beacon.distance.coerceAtLeast(0.1)
                beacon.position?.let {
                    sumX += it.x * weight
                    sumY += it.y * weight
                    sumWeights += weight
                }
            }

            if (sumWeights == 0.0) {
                return
            }

            val rawX = sumX / sumWeights
            val rawY = sumY / sumWeights

            // Calculate average distance as measurement noise estimate, but limit the noise
            // to make the filter more responsive to small movements
            val measurementNoise = (beaconsWithPosition.map { it.distance }.average() * 0.5)
                .coerceAtMost(1.0).toFloat() // Limit noise to improve responsiveness

            // Apply Kalman filter with more responsive parameters
            kalmanStateX.setProcessNoise(0.05f) // Increase process noise for better responsiveness
            kalmanStateY.setProcessNoise(0.05f)
            val filteredX = kalmanStateX.update(rawX.toFloat(), measurementNoise)
            val filteredY = kalmanStateY.update(rawY.toFloat(), measurementNoise)

            // Create a position object with filtered coordinates
            val calculatedPosition = Position(filteredX.toDouble(), filteredY.toDouble(), positioningViewModel.currentFloor.value)

            // Calculate filtered position's accuracy - typically lower than raw due to filtering
            val accuracy = measurementNoise * 0.6f // Kalman filtering typically improves accuracy

            // Update the visualization with the calculated position
            binding.floorPlanView.updateUserPosition(calculatedPosition, accuracy, "Kalman Filter")

            // Show details
            val message = "Kalman filtered position: (${filteredX.toInt()}, ${filteredY.toInt()}), Accuracy: $accuracy"
            showNotification(message)

        } catch (e: Exception) {
            Log.e(TAG, "Error in Kalman filter: ${e.message}")
            showNotification("Kalman filter error: ${e.message}")
        }
    }

    /**
     * Simulate Fingerprinting positioning
     */
    private fun simulateFingerprinting(beacons: List<Beacon>) {
        try {
            // Generate fingerprint database if needed
            if (fingerprints.isEmpty()) {
                generateFingerprintDatabase()
            }

            if (fingerprints.isEmpty()) {
                showNotification("No fingerprint database available")
                return
            }

            // Create a measurement from current beacon readings
            val currentReadings = beacons.associate { it.id to it.rssi }

            // Find the best matching fingerprint
            var bestMatch: Fingerprint? = null
            var smallestDifference = Float.MAX_VALUE

            for (fingerprint in fingerprints) {
                var totalDifference = 0f
                var matchedSignals = 0

                // Compare RSSI values for each beacon in the fingerprint
                for ((beaconId, expectedRssi) in fingerprint.beaconReadings) {
                    val actualRssi = currentReadings[beaconId] ?: continue

                    // Calculate difference
                    val difference = abs(actualRssi - expectedRssi)
                    totalDifference += difference
                    matchedSignals++
                }

                // Skip fingerprints with no matching beacons
                if (matchedSignals == 0) continue

                // Calculate average difference
                val averageDifference = totalDifference / matchedSignals

                // Check if this is the best match
                if (averageDifference < smallestDifference) {
                    smallestDifference = averageDifference
                    bestMatch = fingerprint
                }
            }

            if (bestMatch == null) {
                showNotification("No matching fingerprint found")
                return
            }

            // Use position from the best matching fingerprint
            val posX = bestMatch.position.x
            val posY = bestMatch.position.y

            // Calculate accuracy based on the match quality
            // Better match (smaller difference) = better accuracy
            val accuracy = (smallestDifference / 10).coerceIn(0.5f, 5.0f)

            // Create a position object
            val calculatedPosition = Position(posX, posY, positioningViewModel.currentFloor.value)

            // Update the visualization
            binding.floorPlanView.updateUserPosition(calculatedPosition, accuracy, "Fingerprinting")

            // Show details
            val message = "Fingerprint position: (${posX.toInt()}, ${posY.toInt()}), Accuracy: $accuracy"
            showNotification(message)

        } catch (e: Exception) {
            Log.e(TAG, "Error in fingerprinting: ${e.message}")
            showNotification("Fingerprinting error: ${e.message}")
        }
    }

    /**
     * Simulate Sensor Fusion positioning
     */
    private fun simulateSensorFusion(beacons: List<Beacon>) {
        try {
            // For sensor fusion, we'll combine results from multiple methods
            val positions = mutableListOf<Pair<Position, Float>>() // Position and weight

            // 1. Trilateration - if we have 3+ beacons
            if (beacons.size >= 3) {
                val beaconsWithPosition = beacons.filter { it.position != null }.take(3)
                if (beaconsWithPosition.size >= 3) {
                    // Get the first 3 beacons
                    val b1 = beaconsWithPosition[0]
                    val b2 = beaconsWithPosition[1]
                    val b3 = beaconsWithPosition[2]

                    // Get positions and distances
                    val p1 = b1.position!!
                    val p2 = b2.position!!
                    val p3 = b3.position!!

                    val r1 = b1.distance.coerceAtLeast(0.1)
                    val r2 = b2.distance.coerceAtLeast(0.1)
                    val r3 = b3.distance.coerceAtLeast(0.1)

                    // Simplified trilateration calculation
                    val posX = (p1.x + p2.x + p3.x) / 3
                    val posY = (p1.y + p2.y + p3.y) / 3
                    val accuracy = (r1 + r2 + r3) / 3

                    // Create position and add to list with high weight
                    val trilaterationPos = Position(posX, posY, positioningViewModel.currentFloor.value)
                    positions.add(trilaterationPos to 3.0f)
                }
            }

            // 2. Weighted Centroid - always possible with 1+ beacons
            val beaconsWithPosition = beacons.filter { it.position != null }
            if (beaconsWithPosition.isNotEmpty()) {
                // Calculate weighted centroid
                var sumX = 0.0
                var sumY = 0.0
                var sumWeights = 0.0

                beaconsWithPosition.forEach { beacon ->
                    val weight = 1.0 / beacon.distance.coerceAtLeast(0.1)
                    beacon.position?.let {
                        sumX += it.x * weight
                        sumY += it.y * weight
                        sumWeights += weight
                    }
                }

                if (sumWeights > 0) {
                    val posX = sumX / sumWeights
                    val posY = sumY / sumWeights
                    val accuracy = beaconsWithPosition.map { it.distance }.average().toFloat()

                    // Create position and add to list with medium weight
                    val centroidPos = Position(posX, posY, positioningViewModel.currentFloor.value)
                    positions.add(centroidPos to 2.0f)
                }
            }

            // 3. Kalman Filter - apply to previous estimate if available
            if (beaconsWithPosition.isNotEmpty() && kalmanStateX.isInitialized) {
                // Get raw position estimate
                var sumX = 0.0
                var sumY = 0.0
                var sumWeights = 0.0

                beaconsWithPosition.forEach { beacon ->
                    val weight = 1.0 / beacon.distance.coerceAtLeast(0.1)
                    beacon.position?.let {
                        sumX += it.x * weight
                        sumY += it.y * weight
                        sumWeights += weight
                    }
                }

                if (sumWeights > 0) {
                    val rawX = sumX / sumWeights
                    val rawY = sumY / sumWeights
                    val measurementNoise = beaconsWithPosition.map { it.distance }.average().toFloat()

                    // Apply Kalman filter
                    val filteredX = kalmanStateX.update(rawX.toFloat(), measurementNoise)
                    val filteredY = kalmanStateY.update(rawY.toFloat(), measurementNoise)

                    // Create position and add to list with high weight
                    val kalmanPos = Position(filteredX.toDouble(), filteredY.toDouble(), positioningViewModel.currentFloor.value)
                    positions.add(kalmanPos to 3.0f)
                }
            }

            // 4. Fingerprinting - use if database exists
            if (fingerprints.isNotEmpty() && beacons.isNotEmpty()) {
                // Create a measurement from current beacon readings
                val currentReadings = beacons.associate { it.id to it.rssi }

                // Find the best matching fingerprint
                var bestMatch: Fingerprint? = null
                var smallestDifference = Float.MAX_VALUE

                for (fingerprint in fingerprints) {
                    var totalDifference = 0f
                    var matchedSignals = 0

                    for ((beaconId, expectedRssi) in fingerprint.beaconReadings) {
                        val actualRssi = currentReadings[beaconId] ?: continue
                        val difference = abs(actualRssi - expectedRssi)
                        totalDifference += difference
                        matchedSignals++
                    }

                    if (matchedSignals > 0) {
                        val averageDifference = totalDifference / matchedSignals
                        if (averageDifference < smallestDifference) {
                            smallestDifference = averageDifference
                            bestMatch = fingerprint
                        }
                    }
                }

                bestMatch?.let {
                    // Calculate weight based on match quality (lower difference = higher weight)
                    val weight = (50f / (smallestDifference + 1f)).coerceIn(1f, 4f)

                    // Create position and add to list
                    val fingerprintPos = Position(it.position.x, it.position.y, positioningViewModel.currentFloor.value)
                    positions.add(fingerprintPos to weight)
                }
            }

            // Calculate final fused position if we have inputs
            if (positions.isEmpty()) {
                showNotification("No position data available for fusion")
                return
            }

            // Calculate weighted average
            var sumX = 0.0
            var sumY = 0.0
            var sumWeights = 0.0

            positions.forEach { (position, weight) ->
                sumX += position.x * weight
                sumY += position.y * weight
                sumWeights += weight
            }

            val fusedX = sumX / sumWeights
            val fusedY = sumY / sumWeights

            // Calculate accuracy as weighted average of individual accuracies
            // In a real implementation, this would be more complex
            val accuracy = 1.5f // Set a reasonable value for fusion

            // Create final position
            val fusedPosition = Position(fusedX, fusedY, positioningViewModel.currentFloor.value)

            // Update visualization
            binding.floorPlanView.updateUserPosition(fusedPosition, accuracy, "Sensor Fusion")

            // Show details
            val methodsUsed = positions.size
            val message = "Fusion position (${methodsUsed} methods): (${fusedX.toInt()}, ${fusedY.toInt()}), Accuracy: $accuracy"
            showNotification(message)

        } catch (e: Exception) {
            Log.e(TAG, "Error in sensor fusion: ${e.message}")
            showNotification("Sensor fusion error: ${e.message}")
        }
    }

    /**
     * Generate a simulated fingerprint database for testing
     */
    private fun generateFingerprintDatabase() {
        try {
            fingerprints.clear()

            // Create a grid of fingerprints covering the floor plan
            val gridSpacing = 5 // 5 meter spacing between fingerprints
            val mapWidth = 50
            val mapHeight = 50

            for (x in 0..mapWidth step gridSpacing) {
                for (y in 0..mapHeight step gridSpacing) {
                    val position = Position(x.toDouble(), y.toDouble(), positioningViewModel.currentFloor.value)
                    val beaconReadings = mutableMapOf<String, Int>()

                    // Generate expected RSSI readings for all known beacons
                    beaconAdapter.currentList.forEach { beacon ->
                        beacon.position?.let { beaconPos ->
                            // Calculate distance from fingerprint to beacon
                            val dx = beaconPos.x - position.x
                            val dy = beaconPos.y - position.y
                            val distance = Math.sqrt(dx * dx + dy * dy)

                            // Convert distance to expected RSSI using path loss model
                            val txPower = -59 // Reference power at 1m
                            val pathLossExponent = 2.0 // Free space path loss exponent
                            val expectedRssi = (txPower - 10 * pathLossExponent * Math.log10(distance)).toInt()

                            beaconReadings[beacon.id] = expectedRssi
                        }
                    }

                    // Only add fingerprint if it has readings
                    if (beaconReadings.isNotEmpty()) {
                        fingerprints.add(Fingerprint(position, beaconReadings))
                    }
                }
            }

            Log.d(TAG, "Generated ${fingerprints.size} fingerprints")

        } catch (e: Exception) {
            Log.e(TAG, "Error generating fingerprints: ${e.message}")
        }
    }

    /**
     * Simple Kalman filter implementation for 1D tracking
     */
    inner class KalmanFilter {
        private var x = 0f // State estimate
        private var p = 1f // Estimation error covariance
        private var q = 0.01f // Process noise
        private var r = 0.1f // Measurement noise
        private var k = 0f // Kalman gain
        var isInitialized = false
        
        fun setProcessNoise(noise: Float) {
            q = noise
        }

        fun update(measurement: Float, measurementNoise: Float): Float {
            // On first update, just initialize with measurement
            if (!isInitialized) {
                x = measurement
                isInitialized = true
                return x
            }

            // Update measurement noise based on input
            r = measurementNoise

            // Prediction step (state remains the same, error increases)
            p = p + q

            // Update step
            k = p / (p + r) // Calculate Kalman gain
            x = x + k * (measurement - x) // Update estimate
            p = (1 - k) * p // Update error covariance

            return x
        }

        fun reset() {
            x = 0f
            p = 1f
            k = 0f
            isInitialized = false
        }
    }

    /**
     * Data class for fingerprinting
     */
    data class Fingerprint(
        val position: Position,
        val beaconReadings: Map<String, Int> // Beacon ID to RSSI
    )

    override fun onResume() {
        super.onResume()
        // Start scanning if it was on before
        if (isScanning) {
            startBleScanning()
        }
        // Start WiFi scanning
        wifiManager.startScanning()
    }

    override fun onPause() {
        super.onPause()
        // Stop scanning to conserve battery
        stopBleScanning()
        wifiManager.stopScanning()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        wifiManager.stopScanning()
        _binding = null
    }
}