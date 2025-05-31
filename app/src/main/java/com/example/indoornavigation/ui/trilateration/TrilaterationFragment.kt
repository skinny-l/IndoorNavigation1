package com.example.indoornavigation.ui.trilateration

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.util.Log
import androidx.core.content.ContextCompat
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.example.indoornavigation.R
import com.example.indoornavigation.databinding.FragmentTrilaterationBinding
import com.example.indoornavigation.viewmodel.TrilaterationViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Fragment for displaying the trilateration map view
 */
class TrilaterationFragment : Fragment() {

    private var _binding: FragmentTrilaterationBinding? = null
    private val binding get() = _binding!!

    private val viewModel: TrilaterationViewModel by activityViewModels()
    
    // Permission request launcher
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.entries.all { it.value }) {
            // All permissions granted
            startPositioning()
        } else {
            // Some permissions denied
            Toast.makeText(
                requireContext(),
                "Bluetooth and Location permissions are required for indoor positioning",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTrilaterationBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupFloorPlan()
        setupControls()
        observeViewModel()

        // Check permissions and start positioning if granted
        checkPermissions()
    }

    private fun setupFloorPlan() {
        // Load a sample floor plan bitmap
        // In a real app, this would come from assets or a server
        try {
            // Convert vector drawable to bitmap
            val drawable = ContextCompat.getDrawable(requireContext(), R.drawable.sample_floor_plan)
            val bitmap = Bitmap.createBitmap(600, 400, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            drawable?.setBounds(0, 0, canvas.width, canvas.height)
            drawable?.draw(canvas)
            
            binding.floorPlanView.floorPlan = bitmap
        } catch (e: Exception) {
            Log.e("TrilaterationFragment", "Error loading floor plan", e)
        }
        
        // Set the scaling ratio for the map (1 meter = X pixels)
        binding.floorPlanView.mapToScreenRatio = 20f
    }

    private fun setupControls() {
        // Floor navigation buttons
        binding.floorUpButton.setOnClickListener {
            val currentFloor = viewModel.currentFloor.value
            viewModel.setCurrentFloor(currentFloor + 1)
        }

        binding.floorDownButton.setOnClickListener {
            val currentFloor = viewModel.currentFloor.value
            if (currentFloor > 0) {
                viewModel.setCurrentFloor(currentFloor - 1)
            }
        }

        // Center on position button
        binding.centerPositionFab.setOnClickListener {
            binding.floorPlanView.centerOnUser()
        }

        // Demo mode toggle
        binding.demoModeSwitch.setOnCheckedChangeListener { _, isChecked ->
            viewModel.toggleDemoMode(isChecked)
        }

        // Manage beacons button
        binding.beaconManagementButton.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.nav_host_fragment, BeaconManagementFragment())
                .addToBackStack(null)
                .commit()
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                // Observe current floor
                launch {
                    viewModel.currentFloor.collectLatest { floor ->
                        binding.floorText.text = "Floor: $floor"
                    }
                }

                // Observe beacons
                launch {
                    viewModel.managedBeacons.collectLatest { beacons ->
                        val floorBeacons = beacons.filter { it.floor == viewModel.currentFloor.value }
                        binding.floorPlanView.beacons = floorBeacons
                        updateBeaconCountText(floorBeacons.size, floorBeacons.count { it.lastSeen > 0 })
                    }
                }

                // Observe trilateration status
                launch {
                    viewModel.trilaterationStatus.collectLatest { status ->
                        binding.floorPlanView.userPosition = status.position
                        binding.floorPlanView.positionAccuracy = status.accuracy

                        // Update position text
                        status.position?.let { position ->
                            binding.positionText.text = "Position: (${String.format("%.2f", position.x)}, ${String.format("%.2f", position.y)})"
                        } ?: run {
                            binding.positionText.text = "Position: Not available"
                        }

                        // Update accuracy text
                        if (status.position != null) {
                            binding.accuracyText.text = "Accuracy: ${String.format("%.2f", status.accuracy)} meters"
                        } else {
                            binding.accuracyText.text = "Accuracy: Not available"
                        }

                        // Update beacon count
                        updateBeaconCountText(
                            viewModel.managedBeacons.value.count { it.floor == viewModel.currentFloor.value },
                            status.activeBeacons
                        )
                    }
                }

                // Observe demo mode
                launch {
                    viewModel.demoMode.collectLatest { demoMode ->
                        binding.demoModeSwitch.isChecked = demoMode
                    }
                }
            }
        }
    }

    private fun updateBeaconCountText(totalBeacons: Int, activeBeacons: Int) {
        binding.beaconsCountText.text = "Active Beacons: $activeBeacons/$totalBeacons"
    }

    private fun checkPermissions() {
        val requiredPermissions = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
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

        val missingPermissions = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(requireContext(), it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isEmpty()) {
            // All permissions already granted
            startPositioning()
        } else {
            // Request missing permissions
            requestPermissionLauncher.launch(missingPermissions.toTypedArray())
        }
    }

    private fun startPositioning() {
        viewModel.startPositioning()
    }

    override fun onPause() {
        super.onPause()
        viewModel.stopPositioning()
    }

    override fun onResume() {
        super.onResume()
        if (hasAllPermissions()) {
            startPositioning()
        }
    }

    private fun hasAllPermissions(): Boolean {
        val requiredPermissions = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
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

        return requiredPermissions.all {
            ContextCompat.checkSelfPermission(requireContext(), it) == PackageManager.PERMISSION_GRANTED
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}