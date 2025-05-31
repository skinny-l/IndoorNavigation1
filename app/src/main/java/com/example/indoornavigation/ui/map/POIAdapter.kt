package com.example.indoornavigation.ui.map

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.indoornavigation.R
import com.example.indoornavigation.data.models.EnhancedPOI

/**
 * Adapter for displaying Points of Interest in a RecyclerView
 */
class POIAdapter(
    private val onNavigateClick: (EnhancedPOI) -> Unit
) : ListAdapter<EnhancedPOI, POIAdapter.POIViewHolder>(POIDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): POIViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_poi, parent, false)
        return POIViewHolder(view)
    }

    override fun onBindViewHolder(holder: POIViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class POIViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val iconImageView: ImageView = itemView.findViewById(R.id.iv_poi_icon)
        private val nameTextView: TextView = itemView.findViewById(R.id.tv_poi_name)
        private val descriptionTextView: TextView = itemView.findViewById(R.id.tv_poi_description)
        private val floorTextView: TextView = itemView.findViewById(R.id.tv_poi_floor)
        private val navigateButton: ImageButton = itemView.findViewById(R.id.btn_navigate)

        fun bind(poi: EnhancedPOI) {
            // Set category icon
            iconImageView.setImageResource(poi.getIconForCategory())
            
            // Set text fields
            nameTextView.text = poi.name
            descriptionTextView.text = poi.description
            
            // Format floor name
            val floorText = when {
                poi.floorId == 0 -> "Ground Floor"
                poi.floorId > 0 -> "Floor ${poi.floorId}"
                else -> "Basement ${-poi.floorId}"
            }
            floorTextView.text = floorText
            
            // Set navigation click listener
            navigateButton.setOnClickListener {
                onNavigateClick(poi)
            }
            
            // Make the whole item clickable for navigation
            itemView.setOnClickListener {
                onNavigateClick(poi)
            }
        }
    }
}

/**
 * DiffUtil callback for POI items
 */
class POIDiffCallback : DiffUtil.ItemCallback<EnhancedPOI>() {
    override fun areItemsTheSame(oldItem: EnhancedPOI, newItem: EnhancedPOI): Boolean {
        return oldItem.id == newItem.id
    }

    override fun areContentsTheSame(oldItem: EnhancedPOI, newItem: EnhancedPOI): Boolean {
        return oldItem == newItem
    }
}