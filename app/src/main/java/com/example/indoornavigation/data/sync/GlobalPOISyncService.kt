package com.example.indoornavigation.data.sync

import android.content.Context
import android.util.Log
import com.example.indoornavigation.data.models.POI
import com.example.indoornavigation.data.models.Position
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/**
 * Global POI sync service - POIs are shared globally like Waze
 * No authentication required to access POIs
 */
class GlobalPOISyncService(private val context: Context) {

    private val firestore = FirebaseFirestore.getInstance()

    private val _syncStatus = MutableStateFlow(SyncStatus.IDLE)
    val syncStatus: StateFlow<SyncStatus> = _syncStatus.asStateFlow()

    private val _lastSyncTime = MutableStateFlow(0L)
    val lastSyncTime: StateFlow<Long> = _lastSyncTime.asStateFlow()

    private var syncListener: ListenerRegistration? = null

    companion object {
        private const val TAG = "GlobalPOISyncService"

        // Global collections - accessible to everyone
        private const val COLLECTION_GLOBAL_POIS = "global_building_pois"
        private const val COLLECTION_BUILDING_DATA = "building_data"
        private const val BUILDING_ID = "cs1_building" // Your building identifier
    }

    enum class SyncStatus {
        IDLE,
        SYNCING,
        SUCCESS,
        ERROR
    }

    /**
     * Upload POIs to global storage (like contributing to Waze)
     */
    suspend fun uploadGlobalPOIs(pois: List<POI>): Boolean = withContext(Dispatchers.IO) {
        _syncStatus.value = SyncStatus.SYNCING

        try {
            Log.d(TAG, "=== STARTING GLOBAL POI UPLOAD ===")
            Log.d(TAG, "Uploading ${pois.size} POIs to global storage")

            if (pois.isEmpty()) {
                Log.w(TAG, "No POIs to upload")
                _syncStatus.value = SyncStatus.SUCCESS
                return@withContext true
            }

            val globalPOIsCollection = firestore.collection(COLLECTION_GLOBAL_POIS)

            // Upload each POI to global storage with timeout
            for ((index, poi) in pois.withIndex()) {
                Log.d(TAG, "Uploading POI ${index + 1}/${pois.size}: ${poi.name}")

                val poiData = poi.toGlobalFirebaseMap()
                Log.d(TAG, "POI data: $poiData")

                kotlinx.coroutines.withTimeout(10000) { // 10 second timeout per POI
                    globalPOIsCollection.document(poi.id).set(poiData).await()
                }

                Log.d(TAG, "Successfully uploaded POI: ${poi.name}")
            }

            // Update building metadata
            Log.d(TAG, "Updating building metadata...")
            val buildingData = mapOf(
                "buildingId" to BUILDING_ID,
                "lastUpdated" to System.currentTimeMillis(),
                "totalPOIs" to pois.size,
                "version" to 1
            )

            kotlinx.coroutines.withTimeout(10000) { // 10 second timeout
                firestore.collection(COLLECTION_BUILDING_DATA)
                    .document(BUILDING_ID)
                    .set(buildingData)
                    .await()
            }

            _lastSyncTime.value = System.currentTimeMillis()
            _syncStatus.value = SyncStatus.SUCCESS

            Log.i(TAG, "=== SUCCESSFULLY UPLOADED ${pois.size} POIs TO GLOBAL STORAGE ===")
            true

        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            Log.e(TAG, "Timeout uploading POIs to global storage")
            _syncStatus.value = SyncStatus.ERROR
            false
        } catch (e: Exception) {
            Log.e(TAG, "=== FAILED TO UPLOAD POIs TO GLOBAL STORAGE ===")
            Log.e(TAG, "Error: ${e.message}")
            e.printStackTrace()
            _syncStatus.value = SyncStatus.ERROR
            false
        }
    }

