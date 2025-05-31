package com.example.indoornavigation.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.indoornavigation.data.models.NavigationInstruction
import com.example.indoornavigation.data.models.NavNodeEnhanced
import com.example.indoornavigation.data.models.Path
import com.example.indoornavigation.data.models.Position
import com.example.indoornavigation.data.navigation.EfficientPathCalculator
import com.example.indoornavigation.data.navigation.EnhancedNavigationService
import com.example.indoornavigation.data.navigation.NavigationInstructionGenerator
import com.example.indoornavigation.data.navigation.NavigationManager
import com.example.indoornavigation.data.navigation.NavigationMetricsCalculator
import com.example.indoornavigation.data.repositories.NavigationRepository
import com.example.indoornavigation.data.repositories.POIRepository
import com.example.indoornavigation.data.repositories.PositioningRepository
import com.example.indoornavigation.positioning.BuildingDetector
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Location status indicating whether the user is inside or outside the building
 */
enum class LocationStatus {
    UNKNOWN,
    INSIDE_BUILDING,
    OUTSIDE_BUILDING
}

/**
 * ViewModel for navigation functionality with performance optimizations
 */
class NavigationViewModel(application: Application) : AndroidViewModel(application) {

    // Repositories
    private val positioningRepository = PositioningRepository.getInstance(application)
    private val navigationRepository = NavigationRepository.getInstance(application)
    private val poiRepository = POIRepository(application)
    
    // Navigation services
    private val navigationService = EnhancedNavigationService(navigationRepository.getNavigationGraph())
    private val effPathCalculator = EfficientPathCalculator(navigationService)
    private val navMetricsCalculator = NavigationMetricsCalculator()
    private val instructionGenerator = NavigationInstructionGenerator(navigationService)
    
    // Navigation manager
    private val navigationManager = NavigationManager(application.applicationContext)
    
    // Update interval for navigation
    private val NAVIGATION_UPDATE_INTERVAL = 1000L  // 1 second
    
    // Building detector for indoor/outdoor detection
    private val buildingDetector = BuildingDetector(application.applicationContext)
    
    // Navigation path
    private val _path = MutableStateFlow<Path?>(null)
    val path: StateFlow<Path?> = _path.asStateFlow()
    
    // Current navigation instructions
    private val _currentInstructions = MutableStateFlow<List<NavigationInstruction>>(emptyList())
    val currentInstructions: StateFlow<List<NavigationInstruction>> = _currentInstructions.asStateFlow()
    
    // Current position
    private val _currentPosition = MutableStateFlow<Position?>(null)
    val currentPosition: StateFlow<Position?> = _currentPosition.asStateFlow()
    
    // Navigation metrics
    private val _remainingDistance = MutableStateFlow<Float>(0f)
    val remainingDistance: StateFlow<Float> = _remainingDistance.asStateFlow()
    
    private val _estimatedTimeSeconds = MutableStateFlow<Float>(0f)
    val estimatedTimeSeconds: StateFlow<Float> = _estimatedTimeSeconds.asStateFlow()
    
    // Building location status
    private val _locationStatus = MutableStateFlow(LocationStatus.UNKNOWN)
    val locationStatus: StateFlow<LocationStatus> = _locationStatus.asStateFlow()
    
    // Indoor mode enabled flag
    private val _indoorModeEnabled = MutableStateFlow(true) // Default to true
    val indoorModeEnabled: StateFlow<Boolean> = _indoorModeEnabled.asStateFlow()
    
    // Navigation state
    private var isNavigating = false
    private var navigationJob: Job? = null
    private var destinationId: String? = null
    
    // Current floor
    private val _currentFloor = MutableStateFlow(0)
    val currentFloor: StateFlow<Int> = _currentFloor.asStateFlow()
    
    // Available floors in path
    private val _availableFloors = MutableStateFlow<List<Int>>(listOf(0))
    val availableFloors: StateFlow<List<Int>> = _availableFloors.asStateFlow()
    
    // Map recenter flag
    private val _recenterMap = MutableStateFlow(false)
    val recenterMap: StateFlow<Boolean> = _recenterMap.asStateFlow()
    
    // Reached destination
    private val _reachedDestination = MutableStateFlow(false)
    val reachedDestination: StateFlow<Boolean> = _reachedDestination.asStateFlow()
    
