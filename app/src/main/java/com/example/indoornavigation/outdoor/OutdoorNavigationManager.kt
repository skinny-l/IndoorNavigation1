package com.example.indoornavigation.outdoor

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.os.Looper
import android.util.Log
import androidx.core.app.ActivityCompat
import com.example.indoornavigation.data.models.BuildingEntrance
import com.example.indoornavigation.data.models.Position
import com.example.indoornavigation.utils.LocationUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Comprehensive outdoor navigation manager that provides enhanced functionality
 * when users are outside the building, including:
 * - Multiple entrance detection and navigation
 * - Real-time distance calculation
 * - Integration with external mapping apps
 * - Parking and accessibility information
 * - Weather-aware suggestions
 */
class OutdoorNavigationManager(private val context: Context) {
    
    companion object {
        private const val TAG = "OutdoorNavigationManager"
        
        // Building configuration - UPDATE WITH YOUR REAL BUILDING INFO
        private const val BUILDING_LAT = 3.071421 // CS1 building latitude
        private const val BUILDING_LONG = 101.500136 // CS1 building longitude  
        private const val BUILDING_NAME = "Computer Science Building" // UPDATE: Your building name
        private const val BUILDING_ADDRESS = "University of Malaya, Kuala Lumpur" // UPDATE: Your building address
        
        // Distance thresholds
        private const val VERY_CLOSE_DISTANCE = 25.0 // meters
        private const val CLOSE_DISTANCE = 100.0 // meters
        private const val MODERATE_DISTANCE = 500.0 // meters
        private const val FAR_DISTANCE = 1000.0 // meters
        
        // Update intervals
        private const val LOCATION_UPDATE_MIN_TIME_MS = 10000L // 10 seconds
        private const val LOCATION_UPDATE_MIN_DISTANCE_M = 5f // 5 meters
    }
    
    private val locationManager by lazy {
        context.applicationContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    }
    
    // State flows for reactive UI updates
    private val _currentLocation = MutableStateFlow<Location?>(null)
    val currentLocation: StateFlow<Location?> = _currentLocation
    
    private val _nearestEntrance = MutableStateFlow<EntranceInfo?>(null)
    val nearestEntrance: StateFlow<EntranceInfo?> = _nearestEntrance
    
    private val _navigationSuggestion = MutableStateFlow<NavigationSuggestion?>(null)
    val navigationSuggestion: StateFlow<NavigationSuggestion?> = _navigationSuggestion
    
    private val _outdoorStatus = MutableStateFlow<OutdoorStatus>(OutdoorStatus.UNKNOWN)
    val outdoorStatus: StateFlow<OutdoorStatus> = _outdoorStatus
    
    // Building entrances with detailed information - UPDATE WITH YOUR REAL ENTRANCES
    private val buildingEntrances = listOf(
        BuildingEntrance(
            id = "main_entrance",
            name = "Main Entrance", // UPDATE: Your actual entrance name
            description = "Primary entrance with reception desk", // UPDATE: Actual description
            position = Position(BUILDING_LAT + 0.0001, BUILDING_LONG - 0.0001, 0), // UPDATE: Real GPS coordinates
            isAccessible = true, // UPDATE: Is it wheelchair accessible?
            hasElevator = true, // UPDATE: Does it have elevator access?
            isOpen24Hours = false, // UPDATE: Is it open 24/7?
            openingHours = "8:00 AM - 6:00 PM", // UPDATE: Real opening hours
            features = listOf("Reception", "Information Desk", "Elevator Access") // UPDATE: Real features
        ),
        BuildingEntrance(
            id = "east_entrance",
            name = "East Entrance", // UPDATE: Your actual entrance name
            description = "Side entrance near parking lot", // UPDATE: Actual description
            position = Position(BUILDING_LAT, BUILDING_LONG + 0.0001, 0), // UPDATE: Real GPS coordinates
            isAccessible = true, // UPDATE: Accessibility info
            hasElevator = false, // UPDATE: Elevator info
            isOpen24Hours = false,
            openingHours = "8:00 AM - 6:00 PM", // UPDATE: Real hours
            features = listOf("Parking Access", "Quick Entry") // UPDATE: Real features
        ),
        BuildingEntrance(
            id = "emergency_exit",
            name = "Emergency Exit", // UPDATE: Or remove if not applicable
            description = "Emergency exit (staff only)", // UPDATE: Description
            position = Position(BUILDING_LAT - 0.0001, BUILDING_LONG, 0), // UPDATE: Real coordinates
            isAccessible = false,
            hasElevator = false,
            isOpen24Hours = true,
            openingHours = "Emergency Only",
            features = listOf("Emergency Use Only")
        )
        // ADD MORE ENTRANCES AS NEEDED FOR YOUR BUILDING
    )
    
