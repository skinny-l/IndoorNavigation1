package com.example.indoornavigation.data.repositories

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.example.indoornavigation.data.database.AppDatabase
import com.example.indoornavigation.data.models.POI
import com.example.indoornavigation.data.models.POIEntity
import com.example.indoornavigation.data.models.Position
import com.example.indoornavigation.data.sync.GlobalPOISyncService
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Repository for managing Points of Interest (POIs) with persistent database storage and global cloud sync
 */
class POIRepository(private val context: Context) {

    // Use the main app database with encryption
    private val database = AppDatabase.getDatabase(context, useEncryption = true)
    private val poiDao = database.poiDao()

    // Global sync service - no authentication required
    private val globalSyncService = GlobalPOISyncService(context)

    // SharedPreferences for legacy data migration
    private val sharedPrefs: SharedPreferences =
        context.getSharedPreferences("poi_backup", Context.MODE_PRIVATE)
    private val gson = Gson()

    // Flag to track if sample data has been loaded
    private var sampleDataLoaded = false

    // Sync status
    private val _syncStatus = MutableStateFlow("Idle")
    val syncStatus: StateFlow<String> = _syncStatus.asStateFlow()

    companion object {
        private const val TAG = "POIRepository"
        private const val PREF_LAST_SYNC = "last_global_sync_time"
        private const val PREF_FIRST_LAUNCH = "first_launch_completed"
    }

