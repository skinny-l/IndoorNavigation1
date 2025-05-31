package com.example.indoornavigation.ui.map

import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.indoornavigation.data.models.Position
import com.example.indoornavigation.data.repository.FloorPlanRepository
import com.example.indoornavigation.databinding.FragmentCorridorTracerBinding
import com.example.indoornavigation.utils.MapOverlayTool
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

class CorridorTracerFragment : Fragment() {

    private var _binding: FragmentCorridorTracerBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var floorPlanRepository: FloorPlanRepository
    private lateinit var mapOverlayTool: MapOverlayTool
    
    private var isTracing = false
    private val tracedPoints = mutableListOf<Position>()
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCorridorTracerBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // Initialize repositories and tools
        floorPlanRepository = FloorPlanRepository()
        mapOverlayTool = MapOverlayTool(requireContext())
        
        // Setup floor plan
        setupFloorPlan()
        
        // Setup UI controls
        setupControls()
        
        // Setup touch listener for tracing
        setupTouchListener()
    }
    
    private fun setupFloorPlan() {
        // Get ground floor plan
        val floorPlan = floorPlanRepository.getFloorPlanByLevel(0)
        
        floorPlan?.let {
            // Set real-world dimensions
            binding.corridorFloorPlanView.setRealWorldDimensions(
                it.width.toFloat(),
                it.height.toFloat()
            )
            
            // Display floor plan
            binding.corridorFloorPlanView.setFloorPlan(0)
            
            // Load POI markers
            displayPoiMarkers(it.pois)
        }
        
        // Load existing overlay if available
        viewLifecycleOwner.lifecycleScope.launch {
            if (mapOverlayTool.loadOverlay()) {
                // Display overlay nodes
                binding.corridorFloorPlanView.setNavigationNodes(mapOverlayTool.getOverlayNodes())
                binding.corridorFloorPlanView.setDebugMode(true)
                
                Toast.makeText(
                    requireContext(),
                    "Overlay loaded successfully",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
    
    private fun displayPoiMarkers(pois: List<com.example.indoornavigation.data.models.PointOfInterest>) {
        binding.corridorFloorPlanView.clearMarkers()
        
        // Create markers for POIs
        for (poi in pois) {
            val drawable = when (poi.category) {
                "entrance" -> androidx.core.content.ContextCompat.getDrawable(requireContext(), com.example.indoornavigation.R.drawable.ic_start_marker)
                "elevator", "stairs" -> androidx.core.content.ContextCompat.getDrawable(requireContext(), com.example.indoornavigation.R.drawable.ic_end_marker)
                else -> androidx.core.content.ContextCompat.getDrawable(requireContext(), com.example.indoornavigation.R.drawable.ic_location_marker)
            }
            
            val bitmap = (drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
            
            val marker = FloorPlanView.Marker(
                position = poi.position,
                bitmap = bitmap,
                title = poi.name,
                floor = poi.position.floor
            )
            
            binding.corridorFloorPlanView.addMarker(marker)
        }
    }
    
    private fun setupControls() {
        // Start corridor tracing button
        binding.startCorridorBtn.setOnClickListener {
            val corridorName = binding.corridorNameEdit.text.toString().trim()
            
            if (corridorName.isNotEmpty()) {
                startTracing(corridorName)
            } else {
                Toast.makeText(
                    requireContext(),
                    "Please enter a corridor name",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
        
        // Finish corridor button
        binding.finishCorridorBtn.setOnClickListener {
            finishTracing()
        }
        
        // Clear button
        binding.clearBtn.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Clear Overlay")
                .setMessage("Are you sure you want to clear all traced corridors?")
                .setPositiveButton("Clear") { _, _ ->
                    mapOverlayTool.clearOverlay()
                    binding.corridorFloorPlanView.setNavigationNodes(emptyList())
                    binding.corridorFloorPlanView.invalidate()
                    Toast.makeText(requireContext(), "Overlay cleared", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
        
        // Save button
        binding.saveBtn.setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch {
                if (mapOverlayTool.saveOverlay()) {
                    Toast.makeText(
                        requireContext(),
                        "Overlay saved successfully",
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    Toast.makeText(
                        requireContext(),
                        "Failed to save overlay",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
        
        // Load button
        binding.loadBtn.setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch {
                if (mapOverlayTool.loadOverlay()) {
                    // Display overlay nodes
                    binding.corridorFloorPlanView.setNavigationNodes(mapOverlayTool.getOverlayNodes())
                    binding.corridorFloorPlanView.setDebugMode(true)
                    
                    Toast.makeText(
                        requireContext(),
                        "Overlay loaded successfully",
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    Toast.makeText(
                        requireContext(),
                        "No saved overlay found",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
        
        // Generate code button
        binding.generateCodeBtn.setOnClickListener {
            val code = mapOverlayTool.getNodeCode()
            
            // Show dialog with the generated code
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Generated Node Code")
                .setMessage("Copy this code into the getNavigationNodes() method in PositioningViewModel:\n\n$code")
                .setPositiveButton("OK", null)
                .show()
            
            // Also copy to clipboard
            val clipboard = requireActivity().getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val clip = android.content.ClipData.newPlainText("Navigation Node Code", code)
            clipboard.setPrimaryClip(clip)
            
            Toast.makeText(
                requireContext(),
                "Code copied to clipboard",
                Toast.LENGTH_SHORT
            ).show()
        }
    }
    
    private fun setupTouchListener() {
        binding.corridorFloorPlanView.setOnTouchListener { _, event ->
            if (isTracing && event.action == MotionEvent.ACTION_DOWN) {
                val coords = binding.corridorFloorPlanView.screenToFloorPlanCoordinates(
                    event.x,
                    event.y
                )
                
                if (coords != null) {
                    // Create position from coordinates
                    val position = Position(coords.first, coords.second, 0)
                    
                    // Add to overlay
                    mapOverlayTool.addCorridorPoint(position)
                    
                    // Update the view with the current nodes
                    binding.corridorFloorPlanView.setNavigationNodes(mapOverlayTool.getOverlayNodes())
                    
                    // Add to traced points for display
                    tracedPoints.add(position)
                    
                    // Show point coordinates
                    binding.traceStatusText.text = "Added: (${coords.first.toInt()}, ${coords.second.toInt()})"
                }
            }
            
            false // Don't consume the event
        }
    }
    
    private fun startTracing(corridorName: String) {
        isTracing = true
        mapOverlayTool.startCorridor(corridorName)
        tracedPoints.clear()
        
        // Update UI
        binding.startCorridorBtn.isEnabled = false
        binding.finishCorridorBtn.isEnabled = true
        binding.traceStatusText.text = "Tracing: $corridorName"
        binding.corridorFloorPlanView.setDebugMode(true)
    }
    
    private fun finishTracing() {
        isTracing = false
        mapOverlayTool.finishCorridor()
        
        // Update UI
        binding.startCorridorBtn.isEnabled = true
        binding.finishCorridorBtn.isEnabled = false
        binding.traceStatusText.text = "Trace completed"
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}