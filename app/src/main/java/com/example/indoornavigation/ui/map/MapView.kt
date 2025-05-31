package com.example.indoornavigation.ui.map

import com.example.indoornavigation.data.models.Floor

/**
 * Interface for map view components that support floor transitions
 */
interface MapView {
    /**
     * Set the current floor to display
     */
    fun setFloor(floor: Floor)
    
    /**
     * Set opacity for a floor's display
     */
    fun setFloorOpacity(floor: Floor, opacity: Float)
    
    /**
     * Set elevation offset for a floor's display (for animation)
     */
    fun setFloorElevation(floor: Floor, elevationOffset: Float)
    
    /**
     * Set map mode (indoor, outdoor, hybrid)
     */
    fun setMapMode(mode: MapMode)
    
    /**
     * Set visibility of floor controls
     */
    fun setFloorControlsVisible(visible: Boolean)
    
    /**
     * Set visibility of compass
     */
    fun setCompassVisible(visible: Boolean)
    
    /**
     * Set visibility of zoom controls
     */
    fun setZoomControlsVisible(visible: Boolean)
    
    /**
     * Enum for map modes
     */
    enum class MapMode {
        INDOOR,
        OUTDOOR,
        HYBRID
    }
}