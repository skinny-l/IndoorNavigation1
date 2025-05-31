package com.example.indoornavigation.data.repositories

import android.content.Context
import com.example.indoornavigation.data.models.Position
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Repository for accessing positioning data
 */
class PositioningRepository private constructor(private val context: Context) {
    
    // Current position
    private val _currentPosition = MutableStateFlow<Position?>(null)
    
    // Beacon-based position
    private val _beaconPosition = MutableStateFlow<Position?>(null)
    
    // WiFi-based position
    private val _wifiPosition = MutableStateFlow<Position?>(null)
    
    companion object {
        @Volatile
        private var INSTANCE: PositioningRepository? = null
        
        fun getInstance(context: Context): PositioningRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: PositioningRepository(context.applicationContext).also {
                    INSTANCE = it
                }
            }
        }
    }
    
    /**
     * Get the current position as a StateFlow
     */
    fun getCurrentPositionFlow(): StateFlow<Position?> = _currentPosition.asStateFlow()
    
    /**
     * Set the current position
     */
    fun setCurrentPosition(position: Position) {
        _currentPosition.value = position
    }
    
    /**
     * Get current position (non-Flow)
     */
    fun getCurrentPosition(): Position? = _currentPosition.value
    
    /**
     * Get beacon-based position estimate
     */
    fun getBeaconPosition(): Position? = _beaconPosition.value
    
    /**
     * Set beacon-based position
     */
    fun setBeaconPosition(position: Position) {
        _beaconPosition.value = position
    }
    
    /**
     * Estimate position from WiFi signals
     */
    fun estimatePositionFromWifi(): Position? = _wifiPosition.value
    
    /**
     * Set WiFi-based position
     */
    fun setWifiPosition(position: Position) {
        _wifiPosition.value = position
    }
}