    // Current instruction (for UI)
    private val _currentInstruction = MutableStateFlow<NavigationInstruction?>(null)
    val currentInstruction: StateFlow<NavigationInstruction?> = _currentInstruction.asStateFlow()

    init {
        viewModelScope.launch {
            positioningRepository.getCurrentPositionFlow().collectLatest { position: Position? ->
                position?.let {
                    _currentPosition.value = it
                    
                    // Update navigation if active
                    if (isNavigating) {
                        updateNavigation(it)
                    }
                }
            }
        }
    }
    
    /**
     * Calculate path between two positions
     */
    fun calculatePath(start: Position, end: Position): Path? {
        // Find closest nodes to start and end positions
        val startNode = navigationService.findClosestNode(start) ?: return null
        val endNode = navigationService.findClosestNode(end) ?: return null
        
        // Use efficient path calculator to calculate path (with caching)
        val nodePath = effPathCalculator.calculatePath(startNode.id, endNode.id)
        
        if (nodePath.isEmpty()) {
            return null
        }
        
        // Convert nodes to positions
        val waypoints = nodePath.map { it.position }
        
        // Create path object
        val path = Path(
            start = start,
            end = end,
            waypoints = waypoints
        )
        
        _path.value = path
        
        // Update available floors
        updateAvailableFloors(waypoints.map { it.floor }.distinct())
        
        return path
    }
    
    /**
     * Start navigation to a POI
     */
    fun startNavigation(poiId: String) {
        viewModelScope.launch {
            val poi = poiRepository.getPOIById(poiId) ?: return@launch
            val currentPos = _currentPosition.value ?: return@launch
            
            // Calculate path
            val path = calculatePath(currentPos, poi.position) ?: return@launch
            
            // Store destination ID
            destinationId = poiId
            
            // Generate instructions
            val startNodeId = navigationService.findClosestNode(currentPos)?.id
            val endNodeId = navigationService.findClosestNode(poi.position)?.id
            
            if (startNodeId != null && endNodeId != null) {
                val nodePath = navigationService.findPath(startNodeId, endNodeId)
                _currentInstructions.value = instructionGenerator.generateInstructions(nodePath)
                
                // Start navigation updates
                isNavigating = true
                _reachedDestination.value = false
                startNavigationUpdates()
            }
        }
    }
    
    /**
     * Start navigation with a Path object
     */
    fun startNavigation(path: Path) {
        viewModelScope.launch {
            _path.value = path
            
            val startPos = path.start
            val endPos = path.end
            
            // Generate instructions
            val startNodeId = navigationService.findClosestNode(startPos)?.id
            val endNodeId = navigationService.findClosestNode(endPos)?.id
            
            if (startNodeId != null && endNodeId != null) {
                val nodePath = navigationService.findPath(startNodeId, endNodeId)
                _currentInstructions.value = instructionGenerator.generateInstructions(nodePath)
                
                // Start navigation updates
                isNavigating = true
                _reachedDestination.value = false
                startNavigationUpdates()
            }
        }
    }
    
    /**
     * Update navigation with current position
     */
    private fun updateNavigation(position: Position) {
        val path = _path.value ?: return
        val nodePath = navigationService.findPath(
            navigationService.findClosestNode(position)?.id ?: return,
            navigationService.findClosestNode(path.end)?.id ?: return
        )
        
        // Check if destination reached
        if (isDestinationReached(position, path.end)) {
            isNavigating = false
            _reachedDestination.value = true
            navigationJob?.cancel()
            return
        }
        
        // Update metrics
        _remainingDistance.value = navMetricsCalculator.calculateRemainingDistance(nodePath.map { it.position }, position)
        _estimatedTimeSeconds.value = navMetricsCalculator.calculateETASeconds(nodePath.map { it.position })
        
        // Check if user is off path and needs rerouting
        checkForRerouting(position, nodePath)
        
        // Check for floor transitions
        checkForFloorTransition(position, nodePath)
        
        // Set current floor
        _currentFloor.value = position.floor
    }
    
    /**
     * Start navigation updates
     */
    private fun startNavigationUpdates() {
        navigationJob?.cancel()
        navigationJob = viewModelScope.launch {
            while (isNavigating) {
                delay(NAVIGATION_UPDATE_INTERVAL)
                
                // Current position is already being observed
                // and updating the navigation via updateNavigation()
            }
        }
    }
    
