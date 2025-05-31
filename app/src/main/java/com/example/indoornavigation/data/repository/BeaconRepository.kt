package com.example.indoornavigation.data.repository

import android.content.Context
import com.example.indoornavigation.data.models.ManagedBeacon
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/**
 * Repository for managing BLE beacons
 */
class BeaconRepository(private val context: Context) {
    
    private val sharedPreferences = context.getSharedPreferences("beacons", Context.MODE_PRIVATE)
    private val gson = Gson()
    
    // StateFlow for beacons
    private val _beacons = MutableStateFlow<List<ManagedBeacon>>(emptyList())
    val beacons: StateFlow<List<ManagedBeacon>> = _beacons.asStateFlow()
    
    init {
        // Load beacons from preferences on initialization
        loadBeacons()
    }
    
    /**
     * Load all saved beacons
     */
    fun loadBeacons() {
        val beaconsJson = sharedPreferences.getString("beacons", null)
        val beaconsList = if (beaconsJson != null) {
            val type = object : TypeToken<List<ManagedBeacon>>() {}.type
            gson.fromJson(beaconsJson, type) as? List<ManagedBeacon> ?: emptyList()
        } else {
            emptyList()
        }
        _beacons.value = beaconsList
    }
    
    /**
     * Save all beacons
     */
    private fun saveAllBeacons(beacons: List<ManagedBeacon>) {
        val json = gson.toJson(beacons)
        sharedPreferences.edit().putString("beacons", json).apply()
        _beacons.value = beacons
    }
    
    /**
     * Add or update a beacon
     */
    fun saveBeacon(beacon: ManagedBeacon) {
        val currentBeacons = _beacons.value.toMutableList()
        
        // Check if beacon with this ID already exists
        val existingIndex = currentBeacons.indexOfFirst { it.id == beacon.id }
        
        if (existingIndex >= 0) {
            // Update existing beacon
            currentBeacons[existingIndex] = beacon
        } else {
            // Add new beacon
            currentBeacons.add(beacon)
        }
        
        saveAllBeacons(currentBeacons)
    }
    
    /**
     * Delete a beacon by ID
     */
    fun deleteBeacon(id: String) {
        val currentBeacons = _beacons.value.toMutableList()
        currentBeacons.removeAll { it.id == id }
        saveAllBeacons(currentBeacons)
    }
    
    /**
     * Update beacon with latest RSSI and distance
     */
    fun updateBeaconSignal(id: String, rssi: Int, distance: Double) {
        val currentBeacons = _beacons.value.toMutableList()
        val index = currentBeacons.indexOfFirst { it.id == id }
        
        if (index >= 0) {
            val beacon = currentBeacons[index]
            currentBeacons[index] = beacon.copy(
                lastRssi = rssi,
                lastDistance = distance,
                lastSeen = System.currentTimeMillis()
            )
            
            // Only update state flow to avoid too many preference writes
            _beacons.value = currentBeacons
        }
    }
    
    /**
     * Get all beacons for a specific floor
     */
    fun getBeaconsForFloor(floor: Int): List<ManagedBeacon> {
        return _beacons.value.filter { it.floor == floor }
    }
    
    /**
     * Get recently seen beacons (seen in the last 10 seconds)
     */
    fun getRecentBeacons(maxAgeMs: Long = 10000): List<ManagedBeacon> {
        val now = System.currentTimeMillis()
        return _beacons.value.filter { now - it.lastSeen < maxAgeMs }
    }
    
    /**
     * Generate a new unique ID for beacons
     */
    fun generateBeaconId(): String {
        return UUID.randomUUID().toString()
    }
}