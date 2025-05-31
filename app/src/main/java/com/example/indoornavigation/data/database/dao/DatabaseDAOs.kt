package com.example.indoornavigation.data.database.dao

import androidx.room.*
import androidx.lifecycle.LiveData
import kotlinx.coroutines.flow.Flow
import com.example.indoornavigation.data.database.entities.*

@Dao
interface POIDao {
    @Query("SELECT * FROM cached_pois WHERE floor = :floor")
    fun getPOIsForFloor(floor: Int): Flow<List<CachedPOIEntity>>
    
    @Query("SELECT * FROM cached_pois WHERE id = :id")
    suspend fun getPOIById(id: String): CachedPOIEntity?
    
    @Query("SELECT * FROM cached_pois WHERE name LIKE '%' || :query || '%' OR description LIKE '%' || :query || '%'")
    fun searchPOIs(query: String): Flow<List<CachedPOIEntity>>
    
    @Query("SELECT * FROM cached_pois WHERE isFavorite = 1")
    fun getFavoritePOIs(): Flow<List<CachedPOIEntity>>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPOI(poi: CachedPOIEntity)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPOIs(pois: List<CachedPOIEntity>)
    
    @Update
    suspend fun updatePOI(poi: CachedPOIEntity)
    
    @Delete
    suspend fun deletePOI(poi: CachedPOIEntity)
    
    @Query("DELETE FROM cached_pois WHERE floor = :floor")
    suspend fun deletePOIsForFloor(floor: Int)
}

@Dao
interface RouteDao {
    @Query("SELECT * FROM cached_routes ORDER BY lastUsed DESC")
    fun getAllRoutes(): Flow<List<CachedRouteEntity>>
    
    @Query("SELECT * FROM cached_routes WHERE id = :id")
    suspend fun getRouteById(id: String): CachedRouteEntity?
    
    @Query("SELECT * FROM cached_routes ORDER BY useCount DESC LIMIT :limit")
    fun getMostUsedRoutes(limit: Int = 10): Flow<List<CachedRouteEntity>>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRoute(route: CachedRouteEntity)
    
    @Update
    suspend fun updateRoute(route: CachedRouteEntity)
    
    @Delete
    suspend fun deleteRoute(route: CachedRouteEntity)
    
    @Query("DELETE FROM cached_routes WHERE lastUsed < :timestamp")
    suspend fun deleteOldRoutes(timestamp: Long)
}

@Dao
interface OfflineMapDao {
    @Query("SELECT * FROM offline_maps WHERE floor = :floor")
    suspend fun getMapForFloor(floor: Int): OfflineMapEntity?
    
    @Query("SELECT * FROM offline_maps ORDER BY floor")
    fun getAllMaps(): Flow<List<OfflineMapEntity>>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMap(map: OfflineMapEntity)
    
    @Update
    suspend fun updateMap(map: OfflineMapEntity)
    
    @Delete
    suspend fun deleteMap(map: OfflineMapEntity)
    
    @Query("DELETE FROM offline_maps WHERE version < :version")
    suspend fun deleteOldVersions(version: Int)
}

@Dao
interface LocationHistoryDao {
    @Query("SELECT * FROM user_location_history WHERE sessionId = :sessionId ORDER BY timestamp DESC")
    fun getSessionHistory(sessionId: String): Flow<List<UserLocationHistoryEntity>>
    
    @Query("SELECT * FROM user_location_history WHERE timestamp > :since ORDER BY timestamp DESC")
    fun getRecentHistory(since: Long): Flow<List<UserLocationHistoryEntity>>
    
    @Query("SELECT * FROM user_location_history WHERE floor = :floor AND timestamp > :since")
    fun getHistoryForFloor(floor: Int, since: Long): Flow<List<UserLocationHistoryEntity>>
    
    @Insert
    suspend fun insertLocation(location: UserLocationHistoryEntity)
    
    @Query("DELETE FROM user_location_history WHERE timestamp < :timestamp")
    suspend fun deleteOldHistory(timestamp: Long)
}

