package com.example.indoornavigation.ui.debug.calibration

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.indoornavigation.R

class BeaconListAdapter(
    private val beacons: MutableList<BeaconDevice> = mutableListOf()
) : RecyclerView.Adapter<BeaconListAdapter.BeaconViewHolder>() {

    private var onItemClickListener: ((BeaconDevice) -> Unit)? = null

    class BeaconViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val beaconNameTextView: TextView = itemView.findViewById(R.id.beaconNameTextView)
        val beaconIdTextView: TextView = itemView.findViewById(R.id.beaconIdTextView)
        val rssiValueTextView: TextView = itemView.findViewById(R.id.rssiValueTextView)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BeaconViewHolder {
        val itemView = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_beacon_calibration, parent, false)
        return BeaconViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: BeaconViewHolder, position: Int) {
        val currentBeacon = beacons[position]
        
        holder.beaconNameTextView.text = currentBeacon.name
        holder.beaconIdTextView.text = currentBeacon.id
        holder.rssiValueTextView.text = "RSSI: ${currentBeacon.rssi} dBm"
        
        holder.itemView.setOnClickListener {
            onItemClickListener?.invoke(currentBeacon)
        }
    }

    override fun getItemCount() = beacons.size

    fun setOnItemClickListener(listener: (BeaconDevice) -> Unit) {
        onItemClickListener = listener
    }

    fun updateBeacons(newBeacons: List<BeaconDevice>) {
        beacons.clear()
        beacons.addAll(newBeacons)
        notifyDataSetChanged()
    }

    fun updateRssi(beaconId: String, newRssi: Int) {
        val index = beacons.indexOfFirst { it.id == beaconId }
        if (index != -1) {
            beacons[index].rssi = newRssi
            notifyItemChanged(index)
        }
    }
}