    /**
     * Download global POIs (available to everyone, no auth required)
     */
    suspend fun downloadGlobalPOIs(): List<POI> = withContext(Dispatchers.IO) {
        _syncStatus.value = SyncStatus.SYNCING

        try {
            Log.d(TAG, "Downloading global POIs for building: $BUILDING_ID")

            val globalPOIsCollection = firestore.collection(COLLECTION_GLOBAL_POIS)

            // Add timeout handling
            val snapshot = withTimeout(30000) { // 30 second timeout
                globalPOIsCollection.get().await()
            }

            val pois = mutableListOf<POI>()

            Log.d(TAG, "Found ${snapshot.documents.size} documents in global POI collection")

            for (document in snapshot.documents) {
                try {
                    Log.d(TAG, "Processing document: ${document.id}")
                    val poi = document.data?.toGlobalPOI()
                    if (poi != null) {
                        pois.add(poi)
                        Log.d(TAG, "Successfully parsed POI: ${poi.name}")
                    } else {
                        Log.w(TAG, "Failed to parse POI from document ${document.id}")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error parsing POI from document ${document.id}: ${e.message}")
                }
            }

            _lastSyncTime.value = System.currentTimeMillis()
            _syncStatus.value = SyncStatus.SUCCESS

            Log.i(TAG, "Successfully downloaded ${pois.size} global POIs")
            pois

        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            Log.e(TAG, "Timeout downloading global POIs (30s)")
            _syncStatus.value = SyncStatus.ERROR
            emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to download global POIs: ${e.message}")
            e.printStackTrace()
            _syncStatus.value = SyncStatus.ERROR
            emptyList()
        }
    }

    /**
     * Start real-time listener for global POI updates
     */
    fun startGlobalRealtimeSync(onPOIsUpdated: (List<POI>) -> Unit) {
        syncListener?.remove()

        Log.d(TAG, "Starting real-time sync for global POIs")

        syncListener = firestore.collection(COLLECTION_GLOBAL_POIS)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Global real-time sync error: ${error.message}")
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    val pois = mutableListOf<POI>()

                    for (document in snapshot.documents) {
                        try {
                            val poi = document.data?.toGlobalPOI()
                            if (poi != null) {
                                pois.add(poi)
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to parse real-time global POI: ${e.message}")
                        }
                    }

                    Log.d(TAG, "Global real-time sync: received ${pois.size} POIs")
                    onPOIsUpdated(pois)
                }
            }
    }

    /**
     * Stop real-time sync listener
     */
    fun stopGlobalRealtimeSync() {
        syncListener?.remove()
        syncListener = null
        Log.d(TAG, "Stopped global real-time sync")
    }

    /**
     * Get building information
     */
    suspend fun getBuildingInfo(): Map<String, Any>? = withContext(Dispatchers.IO) {
        try {
            val buildingDoc = firestore.collection(COLLECTION_BUILDING_DATA)
                .document(BUILDING_ID)
                .get()
                .await()

            buildingDoc.data
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get building info: ${e.message}")
            null
        }
    }

    /**
     * Clean up resources
     */
    fun cleanup() {
        stopGlobalRealtimeSync()
    }
}

/**
 * Extension function to convert POI to global Firebase-compatible map
 */
private fun POI.toGlobalFirebaseMap(): Map<String, Any> {
    return mapOf(
        "id" to id,
        "name" to name,
        "description" to description,
        "x" to position.x,
        "y" to position.y,
        "floor" to position.floor,
        "type" to type.name,
        "imageUrl" to (imageUrl ?: ""),
        "metadata" to metadata,
        "buildingId" to "cs1_building",
        "isGlobal" to true,
        "createdAt" to System.currentTimeMillis(),
        "lastModified" to System.currentTimeMillis()
    )
}

/**
 * Extension function to convert global Firebase map to POI
 */
private fun Map<String, Any>.toGlobalPOI(): POI? {
    return try {
        POI(
            id = this["id"] as String,
            name = this["name"] as String,
            description = this["description"] as String,
            position = Position(
                x = (this["x"] as Number).toDouble(),
                y = (this["y"] as Number).toDouble(),
                floor = (this["floor"] as Number).toInt()
            ),
            type = try {
                POI.POIType.valueOf(this["type"] as String)
            } catch (e: Exception) {
                POI.POIType.CUSTOM
            },
            imageUrl = (this["imageUrl"] as? String)?.takeIf { it.isNotEmpty() },
            metadata = (this["metadata"] as? Map<String, String>) ?: emptyMap()
        )
    } catch (e: Exception) {
        Log.e("GlobalPOISyncService", "Failed to convert global Firebase data to POI: ${e.message}")
        null
    }
}
