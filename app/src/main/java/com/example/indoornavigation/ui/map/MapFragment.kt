package com.example.indoornavigation.ui.map

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.example.indoornavigation.data.models.PointOfInterest
import com.example.indoornavigation.data.models.Position
import com.example.indoornavigation.databinding.FragmentMapBinding
import com.example.indoornavigation.viewmodel.NavigationViewModel
import com.example.indoornavigation.viewmodel.PositioningViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MapFragment : Fragment(), DestinationSearchDialogFragment.OnDestinationSelectedListener {

    private var _binding: FragmentMapBinding? = null
    private val binding get() = _binding!!

    private val positioningViewModel: PositioningViewModel by activityViewModels()
    private val navigationViewModel: NavigationViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMapBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupUI()
        observeViewModels()
    }

    private fun setupUI() {
        // Setup floor plan
        binding.floorPlanView.setFloorPlan(0)

        // Setup floor controls
        binding.floorUpButton.setOnClickListener {
            // Handle floor up button click
        }

        binding.floorDownButton.setOnClickListener {
            // Handle floor down button click
        }

        // Setup user tracking
        binding.trackUserSwitch.setOnCheckedChangeListener { _, isChecked ->
            // Handle user tracking switch change
        }

        binding.centerOnUserFab.setOnClickListener {
            // Center on user position
            binding.floorPlanView.centerOnUser()
        }
        
        // Setup search button
        binding.searchButton.setOnClickListener {
            showDestinationSearch()
        }
    }

    private fun observeViewModels() {
        viewLifecycleOwner.lifecycleScope.launch {
            positioningViewModel.currentPosition.collectLatest { position ->
                position?.let {
                    binding.floorPlanView.setUserPosition(it)
                } ?: run {
                    showPositioningUnavailableMessage()
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    fun navigateToPosition(position: Position, destinationId: String? = null) {
        val currentPosition = positioningViewModel.currentPosition.value
        if (currentPosition != null) {
            val path = navigationViewModel.calculatePath(currentPosition, position)
            if (path != null) {
                if (destinationId != null) {
                    navigationViewModel.startNavigation(destinationId)
                }
                try {
                    binding.floorPlanView.setRoute(path.waypoints ?: emptyList())
                } catch (e: Exception) {
                    Toast.makeText(
                        requireContext(),
                        "Route calculated, but can't display on map",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                
                // Show navigation UI
                showNavigationBottomSheet()
                
                // Center map on user
                binding.floorPlanView.centerOnUser()
            } else {
                Toast.makeText(
                    requireContext(),
                    "Could not calculate path to destination",
                    Toast.LENGTH_SHORT
                ).show()
            }
        } else {
            Toast.makeText(
                requireContext(),
                "Current position unknown",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun showNavigationBottomSheet() {
        Toast.makeText(
            requireContext(),
            "Navigation started",
            Toast.LENGTH_SHORT
        ).show()
    }
    
    /**
     * Show destination search dialog
     */
    private fun showDestinationSearch() {
        val dialog = DestinationSearchDialogFragment()
        dialog.setOnDestinationSelectedListener(this)
        dialog.show(childFragmentManager, "destination_search")
    }

    override fun onDestinationSelected(destination: PointOfInterest) {
        navigateToPosition(destination.position, destination.id)
    }

    private fun showPositioningUnavailableMessage() {
        Toast.makeText(
            requireContext(),
            "Positioning signal lost. Please move to an area with better coverage.",
            Toast.LENGTH_LONG
        ).show()
    }
}