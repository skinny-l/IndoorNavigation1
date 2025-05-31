package com.example.indoornavigation.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.indoornavigation.MainActivity
import com.example.indoornavigation.R
import com.example.indoornavigation.data.models.Floor
import com.example.indoornavigation.data.models.Position
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Manager for proximity-based notifications
 */
class ProximityNotificationManager(
    private val context: Context,
    private val settingsManager: SettingsManager
) {
    private val trackedLocations = mutableListOf<TrackedLocation>()
    private val triggeredNotifications = mutableSetOf<String>()
    private var currentFloor: Int? = null
    private var userPosition: Position? = null
    
    init {
        // Subscribe to position updates
        EventBus.register(PositionUpdateEvent::class.java) { event ->
            userPosition = event.position
            checkProximity()
        }
        
        // Subscribe to floor changes
        EventBus.register(FloorChangedEvent::class.java) { event ->
            currentFloor = event.floor.level
            // Reset triggered notifications when floor changes
            triggeredNotifications.clear()
            checkProximity()
        }
        
        // Create notification channel
        createNotificationChannel()
    }
    
    /**
     * Create notification channel for proximity alerts
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Proximity Alerts",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifications for nearby points of interest"
            }
            
            val notificationManager = context.getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    /**
     * Track a location for proximity notifications
     */
    fun trackLocation(location: TrackedLocation) {
        // Check if location already exists
        val existingIndex = trackedLocations.indexOfFirst { it.id == location.id }
        if (existingIndex >= 0) {
            trackedLocations[existingIndex] = location
        } else {
            trackedLocations.add(location)
        }
    }
    
    /**
     * Track multiple locations for proximity notifications
     */
    fun trackLocations(locations: List<TrackedLocation>) {
        locations.forEach { trackLocation(it) }
    }
    
    /**
     * Remove a tracked location
     */
    fun removeTrackedLocation(locationId: String) {
        trackedLocations.removeAll { it.id == locationId }
    }
    
    /**
     * Clear all tracked locations
     */
    fun clearTrackedLocations() {
        trackedLocations.clear()
        triggeredNotifications.clear()
    }
    
    /**
     * Check for nearby locations and send notifications
     */
    private fun checkProximity() {
        if (!settingsManager.settings.value?.notificationsEnabled!!) return
        
        val position = userPosition ?: return
        val floor = currentFloor ?: return
        val notificationDistance = settingsManager.settings.value?.notificationDistance?.toFloat() ?: 10f
        
        // Get nearby locations on current floor
        val nearbyLocations = trackedLocations.filter { 
            it.floor == floor && 
            !triggeredNotifications.contains(it.id) &&
            calculateDistance(position, it.position) <= notificationDistance
        }
        
        // Trigger notification for each nearby location
        nearbyLocations.forEach { location ->
            triggeredNotifications.add(location.id)
            showNotification(location)
        }
    }
    
    /**
     * Calculate distance between two positions in 2D
     */
    private fun calculateDistance(pos1: Position, pos2: Position): Float {
        return sqrt(
            (pos1.x - pos2.x).pow(2) + 
            (pos1.y - pos2.y).pow(2)
        ).toFloat()
    }
    
    /**
     * Show notification for a nearby location
     */
    private fun showNotification(location: TrackedLocation) {
        // Local notification
        val notificationManager = NotificationManagerCompat.from(context)
        
        // Build notification
        val notificationIntent = Intent(context, MainActivity::class.java).apply {
            putExtra("location_id", location.id)
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        
        val pendingIntent = PendingIntent.getActivity(
            context, 
            location.id.hashCode(),
            notificationIntent,
            PendingIntent.FLAG_IMMUTABLE
        )
        
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_location)
            .setContentTitle(location.name)
            .setContentText(location.message ?: "You are near ${location.name}")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        
        try {
            notificationManager.notify(location.id.hashCode(), notification)
        } catch (e: SecurityException) {
            // Handle case where notification permission is not granted
        }
        
        // Also post an in-app notification
        EventBus.post(ProximityEvent(location))
    }
    
    /**
     * Position update event class
     */
    data class PositionUpdateEvent(val position: Position, val source: String)
    
    /**
     * Floor changed event class
     */
    data class FloorChangedEvent(val floor: Floor)
    
    /**
     * Tracked location data class
     */
    data class TrackedLocation(
        val id: String,
        val name: String,
        val floor: Int,
        val position: Position,
        val radius: Float = 10f,
        val message: String? = null,
        val type: String = "poi"
    )
    
    /**
     * Proximity event data class
     */
    data class ProximityEvent(val location: TrackedLocation)
    
    companion object {
        private const val CHANNEL_ID = "proximity_channel"
    }
}