@Dao
interface BeaconDao {
    @Query("SELECT * FROM beacons WHERE floor = :floor AND isActive = 1")
    fun getActiveBeaconsForFloor(floor: Int): Flow<List<BeaconEntity>>
    
    @Query("SELECT * FROM beacons WHERE id = :id")
    suspend fun getBeaconById(id: String): BeaconEntity?
    
    @Query("SELECT * FROM beacons")
    fun getAllBeacons(): Flow<List<BeaconEntity>>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBeacon(beacon: BeaconEntity)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBeacons(beacons: List<BeaconEntity>)
    
    @Update
    suspend fun updateBeacon(beacon: BeaconEntity)
    
    @Delete
    suspend fun deleteBeacon(beacon: BeaconEntity)
    
    @Query("UPDATE beacons SET lastSeen = :timestamp WHERE id = :id")
    suspend fun updateLastSeen(id: String, timestamp: Long)
}

@Dao
interface WifiDao {
    @Query("SELECT * FROM wifi_access_points WHERE floor = :floor")
    fun getWifiPointsForFloor(floor: Int): Flow<List<WifiAccessPointEntity>>
    
    @Query("SELECT * FROM wifi_access_points WHERE bssid = :bssid")
    suspend fun getWifiPoint(bssid: String): WifiAccessPointEntity?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWifiPoint(wifiPoint: WifiAccessPointEntity)
    
    @Update
    suspend fun updateWifiPoint(wifiPoint: WifiAccessPointEntity)
    
    @Query("UPDATE wifi_access_points SET lastSeen = :timestamp WHERE bssid = :bssid")
    suspend fun updateLastSeen(bssid: String, timestamp: Long)
    
    @Query("DELETE FROM wifi_access_points WHERE lastSeen < :timestamp")
    suspend fun deleteOldWifiPoints(timestamp: Long)
}

@Dao
interface NavigationHistoryDao {
    @Query("SELECT * FROM navigation_history ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentNavigations(limit: Int = 50): Flow<List<NavigationHistoryEntity>>
    
    @Query("SELECT * FROM navigation_history WHERE successful = 1 ORDER BY timestamp DESC")
    fun getSuccessfulNavigations(): Flow<List<NavigationHistoryEntity>>
    
    @Insert
    suspend fun insertNavigation(navigation: NavigationHistoryEntity)
    
    @Query("DELETE FROM navigation_history WHERE timestamp < :timestamp")
    suspend fun deleteOldNavigations(timestamp: Long)
}

@Dao
interface UserPreferencesDao {
    @Query("SELECT * FROM user_preferences WHERE key = :key")
    suspend fun getPreference(key: String): UserPreferencesEntity?
    
    @Query("SELECT * FROM user_preferences")
    fun getAllPreferences(): Flow<List<UserPreferencesEntity>>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setPreference(preference: UserPreferencesEntity)
    
    @Delete
    suspend fun deletePreference(preference: UserPreferencesEntity)
}

@Dao
interface AnalyticsDao {
    @Query("SELECT * FROM analytics_events WHERE uploaded = 0 ORDER BY timestamp ASC")
    suspend fun getUnuploadedEvents(): List<AnalyticsEventEntity>
    
    @Insert
    suspend fun insertEvent(event: AnalyticsEventEntity)
    
    @Query("UPDATE analytics_events SET uploaded = 1 WHERE id IN (:eventIds)")
    suspend fun markEventsUploaded(eventIds: List<String>)
    
    @Query("DELETE FROM analytics_events WHERE uploaded = 1 AND timestamp < :timestamp")
    suspend fun deleteOldEvents(timestamp: Long)
}

@Dao
interface CrashReportDao {
    @Query("SELECT * FROM crash_reports WHERE uploaded = 0 ORDER BY timestamp ASC")
    suspend fun getUnuploadedCrashReports(): List<CrashReportEntity>
    
    @Insert
    suspend fun insertCrashReport(crashReport: CrashReportEntity)
    
