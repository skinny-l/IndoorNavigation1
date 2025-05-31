package com.example.indoornavigation.ui

import com.example.indoornavigation.data.models.Position
import com.example.indoornavigation.ui.map.MapView
import com.example.indoornavigation.utils.EventBus
import com.example.indoornavigation.utils.SettingsManager

/**
 * Controller that manages UI changes based on navigation context
 */
class ContextAwareUIController(
    private val navigationView: MapView,
    private val topBar: TopBarView,
    private val bottomBar: BottomBarView,
    private val floatingButtons: FloatingButtonsView,
    private val settingsManager: SettingsManager
) {
    private var currentContext: NavigationContext = NavigationContext.UNKNOWN
    private var previousContext: NavigationContext = NavigationContext.UNKNOWN
    
    init {
        // Subscribe to position updates
        EventBus.register(PositionUpdateEvent::class.java) { event ->
            updateContextFromPosition(event.position, event.source)
        }
        
        // Subscribe to building exit/entry events
        EventBus.register(BuildingExitEvent::class.java) { _ ->
            setContext(NavigationContext.OUTDOOR)
        }
        
        EventBus.register(BuildingEntryEvent::class.java) { _ ->
            setContext(NavigationContext.INDOOR)
        }
        
        // Subscribe to navigation events
        EventBus.register(NavigationStartedEvent::class.java) { _ ->
            setContext(NavigationContext.NAVIGATING)
        }
        
        EventBus.register(NavigationEndedEvent::class.java) { _ ->
            // Return to previous context before navigation started
            setContext(if (previousContext != NavigationContext.NAVIGATING) previousContext else NavigationContext.INDOOR)
        }
        
        // Subscribe to search events
        EventBus.register(SearchFocusedEvent::class.java) { _ ->
            setContext(NavigationContext.SEARCHING)
        }
        
        EventBus.register(SearchUnfocusedEvent::class.java) { _ ->
            // Return to previous context before search started
            setContext(if (previousContext != NavigationContext.SEARCHING) previousContext else NavigationContext.INDOOR)
        }
    }
    
    /**
     * Update the UI context based on positioning source
     */
    private fun updateContextFromPosition(position: Position, source: String) {
        when (source) {
            "gps" -> setContext(NavigationContext.OUTDOOR)
            "wifi", "beacons", "fingerprinting" -> setContext(NavigationContext.INDOOR)
            "fallback" -> {
                // Don't change context for fallback positioning
            }
        }
    }
    
    /**
     * Set the current navigation context and update UI accordingly
     */
    fun setContext(newContext: NavigationContext) {
        if (newContext == currentContext) return
        
        previousContext = currentContext
        currentContext = newContext
        
        // Apply UI changes based on context
        when (newContext) {
            NavigationContext.INDOOR -> applyIndoorUI()
            NavigationContext.OUTDOOR -> applyOutdoorUI()
            NavigationContext.NAVIGATING -> applyNavigatingUI()
            NavigationContext.SEARCHING -> applySearchingUI()
            else -> applyDefaultUI()
        }
        
        EventBus.post(ContextChangedEvent(newContext, previousContext))
    }
    
    /**
     * Apply UI configuration for indoor navigation
     */
    private fun applyIndoorUI() {
        with(navigationView) {
            setMapMode(MapView.MapMode.INDOOR)
            setFloorControlsVisible(true)
            setCompassVisible(true)
            setZoomControlsVisible(true)
        }
        
        with(topBar) {
            setTitle("Indoor Navigation")
            setBackButtonVisible(false)
            setSearchButtonVisible(true)
            setMenuButtonVisible(true)
        }
        
        with(bottomBar) {
            setVisible(true)
            setCurrentTab(BottomBarTab.MAP)
        }
        
        with(floatingButtons) {
            setLocationButtonVisible(true)
            setNavigateButtonVisible(false)
            setDirectionsButtonVisible(true)
        }
    }
    
    /**
     * Apply UI configuration for outdoor navigation
     */
    private fun applyOutdoorUI() {
        with(navigationView) {
            setMapMode(MapView.MapMode.OUTDOOR)
            setFloorControlsVisible(false)
            setCompassVisible(true)
            setZoomControlsVisible(true)
        }
        
        with(topBar) {
            setTitle("Outdoor Navigation")
            setBackButtonVisible(false)
            setSearchButtonVisible(true)
            setMenuButtonVisible(true)
        }
        
        with(bottomBar) {
            setVisible(true)
            setCurrentTab(BottomBarTab.MAP)
        }
        
        with(floatingButtons) {
            setLocationButtonVisible(true)
            setNavigateButtonVisible(false)
            setDirectionsButtonVisible(true)
        }
    }
    
    /**
     * Apply UI configuration for active navigation
     */
    private fun applyNavigatingUI() {
        with(navigationView) {
            // Keep current map mode
            setCompassVisible(true)
            setZoomControlsVisible(true)
        }
        
        with(topBar) {
            setTitle("Navigation Active")
            setBackButtonVisible(true)
            setSearchButtonVisible(false)
            setMenuButtonVisible(false)
        }
        
        with(bottomBar) {
            setVisible(false)
        }
        
        with(floatingButtons) {
            setLocationButtonVisible(true)
            setNavigateButtonVisible(false)
            setDirectionsButtonVisible(false)
            setStopNavigationButtonVisible(true)
        }
    }
    
    /**
     * Apply UI configuration for search mode
     */
    private fun applySearchingUI() {
        with(navigationView) {
            // Keep current map mode
            setFloorControlsVisible(true)
            setCompassVisible(false)
            setZoomControlsVisible(false)
        }
        
        with(topBar) {
            setSearchMode(true)
            setBackButtonVisible(true)
            setMenuButtonVisible(false)
        }
        
        with(bottomBar) {
            setVisible(false)
        }
        
        with(floatingButtons) {
            setAllButtonsVisible(false)
        }
    }
    
    /**
     * Apply default UI configuration
     */
    private fun applyDefaultUI() {
        with(navigationView) {
            setMapMode(MapView.MapMode.INDOOR)
            setFloorControlsVisible(true)
            setCompassVisible(true)
            setZoomControlsVisible(true)
        }
        
        with(topBar) {
            setTitle("Navigation")
            setBackButtonVisible(false)
            setSearchButtonVisible(true)
            setMenuButtonVisible(true)
        }
        
        with(bottomBar) {
            setVisible(true)
            setCurrentTab(BottomBarTab.MAP)
        }
        
        with(floatingButtons) {
            setLocationButtonVisible(true)
            setNavigateButtonVisible(false)
            setDirectionsButtonVisible(true)
        }
    }
    
    /**
     * Navigation context states
     */
    enum class NavigationContext {
        UNKNOWN,
        INDOOR,
        OUTDOOR,
        NAVIGATING,
        SEARCHING
    }
    
    /**
     * Bottom bar tab options
     */
    enum class BottomBarTab {
        MAP,
        SEARCH,
        FAVORITES,
        SETTINGS
    }
    
    /**
     * Event classes for context changes and related events
     */
    data class ContextChangedEvent(
        val newContext: NavigationContext,
        val previousContext: NavigationContext
    )
    
    data class PositionUpdateEvent(val position: Position, val source: String)
    data class BuildingExitEvent(val timestamp: Long = System.currentTimeMillis())
    data class BuildingEntryEvent(val timestamp: Long = System.currentTimeMillis())
    data class NavigationStartedEvent(val timestamp: Long = System.currentTimeMillis())
    data class NavigationEndedEvent(val timestamp: Long = System.currentTimeMillis())
    data class SearchFocusedEvent(val timestamp: Long = System.currentTimeMillis())
    data class SearchUnfocusedEvent(val timestamp: Long = System.currentTimeMillis())
}