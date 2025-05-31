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
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.NavHostFragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.indoornavigation.R
import com.example.indoornavigation.databinding.FragmentBeaconSelectionBinding

class BeaconSelectionFragment : Fragment() {

    private var _binding: FragmentBeaconSelectionBinding? = null
    private val binding get() = _binding!!

    private val beaconListAdapter = BeaconListAdapter()
    private val beaconDevices = mutableListOf<BeaconDevice>()

    private lateinit var bluetoothAdapter: BluetoothAdapter
    private lateinit var bluetoothLeScanner: BluetoothLeScanner
    private val handler = Handler(Looper.getMainLooper())
    private var scanning = false
    private val SCAN_PERIOD: Long = 10000 // 10 seconds

    private val requiredPermissions = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.BLUETOOTH_SCAN,
        Manifest.permission.BLUETOOTH_CONNECT
    )

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            startBleScan()
        } else {
            Toast.makeText(
                requireContext(),
                "Bluetooth and location permissions are required",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentBeaconSelectionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupBluetooth()
        setupRecyclerView()
        setupClickListeners()
    }

    private fun setupBluetooth() {
        val bluetoothManager = requireContext().getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter
        bluetoothLeScanner = bluetoothAdapter.bluetoothLeScanner
    }

    private fun setupRecyclerView() {
        binding.beaconRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.beaconRecyclerView.adapter = beaconListAdapter

        beaconListAdapter.setOnItemClickListener { beacon ->
            // Navigate to calibration fragment with selected beacon
            val action = BeaconSelectionFragmentDirections.actionBeaconSelectionToCalibration(
                beaconId = beacon.id,
                beaconName = beacon.name
            )
            findNavController().navigate(action)
        }
    }

    private fun setupClickListeners() {
        binding.scanBeaconsButton.setOnClickListener {
            if (!scanning) {
                checkPermissionsAndStartScan()
            } else {
                stopBleScan()
            }
        }
    }

    private fun checkPermissionsAndStartScan() {
        val permissionsToRequest = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(requireContext(), it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()

        if (permissionsToRequest.isEmpty()) {
            startBleScan()
        } else {
            requestPermissionLauncher.launch(permissionsToRequest)
        }
    }

    private fun startBleScan() {
        if (!bluetoothAdapter.isEnabled) {
            Toast.makeText(requireContext(), "Bluetooth is not enabled", Toast.LENGTH_SHORT).show()
            return
        }

        // Clear previous results
        beaconDevices.clear()
        beaconListAdapter.updateBeacons(beaconDevices)

        // Update UI
        binding.scanBeaconsButton.text = "Stop Scan"
        binding.scanProgressBar.visibility = View.VISIBLE
        scanning = true

        // Settings for BLE scan
        val scanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        // Start scanning
        handler.postDelayed({
            stopBleScan()
        }, SCAN_PERIOD)

        try {
            bluetoothLeScanner.startScan(null, scanSettings, scanCallback)
        } catch (e: SecurityException) {
            Toast.makeText(requireContext(), "Bluetooth permission denied", Toast.LENGTH_SHORT).show()
            binding.scanBeaconsButton.text = "Scan for Beacons"
            binding.scanProgressBar.visibility = View.GONE
            scanning = false
        }
    }

    private fun stopBleScan() {
        if (scanning) {
            scanning = false
            try {
                bluetoothLeScanner.stopScan(scanCallback)
            } catch (e: SecurityException) {
                // Permission denied, just continue
            }
            binding.scanBeaconsButton.text = "Scan for Beacons"
            binding.scanProgressBar.visibility = View.GONE
        }
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            super.onScanResult(callbackType, result)
            
            try {
                val deviceAddress = result.device.address
                val deviceName = result.device.name ?: "Unknown Beacon"
                val rssi = result.rssi

                // Check if we already have this device
                val existingDeviceIndex = beaconDevices.indexOfFirst { it.id == deviceAddress }
                
                if (existingDeviceIndex >= 0) {
                    // Update existing device RSSI
                    beaconDevices[existingDeviceIndex].rssi = rssi
                    handler.post {
                        beaconListAdapter.updateRssi(deviceAddress, rssi)
                    }
                } else {
                    // Add new device
                    val newBeacon = BeaconDevice(
                        id = deviceAddress,
                        name = deviceName,
                        rssi = rssi
                    )
                    beaconDevices.add(newBeacon)
                    handler.post {
                        beaconListAdapter.updateBeacons(beaconDevices)
                    }
                }
            } catch (e: SecurityException) {
                handler.post {
                    Toast.makeText(requireContext(), "Permission error: ${e.message}", Toast.LENGTH_SHORT).show()
                    stopBleScan()
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
                stopBleScan()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        stopBleScan()
        _binding = null
    }
}