    init {
        // Start global real-time sync (no authentication required)
        startGlobalRealtimeSync()

        // Auto-sync on app start
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d(TAG, "Performing automatic global sync on app start...")
                syncFromGlobalCloud()
            } catch (e: Exception) {
                Log.e(TAG, "Error in automatic global sync on start: ${e.message}")
            }
        }
    }

    /**
     * Get last sync time
     */
    fun getLastSyncTime(): Long {
        return sharedPrefs.getLong(PREF_LAST_SYNC, 0L)
    }

    /**
     * Get a POI by ID
     */
    suspend fun getPOIById(id: String): POI? = withContext(Dispatchers.IO) {
        ensureSampleDataLoaded()
        poiDao.getById(id)?.toPOI()
    }

    /**
     * Get all POIs
     */
    suspend fun getAllPOIs(): List<POI> = withContext(Dispatchers.IO) {
        ensureSampleDataLoaded()
        poiDao.getAll().map { it.toPOI() }
    }

    /**
     * Get POIs on a specific floor
     */
    suspend fun getPOIsForFloor(floor: Int): List<POI> = withContext(Dispatchers.IO) {
        ensureSampleDataLoaded()
        poiDao.getByFloor(floor).map { it.toPOI() }
    }

    /**
     * Get POIs of a specific type
     */
    suspend fun getPOIsOfType(type: POI.POIType): List<POI> = withContext(Dispatchers.IO) {
        ensureSampleDataLoaded()
        poiDao.getByCategory(type.name).map { it.toPOI() }
    }

    /**
     * Add or update a POI and sync to global cloud
     */
    suspend fun savePOI(poi: POI): Boolean = withContext(Dispatchers.IO) {
        try {
            // Save to local database
            poiDao.insert(poi.toPOIEntity())

            // Backup to SharedPreferences
            backupPOIToPreferences(poi)

            // Upload to global cloud (no auth required)
            val allPOIs = poiDao.getAll().map { it.toPOI() }
            syncToGlobalCloud(allPOIs)

            Log.d(TAG, "POI saved successfully: ${poi.name}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save POI: ${e.message}")
            e.printStackTrace()
            false
        }
    }

    /**
     * Delete a POI and sync to global cloud
     */
    suspend fun deletePOI(id: String): Boolean = withContext(Dispatchers.IO) {
        try {
            poiDao.deleteById(id)

            // Also remove from SharedPreferences backup
            removePOIFromPreferences(id)

            // Sync to global cloud
            val allPOIs = poiDao.getAll().map { it.toPOI() }
            syncToGlobalCloud(allPOIs)

            Log.d(TAG, "POI deleted successfully: $id")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete POI: ${e.message}")
            e.printStackTrace()
            false
        }
    }

    /**
     * Find nearest POI to a position
     */
    suspend fun findNearestPOI(position: Position, maxDistance: Double = Double.MAX_VALUE): POI? = withContext(Dispatchers.IO) {
        ensureSampleDataLoaded()
        poiDao.getByFloor(position.floor)
            .map { it.toPOI() }
            .minByOrNull { it.position.distanceTo(position) }
            ?.takeIf { it.position.distanceTo(position) <= maxDistance }
    }

    /**
     * Find POIs within a certain distance
     */
    suspend fun findPOIsWithinDistance(position: Position, distance: Double): List<POI> = withContext(Dispatchers.IO) {
        ensureSampleDataLoaded()
        poiDao.getByFloor(position.floor)
            .map { it.toPOI() }
            .filter { it.position.distanceTo(position) <= distance }
    }

    /**
     * Search POIs by name or description
     */
    suspend fun searchPOIs(query: String): List<POI> = withContext(Dispatchers.IO) {
        ensureSampleDataLoaded()
        poiDao.search("%$query%").map { it.toPOI() }
    }

    /**
     * Add multiple POIs at once with cloud sync
     */
    suspend fun savePOIs(pois: List<POI>): Boolean = withContext(Dispatchers.IO) {
        try {
            poiDao.insertAll(pois.map { it.toPOIEntity() })

            // Backup all POIs to SharedPreferences
            pois.forEach { backupPOIToPreferences(it) }

            // Sync to global cloud
            syncToGlobalCloud(pois)

            Log.d(TAG, "Multiple POIs saved successfully: ${pois.size}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save multiple POIs: ${e.message}")
            e.printStackTrace()
            false
        }
    }

    /**
     * Delete all user-created POIs with cloud sync
     */
    suspend fun deleteAllUserCreatedPOIs(): Boolean = withContext(Dispatchers.IO) {
        try {
            poiDao.deleteAllUserCreated()

            // Clear user-created POIs from SharedPreferences
            clearUserCreatedPOIsFromPreferences()

            // Sync to global cloud
            val remainingPOIs = poiDao.getAll().map { it.toPOI() }
            syncToGlobalCloud(remainingPOIs)

            Log.d(TAG, "All user-created POIs deleted successfully")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete user-created POIs: ${e.message}")
            e.printStackTrace()
            false
        }
    }

    /**
     * Migrate existing POIs to global storage
     */
    suspend fun migrateToGlobalStorage(): Boolean = withContext(Dispatchers.IO) {
        try {
            _syncStatus.value = "🔄 Migrating your POIs to global storage..."
            Log.d(TAG, "Starting migration to global storage")

            // Get all existing POIs
            val existingPOIs = poiDao.getAll().map { it.toPOI() }

            // Also check SharedPreferences backup
            val backupPOIs = importPOIsFromPreferences()

            // Combine all POIs
            val allPOIs = (existingPOIs + (if (backupPOIs) poiDao.getAll()
                .map { it.toPOI() } else emptyList())).distinctBy { it.id }

            if (allPOIs.isNotEmpty()) {
                Log.d(TAG, "Migrating ${allPOIs.size} POIs to global storage")
                val success = syncToGlobalCloud(allPOIs)

                if (success) {
                    _syncStatus.value = "✅ Migrated ${allPOIs.size} POIs to global storage"
                    Log.i(TAG, "Successfully migrated ${allPOIs.size} POIs to global storage")

                    // Mark migration as completed
                    sharedPrefs.edit().putBoolean("migration_completed", true).apply()
                    return@withContext true
                }
            } else {
                _syncStatus.value = "ℹ️ No POIs found to migrate"
                Log.i(TAG, "No POIs found to migrate")
            }
            false
        } catch (e: Exception) {
            _syncStatus.value = "❌ Migration error: ${e.message}"
            Log.e(TAG, "Error migrating to global storage: ${e.message}")
            false
        }
    }

    /**
     * Sync POIs to global cloud (like contributing to Waze)
     */
    suspend fun syncToGlobalCloud(pois: List<POI>? = null): Boolean = withContext(Dispatchers.IO) {
        try {
            _syncStatus.value = "Uploading to global cloud..."

            val poisToSync = pois ?: poiDao.getAll().map { it.toPOI() }
            Log.d(TAG, "Syncing ${poisToSync.size} POIs to global cloud")

            val success = globalSyncService.uploadGlobalPOIs(poisToSync)

            if (success) {
                sharedPrefs.edit().putLong(PREF_LAST_SYNC, System.currentTimeMillis()).apply()
                _syncStatus.value = "✅ Uploaded ${poisToSync.size} POIs to global cloud"
                Log.i(TAG, "Successfully synced ${poisToSync.size} POIs to global cloud")
            } else {
                _syncStatus.value = "❌ Failed to upload POIs"
                Log.e(TAG, "Failed to sync POIs to global cloud")
            }

            success
        } catch (e: Exception) {
            _syncStatus.value = "❌ Upload error: ${e.message}"
            Log.e(TAG, "Error syncing to global cloud: ${e.message}")
            false
        }
    }

    /**
     * Download POIs from global cloud (available to everyone)
     */
    suspend fun syncFromGlobalCloud(): Boolean = withContext(Dispatchers.IO) {
        try {
            _syncStatus.value = "📥 Downloading from global cloud..."
            Log.d(TAG, "Starting global cloud download")

            val globalPOIs = globalSyncService.downloadGlobalPOIs()
            Log.d(TAG, "Downloaded ${globalPOIs.size} POIs from global cloud")

            if (globalPOIs.isNotEmpty()) {
                // Replace all local POIs with global POIs
                poiDao.deleteAll()
                poiDao.insertAll(globalPOIs.map { it.toPOIEntity() })
                Log.d(TAG, "Replaced local POIs with ${globalPOIs.size} global POIs")

                // Also update backup
                exportPOIsToPreferences()

                sharedPrefs.edit().putLong(PREF_LAST_SYNC, System.currentTimeMillis()).apply()
                _syncStatus.value = "✅ Downloaded ${globalPOIs.size} global POIs"

                Log.i(TAG, "Successfully synced ${globalPOIs.size} POIs from global cloud")
                true
            } else {
                _syncStatus.value = "ℹ️ No global POIs found"
                Log.i(TAG, "No global POIs found in cloud")
                false
            }
        } catch (e: Exception) {
            _syncStatus.value = "❌ Download error: ${e.message}"
            Log.e(TAG, "Error syncing from global cloud: ${e.message}")
            e.printStackTrace()
            false
        }
    }

    /**
     * Force sync - downloads from global cloud regardless of existing data
     */
    suspend fun forceSyncFromGlobalCloud(): Boolean = withContext(Dispatchers.IO) {
        try {
            _syncStatus.value = "Force downloading from global cloud..."
            Log.d(TAG, "Starting force sync from global cloud")

            val globalPOIs = globalSyncService.downloadGlobalPOIs()
            Log.d(TAG, "Force downloaded ${globalPOIs.size} POIs from global cloud")

            if (globalPOIs.isNotEmpty()) {
                // Clear all existing data and replace with global cloud data
                poiDao.deleteAll()
                poiDao.insertAll(globalPOIs.map { it.toPOIEntity() })
                Log.d(TAG, "Replaced all local data with ${globalPOIs.size} global POIs")

                // Also update backup
                exportPOIsToPreferences()

                sharedPrefs.edit().putLong(PREF_LAST_SYNC, System.currentTimeMillis()).apply()
                _syncStatus.value = "Force sync completed: ${globalPOIs.size} POIs"

                Log.i(TAG, "Force sync completed: ${globalPOIs.size} POIs")
                true
            } else {
                _syncStatus.value = "No global cloud data found"
                Log.i(TAG, "Force sync: No POIs found in global cloud")
                false
            }
        } catch (e: Exception) {
            _syncStatus.value = "Force sync error: ${e.message}"
            Log.e(TAG, "Error in force sync: ${e.message}")
            e.printStackTrace()
            false
        }
    }

    /**
     * Start global real-time sync listener
     */
    private fun startGlobalRealtimeSync() {
        globalSyncService.startGlobalRealtimeSync { globalPOIs ->
            // Handle real-time updates from global cloud
            try {
                CoroutineScope(Dispatchers.IO).launch {
                    // Update local database with global POIs
                    poiDao.deleteAll()
                    poiDao.insertAll(globalPOIs.map { it.toPOIEntity() })

                    Log.d(TAG, "Real-time sync updated ${globalPOIs.size} global POIs")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error handling global real-time sync: ${e.message}")
            }
        }
    }

    /**
     * Stop global real-time sync listener
     */
    private fun stopGlobalRealtimeSync() {
        globalSyncService.stopGlobalRealtimeSync()
    }

    /**
     * Export POIs to SharedPreferences for backup/transfer
     */
    suspend fun exportPOIsToPreferences(): Boolean = withContext(Dispatchers.IO) {
        try {
            val allPOIs = poiDao.getAll().map { it.toPOI() }
            val json = gson.toJson(allPOIs)
            sharedPrefs.edit().putString("all_pois", json).apply()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Import POIs from SharedPreferences backup
     */
    suspend fun importPOIsFromPreferences(): Boolean = withContext(Dispatchers.IO) {
        try {
            val json = sharedPrefs.getString("all_pois", null)
            if (json != null) {
                val type = object : TypeToken<List<POI>>() {}.type
                val pois: List<POI> = gson.fromJson(json, type)
                poiDao.insertAll(pois.map { it.toPOIEntity() })
                true
            } else {
                false
            }
        } catch (e: JsonSyntaxException) {
            e.printStackTrace()
            false
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Check if backup data exists
     */
    fun hasBackupData(): Boolean {
        return sharedPrefs.contains("all_pois")
    }

    /**
     * Clear all backup data
     */
    fun clearBackupData() {
        sharedPrefs.edit().clear().apply()
    }

    /**
     * Ensure sample data is loaded on first access
     */
    private suspend fun ensureSampleDataLoaded() {
        if (!sampleDataLoaded) {
            val existingPOIs = poiDao.getAll()
            Log.d(TAG, "Existing POIs in database: ${existingPOIs.size}")

            if (existingPOIs.isEmpty()) {
                Log.d(TAG, "Database is empty, checking for global POIs...")

                // First try to download from global cloud
                val globalSyncSuccess = syncFromGlobalCloud()
                Log.d(TAG, "Global sync success: $globalSyncSuccess")

                // If no global data found, check if this is first launch
                val isFirstLaunch = !sharedPrefs.getBoolean(PREF_FIRST_LAUNCH, false)

                if (!globalSyncSuccess && isFirstLaunch) {
                    Log.d(TAG, "First launch detected, loading sample POIs...")
                    loadSamplePOIs()

                    // Upload samples to global cloud for everyone
                    val samplePOIs = poiDao.getAll().map { it.toPOI() }
                    Log.d(TAG, "Uploading ${samplePOIs.size} sample POIs to global cloud...")
                    syncToGlobalCloud(samplePOIs)

                    // Mark first launch as completed
                    sharedPrefs.edit().putBoolean(PREF_FIRST_LAUNCH, true).apply()
                } else if (!globalSyncSuccess && hasBackupData()) {
                    Log.d(TAG, "No global data, importing from local backup...")
                    importPOIsFromPreferences()

                    // Upload backup to global cloud
                    val backupPOIs = poiDao.getAll().map { it.toPOI() }
                    if (backupPOIs.isNotEmpty()) {
                        syncToGlobalCloud(backupPOIs)
                    }
                }
            } else {
                Log.d(TAG, "Found ${existingPOIs.size} existing POIs in database")

                // Check if we need to migrate to global storage
                val migrationCompleted = sharedPrefs.getBoolean("migration_completed", false)
                if (!migrationCompleted) {
                    Log.d(
                        TAG,
                        "Migration not completed, uploading existing POIs to global cloud..."
                    )
                    val localPOIs = existingPOIs.map { it.toPOI() }
                    syncToGlobalCloud(localPOIs)
                }
            }
            sampleDataLoaded = true
        }
    }

    /**
     * Backup individual POI to SharedPreferences
     */
    private fun backupPOIToPreferences(poi: POI) {
        try {
            val json = gson.toJson(poi)
            sharedPrefs.edit().putString("poi_${poi.id}", json).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Remove POI from SharedPreferences backup
     */
    private fun removePOIFromPreferences(poiId: String) {
        sharedPrefs.edit().remove("poi_$poiId").apply()
    }

    /**
     * Clear user-created POIs from SharedPreferences
     */
    private fun clearUserCreatedPOIsFromPreferences() {
        val editor = sharedPrefs.edit()
        val allKeys = sharedPrefs.all.keys
        for (key in allKeys) {
            if (key.startsWith("poi_")) {
                try {
                    val json = sharedPrefs.getString(key, null)
                    if (json != null) {
                        val poi = gson.fromJson(json, POI::class.java)
                        if (poi.metadata["user_created"] == "true") {
                            editor.remove(key)
                        }
                    }
                } catch (e: Exception) {
                    // Skip malformed entries
                }
            }
        }
        editor.apply()
    }

    /**
     * Load sample POIs into the database (only if database is empty)
     */
    private suspend fun loadSamplePOIs() {
        val samplePOIs = listOf(
            POI(
                id = "lobby",
                name = "Main Lobby",
                description = "Main entrance lobby with information desk",
                position = Position(35.0, 55.0, 0),
                type = POI.POIType.ENTRANCE,
                metadata = mapOf(
                    "opening_hours" to "24/7",
                    "accessible" to "true",
                    "floor_name" to "Ground Floor"
                )
            ),
            POI(
                id = "cafeteria",
                name = "Student Cafeteria",
                description = "Main cafeteria serving meals and snacks",
                position = Position(25.0, 25.0, 0),
                type = POI.POIType.FOOD,
                metadata = mapOf(
                    "opening_hours" to "7:00 AM - 9:00 PM",
                    "accessible" to "true",
                    "contact" to "ext. 2345"
                )
            ),
            POI(
                id = "conference1",
                name = "Conference Room A",
                description = "Large conference room with projector",
                position = Position(55.0, 35.0, 0),
                type = POI.POIType.ROOM,
                metadata = mapOf(
                    "capacity" to "20",
                    "accessible" to "true",
                    "equipment" to "projector, whiteboard"
                )
            ),
            POI(
                id = "elevator1",
                name = "Main Elevator",
                description = "Central elevator serving all floors",
                position = Position(40.0, 45.0, 0),
                type = POI.POIType.ELEVATOR,
                metadata = mapOf(
                    "accessible" to "true",
                    "floors" to "0,1,2,3"
                )
            ),
            POI(
                id = "restroom_gf",
                name = "Ground Floor Restrooms",
                description = "Men's and women's restrooms",
                position = Position(45.0, 50.0, 0),
                type = POI.POIType.RESTROOM,
                metadata = mapOf(
                    "accessible" to "true",
                    "facilities" to "accessible stall available"
                )
            ),
            POI(
                id = "lab101",
                name = "Computer Lab 101",
                description = "Computer lab with 30 workstations",
                position = Position(60.0, 25.0, 0),
                type = POI.POIType.CLASSROOM,
                metadata = mapOf(
                    "capacity" to "30",
                    "accessible" to "true",
                    "equipment" to "computers, printer"
                )
            ),
            POI(
                id = "office201",
                name = "Academic Office 201",
                description = "Faculty and staff offices",
                position = Position(25.0, 15.0, 1),
                type = POI.POIType.OFFICE,
                metadata = mapOf(
                    "floor_name" to "First Floor",
                    "accessible" to "true",
                    "contact" to "ext. 1234"
                )
            ),
            POI(
                id = "library",
                name = "Main Library",
                description = "Library with study areas and books",
                position = Position(45.0, 25.0, 1),
                type = POI.POIType.ROOM,
                metadata = mapOf(
                    "floor_name" to "First Floor",
                    "opening_hours" to "8:00 AM - 10:00 PM",
                    "accessible" to "true"
                )
            ),
            POI(
                id = "emergency_exit_1",
                name = "Emergency Exit East",
                description = "Emergency exit on east side",
                position = Position(70.0, 55.0, 0),
                type = POI.POIType.EXIT,
                metadata = mapOf(
                    "accessible" to "true",
                    "emergency_only" to "true"
                )
            ),
            POI(
                id = "info_desk",
                name = "Information Desk",
                description = "Information and help desk",
                position = Position(30.0, 50.0, 0),
                type = POI.POIType.INFO,
                metadata = mapOf(
                    "opening_hours" to "8:00 AM - 6:00 PM",
                    "accessible" to "true",
                    "services" to "directions, general information"
                )
            )
        )

        // Insert sample POIs into database
        poiDao.insertAll(samplePOIs.map { it.toPOIEntity() })

        // Also backup to SharedPreferences for future transfers
        exportPOIsToPreferences()

        // Upload to global cloud
        syncToGlobalCloud(samplePOIs)
    }

    /**
     * Clean up resources
     */
    fun cleanup() {
        globalSyncService.cleanup()
    }
}

/**
 * Extension function to convert POI to POIEntity for database storage
 */
private fun POI.toPOIEntity(): POIEntity {
    return POIEntity(
        id = this.id,
        name = this.name,
        x = this.position.x,
        y = this.position.y,
        floorId = this.position.floor,
        category = this.type.name,
        description = this.description,
        isUserCreated = this.metadata["user_created"] == "true",
        icon = android.R.drawable.ic_menu_mylocation // Default icon
    )
}

/**
 * Extension function to convert POIEntity to POI
 */
private fun POIEntity.toPOI(): POI {
    return POI(
        id = this.id,
        name = this.name,
        description = this.description,
        position = Position(this.x, this.y, this.floorId),
        type = try {
            POI.POIType.valueOf(this.category)
        } catch (e: Exception) {
            POI.POIType.CUSTOM
        },
        metadata = mapOf(
            "user_created" to this.isUserCreated.toString(),
            "floor_name" to "Floor ${this.floorId}"
        )
    )
}
