package com.example.indoornavigation.ui

/**
 * Interface for top bar UI components
 */
interface TopBarView {
    /**
     * Set the title displayed in the top bar
     */
    fun setTitle(title: String)
    
    /**
     * Set visibility of the back button
     */
    fun setBackButtonVisible(visible: Boolean)
    
    /**
     * Set visibility of the search button
     */
    fun setSearchButtonVisible(visible: Boolean)
    
    /**
     * Set visibility of the menu button
     */
    fun setMenuButtonVisible(visible: Boolean)
    
    /**
     * Enable/disable search mode in the top bar
     */
    fun setSearchMode(enabled: Boolean)
}