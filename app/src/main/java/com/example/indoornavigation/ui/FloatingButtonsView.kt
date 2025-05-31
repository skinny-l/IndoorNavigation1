package com.example.indoornavigation.ui

/**
 * Interface for floating buttons UI components
 */
interface FloatingButtonsView {
    /**
     * Set visibility of the location button
     */
    fun setLocationButtonVisible(visible: Boolean)
    
    /**
     * Set visibility of the navigate button
     */
    fun setNavigateButtonVisible(visible: Boolean)
    
    /**
     * Set visibility of the directions button
     */
    fun setDirectionsButtonVisible(visible: Boolean)
    
    /**
     * Set visibility of the stop navigation button
     */
    fun setStopNavigationButtonVisible(visible: Boolean)
    
    /**
     * Hide all buttons
     */
    fun setAllButtonsVisible(visible: Boolean)
}