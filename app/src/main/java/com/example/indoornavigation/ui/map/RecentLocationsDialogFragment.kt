package com.example.indoornavigation.ui.map

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.indoornavigation.R
import com.example.indoornavigation.data.models.Position
import com.example.indoornavigation.viewmodel.RecentLocationsViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class RecentLocationsDialogFragment : DialogFragment() {

    private val recentLocationsViewModel: RecentLocationsViewModel by activityViewModels()
    private lateinit var adapter: RecentLocationsAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.dialog_recent_locations, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val recyclerView = view.findViewById<RecyclerView>(R.id.rvRecentLocations)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())

        // Initialize adapter with empty list
        adapter = RecentLocationsAdapter(emptyList()) { location ->
            // Handle location selection
            val position = Position(location.x.toDouble(), location.y.toDouble(), location.floor)
            
            // Use the MapFragment to navigate to this location
            (parentFragment as? MapFragment)?.navigateToPosition(position)
            
            // Dismiss the dialog
            dismiss()
        }
        recyclerView.adapter = adapter

        // Observe recent locations using StateFlow
        viewLifecycleOwner.lifecycleScope.launch {
            recentLocationsViewModel.recentLocations.collectLatest { locations ->
                adapter = RecentLocationsAdapter(locations) { location ->
                    val position =
                        Position(location.x.toDouble(), location.y.toDouble(), location.floor)
                    (parentFragment as? MapFragment)?.navigateToPosition(position)
                    dismiss()
                }
                recyclerView.adapter = adapter
            }
        }
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }
}
