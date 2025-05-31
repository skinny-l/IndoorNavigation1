package com.example.indoornavigation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.indoornavigation.data.models.ManagedBeacon
import com.example.indoornavigation.data.models.Position
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class IndoorNavigationViewModel : ViewModel() {
    
    // Beacons managed by the user for positioning
    private val _managedBeacons = MutableStateFlow<List<ManagedBeacon>>(emptyList())
    val managedBeacons: StateFlow<List<ManagedBeacon>> = _managedBeacons
    
    // Current user position
    private val _userPosition = MutableStateFlow<Position?>(null)
    val userPosition: StateFlow<Position?> = _userPosition
    
    // Current positioning accuracy
    private val _accuracy = MutableStateFlow(0.0)
    val accuracy: StateFlow<Double> = _accuracy
    
    init {
        // Load beacons from storage/repository in a real app
        loadMockData()
    }
    
    // For testing purposes
    private fun loadMockData() {
        _managedBeacons.value = listOf(
            ManagedBeacon(
                id = "1",
                uuid = "e2c56db5-dffb-48d2-b060-d0f5a71096e0",
                x = 10.0,
                y = 5.0,
                floor = 0,
                name = "Entrance Beacon"
            ),
            ManagedBeacon(
                id = "2",
                uuid = "e2c56db5-dffb-48d2-b060-d0f5a71096e1",
                x = 25.0,
                y = 5.0,
                floor = 0,
                name = "Corridor Beacon"
            ),
            ManagedBeacon(
                id = "3",
                uuid = "e2c56db5-dffb-48d2-b060-d0f5a71096e2",
                x = 25.0,
                y = 15.0,
                floor = 0,
                name = "Meeting Room Beacon"
            )
        )
        
        // Set mock position
        _userPosition.value = Position(15.0, 10.0, 0)
        _accuracy.value = 2.5
    }
    
    /**
     * Add a new beacon to the managed beacons list
     */
    fun addBeacon(beacon: ManagedBeacon) {
        viewModelScope.launch {
            val currentBeacons = _managedBeacons.value.toMutableList()
            currentBeacons.add(beacon)
            _managedBeacons.value = currentBeacons
            
            // In a real app, save to repository/database
        }
    }
    
    /**
     * Delete a beacon from the managed beacons list
     */
    fun deleteBeacon(beaconId: String) {
        viewModelScope.launch {
            val currentBeacons = _managedBeacons.value.toMutableList()
            currentBeacons.removeAll { it.id == beaconId }
            _managedBeacons.value = currentBeacons
            
            // In a real app, delete from repository/database
        }
    }
    
    /**
     * Update a beacon's position or other properties
     */
    fun updateBeacon(beacon: ManagedBeacon) {
        viewModelScope.launch {
            val currentBeacons = _managedBeacons.value.toMutableList()
            val index = currentBeacons.indexOfFirst { it.id == beacon.id }
            if (index >= 0) {
                currentBeacons[index] = beacon
                _managedBeacons.value = currentBeacons
                
                // In a real app, update in repository/database
            }
        }
    }
}