package com.example.indoornavigation.data.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

@Entity(tableName = "cached_pois")
data class CachedPOIEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String,
    val x: Double,
    val y: Double,
    val floor: Int,
    val category: String,
    val tags: String, // JSON string
    val lastUpdated: Long,
    val isFavorite: Boolean = false
)

@Entity(tableName = "cached_routes")
data class CachedRouteEntity(
    @PrimaryKey val id: String,
    val startX: Double,
    val startY: Double,
    val startFloor: Int,
    val endX: Double,
    val endY: Double,
    val endFloor: Int,
    val waypoints: String, // JSON string
    val distance: Double,
    val estimatedTime: Long,
    val difficulty: String,
    val accessibilityFeatures: String, // JSON string
    val lastUsed: Long,
    val useCount: Int = 0
)

@Entity(tableName = "offline_maps")
data class OfflineMapEntity(
    @PrimaryKey val id: String,
    val floor: Int,
    val mapData: ByteArray,
    val metadata: String, // JSON string
    val lastUpdated: Long,
    val version: Int
)

@Entity(tableName = "user_location_history")
data class UserLocationHistoryEntity(
    @PrimaryKey val id: String,
    val x: Double,
    val y: Double,
    val floor: Int,
    val timestamp: Long,
    val accuracy: Float,
    val source: String, // "BLE", "WIFI", "FUSION"
    val sessionId: String
)

@Entity(tableName = "beacons")
data class BeaconEntity(
    @PrimaryKey val id: String,
    val name: String,
    val x: Double,
    val y: Double,
    val floor: Int,
    val txPower: Int,
    val isActive: Boolean,
    val lastSeen: Long,
    val batteryLevel: Int = -1,
    val metadata: String? // JSON string
)

@Entity(tableName = "wifi_access_points")
data class WifiAccessPointEntity(
    @PrimaryKey val bssid: String,
    val ssid: String,
    val x: Double?,
    val y: Double?,
    val floor: Int?,
    val averageRssi: Int,
    val frequency: Int,
    val lastSeen: Long,
    val measurements: String // JSON array of RSSI measurements
)

@Entity(tableName = "navigation_history")
data class NavigationHistoryEntity(
    @PrimaryKey val id: String,
    val startName: String,
    val endName: String,
    val startX: Double,
    val startY: Double,
    val startFloor: Int,
    val endX: Double,
    val endY: Double,
    val endFloor: Int,
    val timestamp: Long,
    val duration: Long,
    val distance: Double,
    val successful: Boolean
)

@Entity(tableName = "user_preferences")
data class UserPreferencesEntity(
    @PrimaryKey val key: String,
    val value: String,
    val type: String, // "STRING", "BOOLEAN", "INT", "FLOAT"
    val lastModified: Long
)

@Entity(tableName = "analytics_events")
data class AnalyticsEventEntity(
    @PrimaryKey val id: String,
    val eventType: String,
    val eventData: String, // JSON string
    val timestamp: Long,
    val userId: String?,
    val sessionId: String,
    val uploaded: Boolean = false
)

@Entity(tableName = "crash_reports")
data class CrashReportEntity(
    @PrimaryKey val id: String,
    val crashMessage: String,
    val stackTrace: String,
    val deviceInfo: String, // JSON string
    val appVersion: String,
    val timestamp: Long,
    val uploaded: Boolean = false
)

@Entity(tableName = "fingerprints")
data class FingerprintEntity(
    @PrimaryKey val id: String,
    val x: Double,
    val y: Double,
    val floor: Int,
    val wifiSignatures: String, // JSON string
    val bleSignatures: String, // JSON string
    val timestamp: Long,
    val quality: Float
)

@Entity(tableName = "floor_plans")
data class FloorPlanEntity(
    @PrimaryKey val id: String,
    val floor: Int,
    val planData: ByteArray,
    val planType: String, // "SVG", "PNG", "JSON"
    val scale: Float,
    val offsetX: Float,
    val offsetY: Float,
    val metadata: String, // JSON string
    val version: Int,
    val lastUpdated: Long
)

@Entity(tableName = "accessibility_routes")
data class AccessibilityRouteEntity(
    @PrimaryKey val id: String,
    val startX: Double,
    val startY: Double,
    val startFloor: Int,
    val endX: Double,
    val endY: Double,
    val endFloor: Int,
    val waypoints: String, // JSON string
    val accessibilityFeatures: String, // JSON string - wheelchair, ramps, elevators
    val difficultyLevel: Int // 1-5 scale
)

@Entity(tableName = "emergency_contacts")
data class EmergencyContactEntity(
    @PrimaryKey val id: String,
    val name: String,
    val phone: String,
    val email: String?,
    val relationship: String,
    val isPrimary: Boolean = false
)

@Entity(tableName = "notifications")
data class NotificationEntity(
    @PrimaryKey val id: String,
    val title: String,
    val message: String,
    val type: String, // "NAVIGATION", "EMERGENCY", "UPDATE", "SYSTEM"
    val priority: Int, // 1-5
    val timestamp: Long,
    val read: Boolean = false,
    val actionData: String? // JSON string for action parameters
)

// Type converters for complex data types
class Converters {
    
    @TypeConverter
    fun fromStringList(value: List<String>): String {
        return Gson().toJson(value)
    }

    @TypeConverter
    fun toStringList(value: String): List<String> {
        return Gson().fromJson(value, object : TypeToken<List<String>>() {}.type)
    }

    @TypeConverter
    fun fromDoubleList(value: List<Double>): String {
        return Gson().toJson(value)
    }

    @TypeConverter
    fun toDoubleList(value: String): List<Double> {
        return Gson().fromJson(value, object : TypeToken<List<Double>>() {}.type)
    }
    
    @TypeConverter
    fun fromMap(value: Map<String, Any>): String {
        return Gson().toJson(value)
    }

    @TypeConverter
    fun toMap(value: String): Map<String, Any> {
        return Gson().fromJson(value, object : TypeToken<Map<String, Any>>() {}.type)
    }
}