package com.example.indoornavigation.ui.debug.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.indoornavigation.data.models.WifiAccessPoint
import com.example.indoornavigation.databinding.ItemAccessPointBinding
import java.text.DecimalFormat

class AccessPointAdapter : ListAdapter<WifiAccessPoint, AccessPointAdapter.AccessPointViewHolder>(AccessPointDiffCallback()) {

    private val distanceFormat = DecimalFormat("#.## m")
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AccessPointViewHolder {
        val binding = ItemAccessPointBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return AccessPointViewHolder(binding)
    }
    
    override fun onBindViewHolder(holder: AccessPointViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
    
    inner class AccessPointViewHolder(
        private val binding: ItemAccessPointBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        
        fun bind(accessPoint: WifiAccessPoint) {
            binding.ssidText.text = accessPoint.ssid
            binding.bssidText.text = accessPoint.id
            binding.rssiText.text = "RSSI: ${accessPoint.rssi} dBm"
            binding.frequencyText.text = "${accessPoint.frequency} MHz"
            binding.distanceText.text = "Distance: ${distanceFormat.format(accessPoint.distance)}"
            binding.root.setOnClickListener(null)
            binding.root.setBackgroundResource(android.R.color.transparent)
            binding.root.alpha = 1.0f
        }
    }
    
    class AccessPointDiffCallback : DiffUtil.ItemCallback<WifiAccessPoint>() {
        override fun areItemsTheSame(oldItem: WifiAccessPoint, newItem: WifiAccessPoint): Boolean {
            return oldItem.id == newItem.id
        }
        
        override fun areContentsTheSame(oldItem: WifiAccessPoint, newItem: WifiAccessPoint): Boolean {
            return oldItem == newItem
        }
    }
}