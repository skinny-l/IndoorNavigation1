package com.example.indoornavigation.ui.debug.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.indoornavigation.R
import com.example.indoornavigation.data.models.Beacon

/**
 * Adapter for beacons with selection capability for trilateration test
 */
class SelectableBeaconAdapter(
    private val listener: BeaconSelectListener
) : ListAdapter<Beacon, SelectableBeaconAdapter.ViewHolder>(BeaconDiffCallback()) {

    // Keep track of selected items
    private val selectedBeacons = mutableSetOf<String>()

    interface BeaconSelectListener {
        fun onBeaconSelected(beaconId: String, isSelected: Boolean)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_selectable_beacon, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val beacon = getItem(position)
        holder.bind(beacon, selectedBeacons.contains(beacon.id))
    }

    fun clearSelections() {
        val previousSelected = selectedBeacons.toSet()
        selectedBeacons.clear()
        
        // Notify listener for each previously selected item
        previousSelected.forEach { beaconId ->
            listener.onBeaconSelected(beaconId, false)
        }
        
        notifyDataSetChanged()
    }

    fun selectAll() {
        val allBeacons = currentList.map { it.id }.toSet()
        val newSelections = allBeacons - selectedBeacons
        
        selectedBeacons.addAll(allBeacons)
        
        // Notify listener for each newly selected item
        newSelections.forEach { beaconId ->
            listener.onBeaconSelected(beaconId, true)
        }
        
        notifyDataSetChanged()
    }

    override fun getCurrentList(): List<Beacon> {
        return super.getCurrentList()
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvId: TextView = itemView.findViewById(R.id.tvBeaconId)
        private val tvDistance: TextView = itemView.findViewById(R.id.tvDistance)
        private val tvRssi: TextView = itemView.findViewById(R.id.tvRssi)
        private val tvSignal: TextView = itemView.findViewById(R.id.tvSignalStrength)
        private val checkbox: CheckBox = itemView.findViewById(R.id.checkboxBeacon)

        fun bind(beacon: Beacon, isSelected: Boolean) {
            tvId.text = beacon.id
            tvDistance.text = "Distance: ${String.format("%.1f", beacon.distance)}m"
            tvRssi.text = "RSSI: ${beacon.rssi} dBm"
            
            val signalText = when {
                beacon.rssi > -70 -> "strong"
                beacon.rssi > -85 -> "medium"
                else -> "weak"
            }
            tvSignal.text = signalText
            
            // Set color based on signal strength
            val signalColor = when {
                beacon.rssi > -70 -> R.color.purple_selected
                beacon.rssi > -85 -> R.color.yellow_signal
                else -> R.color.red_signal
            }
            tvRssi.setTextColor(ContextCompat.getColor(itemView.context, signalColor))
            tvSignal.setTextColor(ContextCompat.getColor(itemView.context, signalColor))
            
            // Set checkbox state
            checkbox.isChecked = isSelected
            
            // Click listeners
            checkbox.setOnClickListener {
                val isChecked = checkbox.isChecked
                if (isChecked) {
                    selectedBeacons.add(beacon.id)
                } else {
                    selectedBeacons.remove(beacon.id)
                }
                listener.onBeaconSelected(beacon.id, isChecked)
            }
            
            // Make the entire item clickable to toggle selection
            itemView.setOnClickListener {
                checkbox.isChecked = !checkbox.isChecked
                val isChecked = checkbox.isChecked
                if (isChecked) {
                    selectedBeacons.add(beacon.id)
                } else {
                    selectedBeacons.remove(beacon.id)
                }
                listener.onBeaconSelected(beacon.id, isChecked)
            }
        }
    }

    class BeaconDiffCallback : DiffUtil.ItemCallback<Beacon>() {
        override fun areItemsTheSame(oldItem: Beacon, newItem: Beacon): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Beacon, newItem: Beacon): Boolean {
            return oldItem.rssi == newItem.rssi &&
                   oldItem.distance == newItem.distance
        }
    }
}