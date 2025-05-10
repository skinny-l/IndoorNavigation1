package com.example.indoornavigation.ui.debug.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.indoornavigation.data.models.Beacon
import com.example.indoornavigation.databinding.ItemBeaconBinding
import java.text.DecimalFormat

class BeaconAdapter : ListAdapter<Beacon, BeaconAdapter.BeaconViewHolder>(BeaconDiffCallback()) {

    private val distanceFormat = DecimalFormat("#.## m")
    
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
    
    inner class BeaconViewHolder(
        private val binding: ItemBeaconBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        
        fun bind(beacon: Beacon) {
            binding.beaconNameText.text = beacon.name
            binding.beaconIdText.text = beacon.id
            binding.rssiText.text = "RSSI: ${beacon.rssi} dBm"
            binding.distanceText.text = "Distance: ${distanceFormat.format(beacon.distance)}"
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