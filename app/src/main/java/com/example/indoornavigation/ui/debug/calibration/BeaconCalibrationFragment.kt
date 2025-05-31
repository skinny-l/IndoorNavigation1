package com.example.indoornavigation.ui.debug.calibration

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.indoornavigation.R
import com.example.indoornavigation.databinding.FragmentBeaconCalibrationBinding
import com.google.firebase.firestore.FirebaseFirestore
import kotlin.math.log10

class BeaconCalibrationFragment : Fragment() {

    private var _binding: FragmentBeaconCalibrationBinding? = null
    private val binding get() = _binding!!
    private val args by navArgs<BeaconCalibrationFragmentArgs>()

    private lateinit var bluetoothAdapter: BluetoothAdapter
    private lateinit var bluetoothLeScanner: BluetoothLeScanner
    private val handler = Handler(Looper.getMainLooper())
    private var scanning = false

    private var selectedBeaconId: String = ""
    private var selectedBeaconName: String = ""
    private var currentRssi: Int = 0
    
    private val rssiMeasurements = mutableListOf<RssiMeasurement>()
    private lateinit var measurementsAdapter: RssiMeasurementAdapter
    
    private var selectedDistance: Double = 1.0
    private var calculatedPathLossExponent: Double? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentBeaconCalibrationBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        selectedBeaconId = args.beaconId
        selectedBeaconName = args.beaconName
        
        setupBluetooth()
        setupUI()
        setupDistanceSpinner()
        setupMeasurementsRecyclerView()
        setupClickListeners()
        
