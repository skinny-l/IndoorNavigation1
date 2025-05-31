package com.example.indoornavigation.ui.debug.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.indoornavigation.R
import com.example.indoornavigation.data.models.Beacon
import com.example.indoornavigation.databinding.ItemBeaconBinding
import java.text.DecimalFormat

class BeaconAdapter(
    private val listener: BeaconActionListener
) : ListAdapter<Beacon, BeaconAdapter.BeaconViewHolder>(BeaconDiffCallback()) {

    interface BeaconActionListener {
        fun onConnectBeacon(beaconId: String)
        fun onDisconnectBeacon(beaconId: String)
    }

    private val distanceFormat = DecimalFormat("#.## m")
    private val connectedBeacons = mutableSetOf<String>()
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BeaconViewHolder {
        val binding = ItemBeaconBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return BeaconViewHolder(binding)
    }
    
    override fun onBindViewHolder(holder: BeaconViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
    
    fun updateConnectedState(beaconId: String, isConnected: Boolean) {
        if (isConnected) {
            connectedBeacons.add(beaconId)
        } else {
            connectedBeacons.remove(beaconId)
        }
        
        // Update UI for this beacon if it's visible
        currentList.forEachIndexed { index, beacon ->
            if (beacon.id == beaconId) {
                notifyItemChanged(index)
            }
        }
    }
    
    inner class BeaconViewHolder(
        private val binding: ItemBeaconBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        
        fun bind(beacon: Beacon) {
            binding.beaconNameText.text = beacon.name
            binding.beaconIdText.text = beacon.id
            binding.rssiText.text = "RSSI: ${beacon.rssi} dBm"
            binding.distanceText.text = "Distance: ${distanceFormat.format(beacon.distance)}"
            
            val isConnected = connectedBeacons.contains(beacon.id)
            
            // Update connect button
            binding.connectButton.text = if (isConnected) "Disconnect" else "Connect"
            binding.connectButton.backgroundTintList = ContextCompat.getColorStateList(
                binding.root.context,
                if (isConnected) R.color.red_warning else R.color.purple_500
            )
            
            // Add click listener
            binding.connectButton.setOnClickListener {
                if (isConnected) {
                    listener.onDisconnectBeacon(beacon.id)
                } else {
                    listener.onConnectBeacon(beacon.id)
                }
            }
            
            // Make background highlighted if connected
            binding.beaconContent.background = if (isConnected) {
                ContextCompat.getDrawable(binding.root.context, R.drawable.connected_beacon_bg)
            } else {
                null
            }
        }
    }
    
    class BeaconDiffCallback : DiffUtil.ItemCallback<Beacon>() {
        override fun areItemsTheSame(oldItem: Beacon, newItem: Beacon): Boolean {
            return oldItem.id == newItem.id
        }
        
        override fun areContentsTheSame(oldItem: Beacon, newItem: Beacon): Boolean {
            return oldItem == newItem
        }
    }
}