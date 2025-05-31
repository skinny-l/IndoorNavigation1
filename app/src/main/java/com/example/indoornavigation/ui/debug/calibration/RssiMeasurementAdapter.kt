package com.example.indoornavigation.ui.debug.calibration

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.indoornavigation.R

class RssiMeasurementAdapter(
    private val measurements: MutableList<RssiMeasurement>
) : RecyclerView.Adapter<RssiMeasurementAdapter.MeasurementViewHolder>() {

    private var onDeleteClickListener: ((Int) -> Unit)? = null

    class MeasurementViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val distanceTextView: TextView = itemView.findViewById(R.id.distanceTextView)
        val rssiTextView: TextView = itemView.findViewById(R.id.rssiTextView)
        val deleteButton: ImageButton = itemView.findViewById(R.id.deleteButton)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MeasurementViewHolder {
        val itemView = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_rssi_measurement, parent, false)
        return MeasurementViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: MeasurementViewHolder, position: Int) {
        val currentMeasurement = measurements[position]
        
        // Format the distance text
        val distanceText = if (currentMeasurement.distance == 1.0) {
            "1 meter (reference)"
        } else {
            "${currentMeasurement.distance} meters"
        }
        
        holder.distanceTextView.text = distanceText
        holder.rssiTextView.text = "RSSI: ${currentMeasurement.rssi} dBm"
        
        // Set delete button click listener
        holder.deleteButton.setOnClickListener {
            val adapterPosition = holder.adapterPosition
            if (adapterPosition != RecyclerView.NO_POSITION) {
                onDeleteClickListener?.invoke(adapterPosition)
            }
        }
    }

    override fun getItemCount() = measurements.size

    fun setOnDeleteClickListener(listener: (Int) -> Unit) {
        onDeleteClickListener = listener
    }
}