        // Start scanning for the selected beacon
        startBeaconScan()
    }

    private fun setupBluetooth() {
        val bluetoothManager = requireContext().getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter
        bluetoothLeScanner = bluetoothAdapter.bluetoothLeScanner
    }

    private fun setupUI() {
        binding.selectedBeaconText.text = "Selected Beacon: $selectedBeaconName ($selectedBeaconId)"
    }
    
    private fun setupDistanceSpinner() {
        val distanceOptions = resources.getStringArray(R.array.distance_options)
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, distanceOptions)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        
        binding.distanceSpinner.adapter = adapter
        binding.distanceSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selectedItem = distanceOptions[position]
                selectedDistance = when (selectedItem) {
                    "1 meter" -> 1.0
                    "2 meters" -> 2.0
                    "3 meters" -> 3.0
                    "5 meters" -> 5.0
                    "7 meters" -> 7.0
                    "10 meters" -> 10.0
                    else -> 1.0
                }
                
                // Update instruction text
                binding.instructionText.text = "Walk to $selectedItem from the beacon and press 'Record RSSI'"
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                // Do nothing
            }
        }
    }
    
    private fun setupMeasurementsRecyclerView() {
        measurementsAdapter = RssiMeasurementAdapter(rssiMeasurements)
        binding.measurementsRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.measurementsRecyclerView.adapter = measurementsAdapter
        
        // Set click listener for delete measurements
        measurementsAdapter.setOnDeleteClickListener { position ->
            rssiMeasurements.removeAt(position)
            measurementsAdapter.notifyItemRemoved(position)
            // Reset calculated value when measurements change
            resetCalculation()
        }
    }
    
    private fun setupClickListeners() {
        // Record RSSI button
        binding.recordRssiButton.setOnClickListener {
            if (currentRssi != 0) {
                val measurement = RssiMeasurement(selectedDistance, currentRssi)
                
                // Check if we already have a measurement at this distance
                val existingIndex = rssiMeasurements.indexOfFirst { it.distance == selectedDistance }
                if (existingIndex != -1) {
                    // Replace existing measurement
                    rssiMeasurements[existingIndex] = measurement
                    measurementsAdapter.notifyItemChanged(existingIndex)
                    Toast.makeText(requireContext(), "Updated RSSI at $selectedDistance meters", Toast.LENGTH_SHORT).show()
                } else {
                    // Add new measurement
                    rssiMeasurements.add(measurement)
                    measurementsAdapter.notifyItemInserted(rssiMeasurements.size - 1)
                    Toast.makeText(requireContext(), "Recorded RSSI at $selectedDistance meters", Toast.LENGTH_SHORT).show()
                }
                
                // Reset calculated value when measurements change
                resetCalculation()
            } else {
                Toast.makeText(requireContext(), "No RSSI value detected", Toast.LENGTH_SHORT).show()
            }
        }
        
        // Calculate n button
        binding.calculateNButton.setOnClickListener {
            if (rssiMeasurements.size >= 2) {
                calculatePathLossExponent()
            } else {
                Toast.makeText(requireContext(), "Need at least 2 measurements at different distances", Toast.LENGTH_SHORT).show()
            }
        }
        
        // Save calibration button
        binding.saveCalibrationButton.setOnClickListener {
            calculatedPathLossExponent?.let { n ->
                saveCalibrationToFirestore(n)
            } ?: run {
                Toast.makeText(requireContext(), "Calculate path loss exponent first", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun resetCalculation() {
        calculatedPathLossExponent = null
        binding.estimatedNValue.text = "Not calculated"
        binding.saveCalibrationButton.isEnabled = false
    }
    
    private fun startBeaconScan() {
        if (!hasRequiredPermissions()) {
            Toast.makeText(requireContext(), "Missing required permissions", Toast.LENGTH_SHORT).show()
            return
        }

        if (!bluetoothAdapter.isEnabled) {
            Toast.makeText(requireContext(), "Bluetooth is not enabled", Toast.LENGTH_SHORT).show()
            return
        }

        // Scan settings for BLE
        val scanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        try {
            scanning = true
            bluetoothLeScanner.startScan(null, scanSettings, scanCallback)
        } catch (e: SecurityException) {
            Toast.makeText(requireContext(), "Permission denied: ${e.message}", Toast.LENGTH_SHORT).show()
            scanning = false
        }
    }

    private fun stopBeaconScan() {
        if (scanning) {
            try {
                scanning = false
                bluetoothLeScanner.stopScan(scanCallback)
            } catch (e: SecurityException) {
                // Permission denied
                Toast.makeText(requireContext(), "Permission denied: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            super.onScanResult(callbackType, result)
            
            try {
                val deviceAddress = result.device.address
                
                // Only process the selected beacon
                if (deviceAddress == selectedBeaconId) {
                    currentRssi = result.rssi
                    handler.post {
                        binding.currentRssiValue.text = "$currentRssi dBm"
                    }
                }
            } catch (e: SecurityException) {
                handler.post {
                    Toast.makeText(requireContext(), "Permission error: ${e.message}", Toast.LENGTH_SHORT).show()
                    stopBeaconScan()
                }
            }
        }

        override fun onScanFailed(errorCode: Int) {
            super.onScanFailed(errorCode)
            handler.post {
                Toast.makeText(
                    requireContext(),
                    "Scan failed with error code: $errorCode",
                    Toast.LENGTH_SHORT
                ).show()
                stopBeaconScan()
            }
        }
    }
    
    private fun hasRequiredPermissions(): Boolean {
        return ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED &&
        ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.BLUETOOTH_SCAN
        ) == PackageManager.PERMISSION_GRANTED
    }
    
    private fun calculatePathLossExponent() {
        // Need at least one measurement at 1 meter for reference
        val reference = rssiMeasurements.find { it.distance == 1.0 }
        if (reference == null) {
            Toast.makeText(requireContext(), "Need a measurement at 1 meter for reference", Toast.LENGTH_SHORT).show()
            return
        }
        
        val rssiAt1m = reference.rssi
        
        // Calculate n for each non-reference measurement
        val nonReferenceMeasurements = rssiMeasurements.filter { it.distance != 1.0 }
        
        if (nonReferenceMeasurements.isEmpty()) {
            Toast.makeText(requireContext(), "Need measurements at distances other than 1 meter", Toast.LENGTH_SHORT).show()
            return
        }
        
        val nValues = nonReferenceMeasurements.map { measurement ->
            calculatePathLossExponent(rssiAt1m.toDouble(), measurement.distance, measurement.rssi.toDouble())
        }
        
        // Calculate average n
        calculatedPathLossExponent = nValues.average()
        
        // Update UI
        binding.estimatedNValue.text = String.format("%.2f", calculatedPathLossExponent)
        binding.saveCalibrationButton.isEnabled = true
        
        Toast.makeText(requireContext(), "Path loss exponent calculated", Toast.LENGTH_SHORT).show()
    }
    
    private fun calculatePathLossExponent(rssi1m: Double, distance: Double, rssiAtDistance: Double): Double {
        return (rssi1m - rssiAtDistance) / (10 * log10(distance))
    }
    
    private fun saveCalibrationToFirestore(pathLossExponent: Double) {
        val firestore = FirebaseFirestore.getInstance()
        
        val calibratedData = hashMapOf(
            "beaconId" to selectedBeaconId,
            "beaconName" to selectedBeaconName,
            "calibratedN" to pathLossExponent,
            "rssiAt1m" to rssiMeasurements.find { it.distance == 1.0 }?.rssi,
            "timestamp" to System.currentTimeMillis()
        )
        
        firestore.collection("beaconCalibration")
            .document(selectedBeaconId)
            .set(calibratedData)
            .addOnSuccessListener { 
                Toast.makeText(requireContext(), "Calibration data saved!", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(requireContext(), "Error saving calibration data: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        stopBeaconScan()
        _binding = null
    }
}