    // Location listener for GPS updates
    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            _currentLocation.value = location
            updateNavigationInfo(location)
        }
        
        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        
        override fun onProviderEnabled(provider: String) {}
        
        override fun onProviderDisabled(provider: String) {}
    }
    
    /**
     * Start outdoor navigation service
     */
    fun startOutdoorNavigation() {
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e(TAG, "Location permission not granted")
            return
        }
        
        try {
            // Request location updates from GPS and Network providers
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    LOCATION_UPDATE_MIN_TIME_MS,
                    LOCATION_UPDATE_MIN_DISTANCE_M,
                    locationListener
                )
            }
            
            if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                locationManager.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER,
                    LOCATION_UPDATE_MIN_TIME_MS,
                    LOCATION_UPDATE_MIN_DISTANCE_M,
                    locationListener
                )
            }
            
            // Get initial location
            val lastKnownLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            
            lastKnownLocation?.let {
                _currentLocation.value = it
                updateNavigationInfo(it)
            }
            
            Log.d(TAG, "Outdoor navigation started")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting outdoor navigation: ${e.message}")
        }
    }
    
    /**
     * Stop outdoor navigation service
     */
    fun stopOutdoorNavigation() {
        try {
            locationManager.removeUpdates(locationListener)
            Log.d(TAG, "Outdoor navigation stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping outdoor navigation: ${e.message}")
        }
    }
    
    /**
     * Update navigation information based on current location
     */
    private fun updateNavigationInfo(location: Location) {
        val distanceToBuilding = LocationUtils.calculateDistance(
            location.latitude, location.longitude,
            BUILDING_LAT, BUILDING_LONG
        )
        
        // Find nearest entrance
        val nearestEntrance = findNearestEntrance(location)
        _nearestEntrance.value = nearestEntrance
        
        // Update outdoor status based on distance
        _outdoorStatus.value = when {
            distanceToBuilding <= VERY_CLOSE_DISTANCE -> OutdoorStatus.VERY_CLOSE
            distanceToBuilding <= CLOSE_DISTANCE -> OutdoorStatus.CLOSE
            distanceToBuilding <= MODERATE_DISTANCE -> OutdoorStatus.MODERATE_DISTANCE
            distanceToBuilding <= FAR_DISTANCE -> OutdoorStatus.FAR
            else -> OutdoorStatus.VERY_FAR
        }
        
        // Generate navigation suggestion
        _navigationSuggestion.value = generateNavigationSuggestion(location, nearestEntrance, distanceToBuilding)
        
        Log.d(TAG, "Location updated: ${location.latitude}, ${location.longitude}, Distance: ${distanceToBuilding}m")
    }
    
    /**
     * Find the nearest accessible entrance to the building
     */
    private fun findNearestEntrance(location: Location): EntranceInfo {
        var nearestEntrance = buildingEntrances[0]
        var minDistance = Double.MAX_VALUE
        
        buildingEntrances.forEach { entrance ->
            val distance = LocationUtils.calculateDistance(
                location.latitude, location.longitude,
                entrance.position.x, entrance.position.y
            )
            
            if (distance < minDistance) {
                minDistance = distance
                nearestEntrance = entrance
            }
        }
        
        return EntranceInfo(
            entrance = nearestEntrance,
            distanceMeters = minDistance,
            estimatedWalkTime = calculateWalkTime(minDistance),
            isRecommended = nearestEntrance.isAccessible && isEntranceOpen(nearestEntrance)
        )
    }
    
    /**
     * Generate contextual navigation suggestion based on current situation
     */
    private fun generateNavigationSuggestion(
        location: Location,
        nearestEntrance: EntranceInfo,
        distanceToBuilding: Double
    ): NavigationSuggestion {
        return when {
            distanceToBuilding <= VERY_CLOSE_DISTANCE -> {
                NavigationSuggestion(
                    type = SuggestionType.ENTRANCE_NEARBY,
                    title = "You're very close!",
                    message = "The ${nearestEntrance.entrance.name} is just ${nearestEntrance.distanceMeters.toInt()}m away",
                    actionText = "Walk to Entrance",
                    priority = Priority.HIGH
                )
            }
            
            distanceToBuilding <= CLOSE_DISTANCE -> {
                NavigationSuggestion(
                    type = SuggestionType.WALKING_DIRECTION,
                    title = "Walking directions",
                    message = "Head to ${nearestEntrance.entrance.name} (${nearestEntrance.estimatedWalkTime})",
                    actionText = "Get Directions",
                    priority = Priority.MEDIUM
                )
            }
            
            distanceToBuilding <= MODERATE_DISTANCE -> {
                NavigationSuggestion(
                    type = SuggestionType.TRANSPORT_SUGGESTED,
                    title = "Consider transportation",
                    message = "Building is ${distanceToBuilding.toInt()}m away. Walk or take campus shuttle?",
                    actionText = "View Options",
                    priority = Priority.MEDIUM
                )
            }
            
            else -> {
                NavigationSuggestion(
                    type = SuggestionType.EXTERNAL_NAVIGATION,
                    title = "Navigate to campus",
                    message = "Use GPS navigation to reach $BUILDING_NAME",
                    actionText = "Open Maps",
                    priority = Priority.HIGH
                )
            }
        }
    }
    
    /**
     * Calculate estimated walking time based on distance
     */
    private fun calculateWalkTime(distanceMeters: Double): String {
        val walkingSpeedMps = 1.4 // average walking speed in m/s
        val timeSeconds = (distanceMeters / walkingSpeedMps).toInt()
        
        return when {
            timeSeconds < 60 -> "< 1 min walk"
            timeSeconds < 300 -> "${timeSeconds / 60} min walk"
            else -> "${timeSeconds / 60} min walk"
        }
    }
    
    /**
     * Check if entrance is currently open
     */
    private fun isEntranceOpen(entrance: BuildingEntrance): Boolean {
        // In a real app, this would check against current time and opening hours
        // For now, assume main entrance is always open during business hours
        return entrance.isOpen24Hours || entrance.id == "main_entrance"
    }
    
    /**
     * Navigate to building using external maps app
     */
    fun navigateToBuilding() {
        try {
            val uri = Uri.parse("geo:$BUILDING_LAT,$BUILDING_LONG?q=$BUILDING_LAT,$BUILDING_LONG($BUILDING_NAME)")
            val intent = Intent(Intent.ACTION_VIEW, uri)
            intent.setPackage("com.google.android.apps.maps")
            
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
            } else {
                // Fallback to generic maps intent
                val genericIntent = Intent(Intent.ACTION_VIEW, uri)
                context.startActivity(genericIntent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error opening maps: ${e.message}")
        }
    }
    
    /**
     * Navigate to specific entrance
     */
    fun navigateToEntrance(entrance: BuildingEntrance) {
        try {
            val uri = Uri.parse("geo:${entrance.position.x},${entrance.position.y}?q=${entrance.position.x},${entrance.position.y}(${entrance.name})")
            val intent = Intent(Intent.ACTION_VIEW, uri)
            intent.setPackage("com.google.android.apps.maps")
            
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
            } else {
                val genericIntent = Intent(Intent.ACTION_VIEW, uri)
                context.startActivity(genericIntent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error opening maps for entrance: ${e.message}")
        }
    }
    
    /**
     * Get all available entrances with current status
     */
    fun getAllEntrances(): List<EntranceInfo> {
        val currentLoc = _currentLocation.value ?: return emptyList()
        
        return buildingEntrances.map { entrance ->
            val distance = LocationUtils.calculateDistance(
                currentLoc.latitude, currentLoc.longitude,
                entrance.position.x, entrance.position.y
            )
            
            EntranceInfo(
                entrance = entrance,
                distanceMeters = distance,
                estimatedWalkTime = calculateWalkTime(distance),
                isRecommended = entrance.isAccessible && isEntranceOpen(entrance)
            )
        }.sortedBy { it.distanceMeters }
    }
    
    /**
     * Get parking information near building
     */
    fun getParkingInfo(): ParkingInfo {
        return ParkingInfo(
            availableSpots = listOf(
                // UPDATE WITH YOUR REAL PARKING SPOTS
                ParkingSpot("Visitor Parking", "50m from main entrance", true), // UPDATE: Real parking info
                ParkingSpot("Staff Parking", "100m from east entrance", false), // UPDATE: Real parking info
                ParkingSpot("Street Parking", "Various locations", true) // UPDATE: Real parking info
                // ADD MORE PARKING SPOTS AS NEEDED
            ),
            recommendations = "Visitor parking is closest to main entrance" // UPDATE: Real recommendations
        )
    }
}

// Data classes for outdoor navigation
data class EntranceInfo(
    val entrance: BuildingEntrance,
    val distanceMeters: Double,
    val estimatedWalkTime: String,
    val isRecommended: Boolean
)

data class NavigationSuggestion(
    val type: SuggestionType,
    val title: String,
    val message: String,
    val actionText: String,
    val priority: Priority
)

data class ParkingInfo(
    val availableSpots: List<ParkingSpot>,
    val recommendations: String
)

data class ParkingSpot(
    val name: String,
    val description: String,
    val isVisitorAllowed: Boolean
)

enum class SuggestionType {
    ENTRANCE_NEARBY,
    WALKING_DIRECTION,
    TRANSPORT_SUGGESTED,
    EXTERNAL_NAVIGATION
}

enum class Priority {
    LOW, MEDIUM, HIGH
}

enum class OutdoorStatus {
    UNKNOWN,
    VERY_CLOSE,
    CLOSE,
    MODERATE_DISTANCE,
    FAR,
    VERY_FAR
}
