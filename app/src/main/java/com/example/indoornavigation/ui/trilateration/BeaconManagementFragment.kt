package com.example.indoornavigation.ui.trilateration

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.indoornavigation.data.models.ManagedBeacon
import com.example.indoornavigation.databinding.DialogAddBeaconBinding
import com.example.indoornavigation.databinding.FragmentBeaconManagementBinding
import com.example.indoornavigation.viewmodel.TrilaterationViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Fragment for managing beacons
 */
class BeaconManagementFragment : Fragment() {

    private var _binding: FragmentBeaconManagementBinding? = null
    private val binding get() = _binding!!
    
    private val viewModel: TrilaterationViewModel by activityViewModels()
    private lateinit var beaconAdapter: BeaconAdapter
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentBeaconManagementBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupRecyclerView()
        setupListeners()
        observeBeacons()
    }
    
    private fun setupRecyclerView() {
        beaconAdapter = BeaconAdapter(
            onEdit = { beacon -> showAddEditBeaconDialog(beacon) },
            onDelete = { beacon -> confirmDeleteBeacon(beacon) }
        )
        
        binding.beaconsRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = beaconAdapter
        }
    }
    
    private fun setupListeners() {
        // Add beacon button
        binding.addBeaconButton.setOnClickListener {
            showAddEditBeaconDialog()
        }
        
        // Floor buttons
        binding.floorDownButton.setOnClickListener {
            val currentFloor = viewModel.currentFloor.value
            if (currentFloor > 0) {
                viewModel.setCurrentFloor(currentFloor - 1)
            }
        }
        
        binding.floorUpButton.setOnClickListener {
            val currentFloor = viewModel.currentFloor.value
            viewModel.setCurrentFloor(currentFloor + 1)
        }
    }
    
    private fun observeBeacons() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                // Observe current floor
                launch {
                    viewModel.currentFloor.collectLatest { floor ->
                        binding.floorTextView.text = "Floor $floor"
                        filterBeaconsByFloor(floor)
                    }
                }
                
                // Observe all beacons
                launch {
                    viewModel.managedBeacons.collectLatest { beacons ->
                        filterBeaconsByFloor(viewModel.currentFloor.value)
                    }
                }
            }
        }
    }
    
    private fun filterBeaconsByFloor(floor: Int) {
        val beacons = viewModel.managedBeacons.value.filter { it.floor == floor }
        beaconAdapter.submitList(beacons)
        
        // Update empty state
        if (beacons.isEmpty()) {
            binding.emptyStateView.visibility = View.VISIBLE
        } else {
            binding.emptyStateView.visibility = View.GONE
        }
    }
    
    private fun showAddEditBeaconDialog(beacon: ManagedBeacon? = null) {
        val dialogBinding = DialogAddBeaconBinding.inflate(layoutInflater)
        val isEdit = beacon != null
        
        // Pre-fill fields if editing existing beacon
        if (isEdit) {
            dialogBinding.uuidEditText.setText(beacon!!.uuid)
            dialogBinding.nameEditText.setText(beacon.name)
            dialogBinding.xCoordEditText.setText(beacon.x.toString())
            dialogBinding.yCoordEditText.setText(beacon.y.toString())
            dialogBinding.floorEditText.setText(beacon.floor.toString())
        } else {
            // Default floor to current floor
            dialogBinding.floorEditText.setText(viewModel.currentFloor.value.toString())
        }
        
        val dialogTitle = if (isEdit) "Edit Beacon" else "Add New Beacon"
        
        AlertDialog.Builder(requireContext())
            .setTitle(dialogTitle)
            .setView(dialogBinding.root)
            .setPositiveButton("Save") { _, _ ->
                // Get values from dialog
                val uuid = dialogBinding.uuidEditText.text.toString().trim()
                val name = dialogBinding.nameEditText.text.toString().trim()
                val xStr = dialogBinding.xCoordEditText.text.toString().trim()
                val yStr = dialogBinding.yCoordEditText.text.toString().trim()
                val floorStr = dialogBinding.floorEditText.text.toString().trim()
                
                // Validate input
                if (uuid.isEmpty()) {
                    Toast.makeText(context, "UUID is required", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                
                try {
                    val x = xStr.toDouble()
                    val y = yStr.toDouble()
                    val floor = floorStr.toInt()
                    
                    if (isEdit) {
                        // Update existing beacon
                        val updatedBeacon = beacon!!.copy(
                            uuid = uuid,
                            name = name,
                            x = x,
                            y = y,
                            floor = floor
                        )
                        viewModel.saveBeacon(updatedBeacon)
                    } else {
                        // Create new beacon
                        val newBeacon = viewModel.createBeacon(
                            uuid = uuid,
                            name = name,
                            x = x,
                            y = y,
                            floor = floor
                        )
                        viewModel.saveBeacon(newBeacon)
                        
                        // Switch to the floor where the beacon was added
                        if (floor != viewModel.currentFloor.value) {
                            viewModel.setCurrentFloor(floor)
                        }
                    }
                    
                    Toast.makeText(context, "Beacon saved", Toast.LENGTH_SHORT).show()
                } catch (e: NumberFormatException) {
                    Toast.makeText(context, "Invalid coordinates or floor", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun confirmDeleteBeacon(beacon: ManagedBeacon) {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete Beacon")
            .setMessage("Are you sure you want to delete this beacon? This action cannot be undone.")
            .setPositiveButton("Delete") { _, _ ->
                viewModel.deleteBeacon(beacon.id)
                Toast.makeText(context, "Beacon deleted", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}