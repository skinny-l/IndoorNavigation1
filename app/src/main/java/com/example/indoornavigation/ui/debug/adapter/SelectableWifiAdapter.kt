package com.example.indoornavigation.ui.debug.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.indoornavigation.R
import com.example.indoornavigation.data.models.WifiAccessPoint

/**
 * Adapter for selectable WiFi access points in the trilateration test UI
 */
class SelectableWifiAdapter(
    private val listener: WifiSelectListener
) : ListAdapter<WifiAccessPoint, SelectableWifiAdapter.ViewHolder>(WifiDiffCallback()) {

    // Keep track of selected items
    private val selectedWifiAPs = mutableSetOf<String>()

    interface WifiSelectListener {
        fun onWifiSelected(ssid: String, isSelected: Boolean)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_selectable_wifi, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val wifiAP = getItem(position)
        holder.bind(wifiAP, selectedWifiAPs.contains(wifiAP.ssid))
    }

    fun clearSelections() {
        val previousSelected = selectedWifiAPs.toSet()
        selectedWifiAPs.clear()
        
        // Notify listener for each previously selected item
        previousSelected.forEach { ssid ->
            listener.onWifiSelected(ssid, false)
        }
        
        notifyDataSetChanged()
    }

    fun selectAll() {
        val allWifiAPs = currentList.map { it.ssid }.toSet()
        val newSelections = allWifiAPs - selectedWifiAPs
        
        selectedWifiAPs.addAll(allWifiAPs)
        
        // Notify listener for each newly selected item
        newSelections.forEach { ssid ->
            listener.onWifiSelected(ssid, true)
        }
        
        notifyDataSetChanged()
    }

    override fun getCurrentList(): List<WifiAccessPoint> {
        return super.getCurrentList()
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvSsid: TextView = itemView.findViewById(R.id.tvWifiSsid)
        private val tvFrequency: TextView = itemView.findViewById(R.id.tvFrequency)
        private val tvRssi: TextView = itemView.findViewById(R.id.tvWifiRssi)
        private val checkbox: CheckBox = itemView.findViewById(R.id.checkboxWifi)

        fun bind(wifiAP: WifiAccessPoint, isSelected: Boolean) {
            tvSsid.text = wifiAP.ssid
            tvFrequency.text = "${wifiAP.frequency / 1000}GHz"
            tvRssi.text = "RSSI: ${wifiAP.rssi} dBm"
            
            // Set checkbox state
            checkbox.isChecked = isSelected
            
            // Click listeners
            checkbox.setOnClickListener {
                val isChecked = checkbox.isChecked
                if (isChecked) {
                    selectedWifiAPs.add(wifiAP.ssid)
                } else {
                    selectedWifiAPs.remove(wifiAP.ssid)
                }
                listener.onWifiSelected(wifiAP.ssid, isChecked)
            }
            
            // Make the entire item clickable to toggle selection
            itemView.setOnClickListener {
                checkbox.isChecked = !checkbox.isChecked
                val isChecked = checkbox.isChecked
                if (isChecked) {
                    selectedWifiAPs.add(wifiAP.ssid)
                } else {
                    selectedWifiAPs.remove(wifiAP.ssid)
                }
                listener.onWifiSelected(wifiAP.ssid, isChecked)
            }
        }
    }

    class WifiDiffCallback : DiffUtil.ItemCallback<WifiAccessPoint>() {
        override fun areItemsTheSame(oldItem: WifiAccessPoint, newItem: WifiAccessPoint): Boolean {
            return oldItem.ssid == newItem.ssid
        }

        override fun areContentsTheSame(oldItem: WifiAccessPoint, newItem: WifiAccessPoint): Boolean {
            return oldItem.rssi == newItem.rssi &&
                   oldItem.frequency == newItem.frequency
        }
    }
}