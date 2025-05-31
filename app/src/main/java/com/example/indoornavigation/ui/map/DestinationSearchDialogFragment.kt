package com.example.indoornavigation.ui.map

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ListView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.example.indoornavigation.R
import com.example.indoornavigation.data.models.PointOfInterest

/**
 * A simple dialog fragment for destination search
 */
class DestinationSearchDialogFragment : DialogFragment() {
    
    interface OnDestinationSelectedListener {
        fun onDestinationSelected(destination: PointOfInterest)
    }
    
    private var listener: OnDestinationSelectedListener? = null
    
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val destinations = getDestinations()
        val destinationNames = destinations.map { it.name }.toTypedArray()
        
        return AlertDialog.Builder(requireContext())
            .setTitle("Select Destination")
            .setItems(destinationNames) { _, which ->
                listener?.onDestinationSelected(destinations[which])
            }
            .setNegativeButton("Cancel", null)
            .create()
    }
    
    /**
     * Set the destination selection listener
     */
    fun setOnDestinationSelectedListener(listener: OnDestinationSelectedListener) {
        this.listener = listener
    }
    
    /**
     * Get list of destinations
     */
    private fun getDestinations(): List<PointOfInterest> {
        return listOf(
            PointOfInterest("entrance", "Main Entrance", "Main building entrance", 
                com.example.indoornavigation.data.models.Position(35.0, 55.0, 1), "entrance"),
            PointOfInterest("dewan_alghazali", "Dewan Al-Ghazali", "Conference hall", 
                com.example.indoornavigation.data.models.Position(17.0, 45.0, 1), "hall"),
            PointOfInterest("th1", "Tutorial Hall 1", "Small lecture room", 
                com.example.indoornavigation.data.models.Position(10.0, 45.0, 1), "classroom"),
            PointOfInterest("th2", "Tutorial Hall 2", "Medium lecture room", 
                com.example.indoornavigation.data.models.Position(20.0, 45.0, 1), "classroom"),
            PointOfInterest("th3", "Tutorial Hall 3", "Large lecture room", 
                com.example.indoornavigation.data.models.Position(30.0, 45.0, 1), "classroom"),
            PointOfInterest("th4", "Theatre Hall 4", "Main auditorium", 
                com.example.indoornavigation.data.models.Position(75.0, 15.0, 1), "auditorium"),
            PointOfInterest("th5", "Theatre Hall 5", "Secondary auditorium", 
                com.example.indoornavigation.data.models.Position(75.0, 8.0, 1), "auditorium"),
            PointOfInterest("laman_najib", "Laman Najib", "Central courtyard", 
                com.example.indoornavigation.data.models.Position(30.0, 25.0, 1), "outdoor"),
            PointOfInterest("cafe", "Cafeteria", "Food and refreshments", 
                com.example.indoornavigation.data.models.Position(65.0, 30.0, 1), "food"),
            PointOfInterest("pengurusan_akademik", "Academic Management", "Academic affairs office", 
                com.example.indoornavigation.data.models.Position(12.0, 5.0, 1), "office"),
            PointOfInterest("pengurusan_pentadbiran", "Admin Office", "Administrative services", 
                com.example.indoornavigation.data.models.Position(12.0, 15.0, 1), "office")
        )
    }
}