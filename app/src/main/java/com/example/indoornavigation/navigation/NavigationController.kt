package com.example.indoornavigation.navigation

import android.util.Log
import com.example.indoornavigation.data.fusion.PositionFusionEngine
import com.example.indoornavigation.data.models.NavNode
import com.example.indoornavigation.data.models.NavNodeEnhanced
import com.example.indoornavigation.data.models.Position
import com.example.indoornavigation.data.navigation.EnhancedNavigationService
import com.example.indoornavigation.viewmodel.NavigationViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Controller for managing navigation, including re-routing when user deviates from path
 */
class NavigationController(
    private val positioningEngine: PositionFusionEngine,
    private val navigationService: EnhancedNavigationService,
    private val navViewModel: NavigationViewModel,
    private val coroutineScope: CoroutineScope
) {
    companion object {
        private const val TAG = "NavigationController"
        
        // How far (in meters) the user can deviate from path before re-routing
        private const val OFF_PATH_THRESHOLD = 5.0
        
        // Minimum time between re-routing checks (ms)
        private const val REROUTE_CHECK_INTERVAL = 5000L
        
        // Minimum time between re-routes (ms)
        private const val MIN_REROUTE_INTERVAL = 15000L
    }
    
    private var currentPath: List<NavNodeEnhanced> = emptyList()
    private var destinationNodeId: String? = null
    private var isNavigating = false
    private var lastOnPathCheck = 0L
    private var lastRerouteTime = 0L
    private var navigationJob: Job? = null
    
    /**
     * Start navigation to a destination
     * @param destinationId ID of the destination node
     * @return true if navigation started successfully
     */
    fun startNavigation(destinationId: String): Boolean {
        val currentPosition = positioningEngine.fusedPosition.value ?: return false
        
        // Find the closest node to current position
        val startNode = navigationService.findClosestNode(currentPosition) ?: return false
        
        // Calculate path
        val path = navigationService.findPath(startNode.id, destinationId)
        if (path.isEmpty()) {
            Log.e(TAG, "Failed to find path to destination")
            return false
        }
        
        // Set current destination and path
        destinationNodeId = destinationId
        currentPath = path
        
        // Update view model
        navViewModel.calculatePath(path.first().position, path.last().position)
        
        // Start navigation monitoring
        isNavigating = true
        startNavigationMonitoring()
        
        return true
    }
    
    /**
     * Pause navigation
     */
    fun pauseNavigation() {
        isNavigating = false
        navigationJob?.cancel()
    }
    
    /**
     * Resume navigation
     */
    fun resumeNavigation() {
        if (destinationNodeId != null) {
            isNavigating = true
            startNavigationMonitoring()
        }
    }
    
    /**
     * Stop navigation
     */
    fun stopNavigation() {
        isNavigating = false
        navigationJob?.cancel()
        destinationNodeId = null
        currentPath = emptyList()
        navViewModel.stopNavigation()
    }
    
    /**
     * Start monitoring user position for re-routing checks
     */
    private fun startNavigationMonitoring() {
        // Cancel existing job if any
        navigationJob?.cancel()
        
        // Start new monitoring job
        navigationJob = coroutineScope.launch(Dispatchers.Default) {
            while (isNavigating) {
                checkPositionAndReroute()
                delay(1000) // Check every second
            }
        }
    }
    
    /**
     * Check if user is still on path and reroute if necessary
     */
    fun checkPositionAndReroute() {
        if (!isNavigating || System.currentTimeMillis() - lastOnPathCheck < REROUTE_CHECK_INTERVAL) {
            return
        }
        
        lastOnPathCheck = System.currentTimeMillis()
        val currentPosition = positioningEngine.fusedPosition.value ?: return
        
        // Find closest node on path
        val closestPathNode = findClosestNode(currentPath, currentPosition)
        val distanceToPath = calculateDistance(currentPosition, closestPathNode.position)
        
        Log.d(TAG, "Distance to path: $distanceToPath meters")
        
        if (distanceToPath > OFF_PATH_THRESHOLD && 
            System.currentTimeMillis() - lastRerouteTime > MIN_REROUTE_INTERVAL) {
            // User is off path, reroute
            Log.d(TAG, "User is off path. Recalculating route...")
            
            val closestGraphNode = navigationService.findClosestNode(currentPosition) ?: return
            destinationNodeId?.let { destination ->
                // Calculate new path
                val newPath = navigationService.findPath(closestGraphNode.id, destination)
                
                if (newPath.isNotEmpty()) {
                    lastRerouteTime = System.currentTimeMillis()
                    currentPath = newPath
                    // Use the first and last positions for recalculating path
                    val pathToFeed = newPath.takeIf { it.isNotEmpty() }?.let {
                        navViewModel.calculatePath(it.first().position, it.last().position)
                    }
                    
                    // Notify that route has been recalculated
                    navViewModel.recenterMap() // Use recenter as a notification
                }
            }
        }
    }
    
    /**
     * Find the closest node to the given position
     * @param nodes List of nodes to search
     * @param position Current position
     * @return The closest node
     */
    private fun findClosestNode(nodes: List<NavNodeEnhanced>, position: Position): NavNodeEnhanced {
        if (nodes.isEmpty()) throw IllegalArgumentException("Node list cannot be empty")
        
        return nodes.minByOrNull { 
            calculateDistance(position, it.position)
        } ?: nodes.first()
    }
    
    /**
     * Calculate Euclidean distance between two positions
     * @param p1 First position
     * @param p2 Second position
     * @return Distance in meters
     */
    private fun calculateDistance(p1: Position, p2: Position): Double {
        // Add floor penalty if on different floors
        val floorPenalty = if (p1.floor != p2.floor) 50.0 else 0.0
        
        val dx = p2.x - p1.x
        val dy = p2.y - p1.y
        
        return sqrt(dx.pow(2) + dy.pow(2)) + floorPenalty
    }
    
    /**
     * Get the current navigation path
     */
    fun getCurrentPath(): List<NavNodeEnhanced> {
        return currentPath
    }
    
    /**
     * Check if currently navigating
     */
    fun isNavigating(): Boolean {
        return isNavigating
    }
}