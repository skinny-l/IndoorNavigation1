package com.example.indoornavigation.ui.trilateration

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.indoornavigation.data.models.ManagedBeacon
import com.example.indoornavigation.databinding.ItemBeaconBinding

/**
 * Adapter for displaying beacons in a RecyclerView
 */
class BeaconAdapter(
    private val onEdit: (ManagedBeacon) -> Unit,
    private val onDelete: (ManagedBeacon) -> Unit
) : ListAdapter<ManagedBeacon, BeaconAdapter.BeaconViewHolder>(BeaconDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BeaconViewHolder {
        val binding = ItemBeaconBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        
        // Create edit and delete buttons and signal indicator programmatically
        // since they're not in the standard item_beacon.xml layout
        val rootLayout = binding.root.findViewById<ViewGroup>(android.R.id.content)
        
        // Add these views programmatically
        val editButton = Button(parent.context).apply {
            id = View.generateViewId()
            text = "Edit"
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        
        val deleteButton = Button(parent.context).apply {
            id = View.generateViewId()
            text = "Delete"
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        
        val signalIndicator = ImageView(parent.context).apply {
            id = View.generateViewId()
            layoutParams = ViewGroup.LayoutParams(
                24, // width in dp
                24  // height in dp
            )
            setImageResource(android.R.drawable.ic_notification_overlay)
        }
        
        return BeaconViewHolder(binding, editButton, deleteButton, signalIndicator)
    }

    override fun onBindViewHolder(holder: BeaconViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class BeaconViewHolder(
        private val binding: ItemBeaconBinding,
        private val editButton: Button,
        private val deleteButton: Button,
        private val signalIndicator: ImageView
    ) :
        RecyclerView.ViewHolder(binding.root) {

        init {
            // Set up button click listeners
            editButton.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onEdit(getItem(position))
                }
            }

            deleteButton.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onDelete(getItem(position))
                }
            }
        }

        fun bind(beacon: ManagedBeacon) {
            // Set beacon name or use UUID if name is empty
            binding.beaconNameText.text = if (beacon.name.isNotEmpty()) {
                beacon.name
            } else {
                "Beacon ${beacon.uuid.takeLast(6)}"
            }

            // Set UUID
            binding.beaconIdText.text = beacon.uuid

            // Set location info
            binding.distanceText.text = "Pos: (${beacon.x}, ${beacon.y}), Floor: ${beacon.floor}"

            // Set signal info if available
            if (beacon.lastSeen > 0) {
                val timeSinceLastSeen = System.currentTimeMillis() - beacon.lastSeen
                val isRecent = timeSinceLastSeen < 10000 // 10 seconds

                binding.rssiText.text = if (isRecent) {
                    "RSSI: ${beacon.lastRssi} dBm, Distance: ${String.format("%.2f", beacon.lastDistance)}m"
                } else {
                    "Last seen: ${timeSinceLastSeen / 1000}s ago"
                }

                // Update signal indicator color based on strength
                val signalColor = when {
                    beacon.lastRssi > -65 -> Color.GREEN
                    beacon.lastRssi > -80 -> Color.YELLOW
                    else -> Color.RED
                }
                signalIndicator.setColorFilter(signalColor)
            } else {
                binding.rssiText.text = "No signal detected yet"
                signalIndicator.setColorFilter(Color.GRAY)
            }
        }
    }

    /**
     * DiffUtil callback for efficient updates
     */
    class BeaconDiffCallback : DiffUtil.ItemCallback<ManagedBeacon>() {
        override fun areItemsTheSame(oldItem: ManagedBeacon, newItem: ManagedBeacon): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: ManagedBeacon, newItem: ManagedBeacon): Boolean {
            return oldItem == newItem
        }
    }
}