    @Query("UPDATE crash_reports SET uploaded = 1 WHERE id IN (:reportIds)")
    suspend fun markReportsUploaded(reportIds: List<String>)
    
    @Query("DELETE FROM crash_reports WHERE uploaded = 1 AND timestamp < :timestamp")
    suspend fun deleteOldReports(timestamp: Long)
}

@Dao
interface FingerprintDao {
    @Query("SELECT * FROM fingerprints WHERE floor = :floor")
    fun getFingerprintsForFloor(floor: Int): Flow<List<FingerprintEntity>>
    
    @Query("SELECT * FROM fingerprints WHERE x BETWEEN :minX AND :maxX AND y BETWEEN :minY AND :maxY AND floor = :floor")
    suspend fun getFingerprintsInArea(floor: Int, minX: Double, maxX: Double, minY: Double, maxY: Double): List<FingerprintEntity>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFingerprint(fingerprint: FingerprintEntity)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFingerprints(fingerprints: List<FingerprintEntity>)
    
    @Delete
    suspend fun deleteFingerprint(fingerprint: FingerprintEntity)
}

@Dao
interface FloorPlanDao {
    @Query("SELECT * FROM floor_plans WHERE floor = :floor ORDER BY version DESC LIMIT 1")
    suspend fun getLatestFloorPlan(floor: Int): FloorPlanEntity?
    
    @Query("SELECT * FROM floor_plans ORDER BY floor, version DESC")
    fun getAllFloorPlans(): Flow<List<FloorPlanEntity>>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFloorPlan(floorPlan: FloorPlanEntity)
    
    @Query("DELETE FROM floor_plans WHERE floor = :floor AND version < :version")
    suspend fun deleteOldVersions(floor: Int, version: Int)
}

@Dao
interface AccessibilityDao {
    @Query("SELECT * FROM accessibility_routes WHERE startFloor = :startFloor AND endFloor = :endFloor")
    fun getAccessibilityRoutes(startFloor: Int, endFloor: Int): Flow<List<AccessibilityRouteEntity>>
    
    @Query("SELECT * FROM accessibility_routes WHERE difficultyLevel <= :maxDifficulty")
    fun getRoutesWithMaxDifficulty(maxDifficulty: Int): Flow<List<AccessibilityRouteEntity>>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAccessibilityRoute(route: AccessibilityRouteEntity)
    
    @Delete
    suspend fun deleteAccessibilityRoute(route: AccessibilityRouteEntity)
}

@Dao
interface EmergencyDao {
    @Query("SELECT * FROM emergency_contacts ORDER BY isPrimary DESC, name ASC")
    fun getAllEmergencyContacts(): Flow<List<EmergencyContactEntity>>
    
    @Query("SELECT * FROM emergency_contacts WHERE isPrimary = 1")
    suspend fun getPrimaryContact(): EmergencyContactEntity?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertContact(contact: EmergencyContactEntity)
    
    @Update
    suspend fun updateContact(contact: EmergencyContactEntity)
    
    @Delete
    suspend fun deleteContact(contact: EmergencyContactEntity)
}

@Dao
interface NotificationDao {
    @Query("SELECT * FROM notifications WHERE read = 0 ORDER BY priority DESC, timestamp DESC")
    fun getUnreadNotifications(): Flow<List<NotificationEntity>>
    
    @Query("SELECT * FROM notifications ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentNotifications(limit: Int = 50): Flow<List<NotificationEntity>>
    
    @Insert
    suspend fun insertNotification(notification: NotificationEntity)
    
    @Query("UPDATE notifications SET read = 1 WHERE id = :id")
    suspend fun markAsRead(id: String)
    
    @Query("UPDATE notifications SET read = 1")
    suspend fun markAllAsRead()
    
    @Delete
    suspend fun deleteNotification(notification: NotificationEntity)
    
    @Query("DELETE FROM notifications WHERE timestamp < :timestamp")
    suspend fun deleteOldNotifications(timestamp: Long)
}