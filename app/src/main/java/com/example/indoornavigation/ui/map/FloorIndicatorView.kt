package com.example.indoornavigation.ui.map

/**
 * Interface for floor indicator UI components
 */
interface FloorIndicatorView {
    /**
     * Set the current floor in the UI indicator
     */
    fun setCurrentFloor(floorLevel: Int)
    
    /**
     * Set the available floors to display in the indicator
     */
    fun setAvailableFloors(floors: List<Int>)
    
    /**
     * Set visibility of the floor indicator
     */
    fun setVisible(visible: Boolean)
}