    /**
     * Check if destination has been reached
     */
    private fun isDestinationReached(position: Position, destination: Position): Boolean {
        // Consider destination reached if within 3 meters on the same floor
        return position.floor == destination.floor && position.distanceTo(destination) < 3.0
    }
    
    /**
     * Check if user has deviated from the path and needs rerouting
     */
    private fun checkForRerouting(position: Position, path: List<NavNodeEnhanced>) {
        // Maximum distance from path before rerouting (meters)
        val maxOffPathDistance = 10.0
        
        // Check distance to the path
        val closestDistance = path.minOfOrNull { node -> 
            position.distanceTo(node.position)
        } ?: Double.MAX_VALUE
        
        // If too far from path, recalculate route
        if (closestDistance > maxOffPathDistance) {
            // Get the destination
            val destination = _path.value?.end ?: return
            
            // Recalculate path from current position
            calculatePath(position, destination)
            
            // Regenerate instructions
            val startNode = navigationService.findClosestNode(position) ?: return
            val endNode = navigationService.findClosestNode(destination) ?: return
            val newPath = effPathCalculator.calculatePath(startNode.id, endNode.id)
            _currentInstructions.value = instructionGenerator.generateInstructions(newPath)
        }
    }
    
    /**
     * Check if user is transitioning between floors
     */
    private fun checkForFloorTransition(position: Position, path: List<NavNodeEnhanced>) {
        // Current floor from position
        val currentFloor = position.floor
        
        // If floor has changed, update instructions
        if (currentFloor != _currentFloor.value) {
            _currentFloor.value = currentFloor
            
            // Find the first node on the new floor
            val startNode = path.find { it.position.floor == currentFloor } ?: return
            val endNode = path.last()
            
            // Generate new instructions from this floor
            val floorPath = effPathCalculator.calculatePath(startNode.id, endNode.id)
            _currentInstructions.value = instructionGenerator.generateInstructions(floorPath)
        }
    }
    
    /**
     * Stop navigation
     */
    fun stopNavigation() {
        isNavigating = false
        navigationJob?.cancel()
        effPathCalculator.clear()
        _path.value = null
        _currentInstructions.value = emptyList()
        _reachedDestination.value = false
    }
    
    /**
     * Recenter map on user
     */
    fun recenterMap() {
        _recenterMap.value = true
        // Reset flag after a short delay (handled by observers)
        viewModelScope.launch {
            delay(100)
            _recenterMap.value = false
        }
    }
    
    fun setCurrentFloor(floor: Int) {
        _currentFloor.value = floor
    }
    
    fun isNavigationActive(): Boolean {
        return isNavigating
    }
    
    fun getVoiceGuidanceEnabled(): Boolean {
        return navigationManager.isVoiceGuidanceEnabled()
    }
    
    fun setVoiceGuidance(enabled: Boolean) {
        navigationManager.setVoiceGuidance(enabled)
    }
    
    fun getHapticFeedbackEnabled(): Boolean {
        return navigationManager.isHapticFeedbackEnabled()
    }
    
    fun setHapticFeedback(enabled: Boolean) {
        navigationManager.setHapticFeedback(enabled)
    }
    
    fun getAccessibleRoutesEnabled(): Boolean {
        return navigationManager.isAccessibleRoutesEnabled()
    }
    
    fun setAccessibleRoutes(enabled: Boolean) {
        navigationManager.setAccessibleRoutes(enabled)
        _currentPosition.value?.let { position ->
            _path.value?.end?.let { destination ->
                calculatePath(position, destination)
            }
        }
    }
    
    /**
     * Update available floors in path
     */
    fun updateAvailableFloors(floors: List<Int>) {
        _availableFloors.value = floors.sorted()
    }
    
    /**
     * Set location status
     */
    fun setLocationStatus(status: LocationStatus) {
        _locationStatus.value = status
    }
    
    /**
     * Set indoor mode enabled
     */
    fun setIndoorModeEnabled(enabled: Boolean) {
        _indoorModeEnabled.value = enabled
        
        // If this is a manual override, use development mode
        buildingDetector.setDevelopmentMode(enabled)
    }

    override fun onCleared() {
        super.onCleared()
        
        // Clean up resources
        stopNavigation()
        navigationManager.shutdown()
    }
}
