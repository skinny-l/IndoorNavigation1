package com.example.indoornavigation.ui.map

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.indoornavigation.data.models.RecentLocation
import com.example.indoornavigation.databinding.ItemRecentLocationBinding

class RecentLocationsAdapter(
    private val locations: List<RecentLocation>,
    private val onItemClick: (RecentLocation) -> Unit
) : RecyclerView.Adapter<RecentLocationsAdapter.ViewHolder>() {

    class ViewHolder(private val binding: ItemRecentLocationBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(location: RecentLocation, onItemClick: (RecentLocation) -> Unit) {
            binding.tvLocationName.text = location.name
            binding.root.setOnClickListener { onItemClick(location) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemRecentLocationBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(locations[position], onItemClick)
    }

    override fun getItemCount() = locations.size
}

data class RecentLocation(
    val id: String,
    val name: String,
    val x: Float,
    val y: Float,
    val floor: Int
)