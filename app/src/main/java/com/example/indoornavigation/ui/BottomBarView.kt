package com.example.indoornavigation.ui

import com.example.indoornavigation.ui.ContextAwareUIController.BottomBarTab

/**
 * Interface for bottom bar UI components
 */
interface BottomBarView {
    /**
     * Set visibility of the bottom bar
     */
    fun setVisible(visible: Boolean)
    
    /**
     * Set the currently selected tab
     */
    fun setCurrentTab(tab: BottomBarTab)
}