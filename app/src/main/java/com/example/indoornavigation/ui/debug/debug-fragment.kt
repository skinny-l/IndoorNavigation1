package com.example.indoornavigation.ui.debug

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.indoornavigation.data.models.Position
import com.example.indoornavigation.databinding.FragmentDebugBinding
import com.example.indoornavigation.ui.debug.adapter.BeaconAdapter
import com.example.indoornavigation.viewmodel.PositioningViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class DebugFragment : Fragment() {

    private var _binding: FragmentDebugBinding? = null
    private val binding get() = _binding!!
    
    private val positioningViewModel: PositioningViewModel by activityViewModels()
    private lateinit var beaconAdapter: BeaconAdapter
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDebugBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // Setup beacon recycler view
        setupBeaconList()
        
        // Setup debug controls
        setupDebugControls()
        
        // Observe position updates
        observePositionUpdates()
    }
    
    private fun setupBeaconList() {
        beaconAdapter = BeaconAdapter()
        
        binding.beaconRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = beaconAdapter
        }
        
        // Observe beacon updates
        viewLifecycleOwner.lifecycleScope.launch {
            positioningViewModel.nearbyBeacons.collectLatest { beacons ->
                beaconAdapter.submitList(beacons)
                binding.beaconCountText.text = "Beacons Detected: ${beacons.size}"
            }
        }
    }
    
    private fun setupDebugControls() {
        // Toggle debug mode
        binding.debugSwitch.isChecked = positioningViewModel.isDebugMode.value
        binding.debugSwitch.setOnCheckedChangeListener { _, isChecked ->
            positioningViewModel.toggleDebugMode()
        }
        
        // Quick path test button
        binding.quickPathTestButton.setOnClickListener {
            performQuickPathTest()
        }
        
        // Beacon scanning control
        binding.scanningSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                positioningViewModel.startScanning()
            } else {
                positioningViewModel.stopScanning()
            }
        }
    }
    
    private fun observePositionUpdates() {
        viewLifecycleOwner.lifecycleScope.launch {
            positioningViewModel.currentPosition.collectLatest { position ->
                updatePositionInfo(position)
            }
        }
    }
    
    private fun updatePositionInfo(position: Position?) {
        position?.let {
            binding.positionInfoText.text = "Position: (${it.x.toInt()}, ${it.y.toInt()}) Floor: ${it.floor}"
        } ?: run {
            binding.positionInfoText.text = "Position: Unknown"
        }
    }
    
    private fun performQuickPathTest() {
        // Create mock start and end positions
        val startPosition = Position(10.0, 15.0, 1)
        val endPosition = Position(40.0, 40.0, 1)
        
        // Display test information
        binding.pathTestInfoText.text = "Test Path: (${startPosition.x.toInt()}, ${startPosition.y.toInt()}) to (${endPosition.x.toInt()}, ${endPosition.y.toInt()})"
        
        // In a real implementation, this would calculate and display a path
        // using the PathfindingEngine
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}