package com.example.indoornavigation.data.sync

import android.content.Context
import android.util.Log
import com.example.indoornavigation.data.models.POI
import com.example.indoornavigation.data.models.Position
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * Firebase-based POI synchronization service
 * Syncs POI data across devices for the same user
 */
class POISyncService(private val context: Context) {

    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private val _syncStatus = MutableStateFlow(SyncStatus.IDLE)
    val syncStatus: StateFlow<SyncStatus> = _syncStatus.asStateFlow()

    private val _lastSyncTime = MutableStateFlow(0L)
    val lastSyncTime: StateFlow<Long> = _lastSyncTime.asStateFlow()

    private var syncListener: ListenerRegistration? = null

    companion object {
        private const val TAG = "POISyncService"
        private const val COLLECTION_USER_POIS = "user_pois"
        private const val COLLECTION_GLOBAL_POIS = "global_pois"
    }

    enum class SyncStatus {
        IDLE,
        SYNCING,
        SUCCESS,
        ERROR
    }

    /**
     * Upload user's POIs to Firebase
     */
    suspend fun uploadPOIs(pois: List<POI>): Boolean = withContext(Dispatchers.IO) {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            Log.w(TAG, "No authenticated user for POI upload")
            return@withContext false
        }

        _syncStatus.value = SyncStatus.SYNCING

        try {
            val userPOIsCollection = firestore.collection(COLLECTION_USER_POIS)
                .document(currentUser.uid)
                .collection("pois")

            // Upload each POI
            for (poi in pois) {
                val poiData = poi.toFirebaseMap()
                userPOIsCollection.document(poi.id).set(poiData).await()
                Log.d(TAG, "Uploaded POI: ${poi.name}")
            }

            // Update last sync time
            val syncData = mapOf(
                "lastSync" to System.currentTimeMillis(),
                "deviceId" to getDeviceId(),
                "poiCount" to pois.size
            )

            firestore.collection(COLLECTION_USER_POIS)
                .document(currentUser.uid)
                .set(syncData)
                .await()

            _lastSyncTime.value = System.currentTimeMillis()
            _syncStatus.value = SyncStatus.SUCCESS

            Log.i(TAG, "Successfully uploaded ${pois.size} POIs")
            true

        } catch (e: Exception) {
            Log.e(TAG, "Failed to upload POIs: ${e.message}")
            _syncStatus.value = SyncStatus.ERROR
            false
        }
    }

    /**
     * Download user's POIs from Firebase
     */
    suspend fun downloadPOIs(): List<POI> = withContext(Dispatchers.IO) {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            Log.w(TAG, "No authenticated user for POI download")
            return@withContext emptyList()
        }

        _syncStatus.value = SyncStatus.SYNCING

        try {
            val userPOIsCollection = firestore.collection(COLLECTION_USER_POIS)
                .document(currentUser.uid)
                .collection("pois")

            val snapshot = userPOIsCollection.get().await()
            val pois = mutableListOf<POI>()

            for (document in snapshot.documents) {
                try {
                    val poi = document.data?.toPOI()
                    if (poi != null) {
                        pois.add(poi)
                        Log.d(TAG, "Downloaded POI: ${poi.name}")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to parse POI from document ${document.id}: ${e.message}")
                }
            }

            _lastSyncTime.value = System.currentTimeMillis()
            _syncStatus.value = SyncStatus.SUCCESS

            Log.i(TAG, "Successfully downloaded ${pois.size} POIs")
            pois

        } catch (e: Exception) {
            Log.e(TAG, "Failed to download POIs: ${e.message}")
            _syncStatus.value = SyncStatus.ERROR
            emptyList()
        }
    }

    /**
     * Check if newer POI data is available on server
     */
    suspend fun checkForUpdates(): Boolean = withContext(Dispatchers.IO) {
        val currentUser = auth.currentUser ?: return@withContext false

        try {
            val userDoc = firestore.collection(COLLECTION_USER_POIS)
                .document(currentUser.uid)
                .get()
                .await()

            val serverLastSync = userDoc.getLong("lastSync") ?: 0L
            val localLastSync = _lastSyncTime.value

            serverLastSync > localLastSync

        } catch (e: Exception) {
            Log.e(TAG, "Failed to check for updates: ${e.message}")
            false
        }
    }

    /**
     * Start real-time sync listener
     */
    fun startRealtimeSync(onPOIsUpdated: (List<POI>) -> Unit) {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            Log.w(TAG, "No authenticated user for real-time sync")
            return
        }

        syncListener?.remove()

        syncListener = firestore.collection(COLLECTION_USER_POIS)
            .document(currentUser.uid)
            .collection("pois")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Real-time sync error: ${error.message}")
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    val pois = mutableListOf<POI>()

                    for (document in snapshot.documents) {
                        try {
                            val poi = document.data?.toPOI()
                            if (poi != null) {
                                pois.add(poi)
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to parse real-time POI: ${e.message}")
                        }
                    }

                    Log.d(TAG, "Real-time sync: received ${pois.size} POIs")
                    onPOIsUpdated(pois)
                }
            }
    }

    /**
     * Stop real-time sync listener
     */
    fun stopRealtimeSync() {
        syncListener?.remove()
        syncListener = null
    }

    /**
     * Upload global POIs (admin only)
     */
    suspend fun uploadGlobalPOIs(pois: List<POI>): Boolean = withContext(Dispatchers.IO) {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            Log.w(TAG, "No authenticated user for global POI upload")
            return@withContext false
        }

        _syncStatus.value = SyncStatus.SYNCING

        try {
            val globalPOIsCollection = firestore.collection(COLLECTION_GLOBAL_POIS)

            // Upload each global POI
            for (poi in pois) {
                val poiData = poi.toFirebaseMap().toMutableMap()
                poiData["uploadedBy"] = currentUser.uid
                poiData["uploadedAt"] = System.currentTimeMillis()

                globalPOIsCollection.document(poi.id).set(poiData).await()
                Log.d(TAG, "Uploaded global POI: ${poi.name}")
            }

            _syncStatus.value = SyncStatus.SUCCESS

            Log.i(TAG, "Successfully uploaded ${pois.size} global POIs")
            true

        } catch (e: Exception) {
            Log.e(TAG, "Failed to upload global POIs: ${e.message}")
            _syncStatus.value = SyncStatus.ERROR
            false
        }
    }

    /**
     * Download global POIs (available to all users)
     */
    suspend fun downloadGlobalPOIs(): List<POI> = withContext(Dispatchers.IO) {
        _syncStatus.value = SyncStatus.SYNCING

        try {
            val globalPOIsCollection = firestore.collection(COLLECTION_GLOBAL_POIS)
            val snapshot = globalPOIsCollection.get().await()
            val pois = mutableListOf<POI>()

            for (document in snapshot.documents) {
                try {
                    val poi = document.data?.toPOI()
                    if (poi != null) {
                        pois.add(poi)
                        Log.d(TAG, "Downloaded global POI: ${poi.name}")
                    }
                } catch (e: Exception) {
                    Log.w(
                        TAG,
                        "Failed to parse global POI from document ${document.id}: ${e.message}"
                    )
                }
            }

            _syncStatus.value = SyncStatus.SUCCESS

            Log.i(TAG, "Successfully downloaded ${pois.size} global POIs")
            pois

        } catch (e: Exception) {
            Log.e(TAG, "Failed to download global POIs: ${e.message}")
            _syncStatus.value = SyncStatus.ERROR
            emptyList()
        }
    }

    /**
     * Get device ID for sync tracking
     */
    private fun getDeviceId(): String {
        val sharedPrefs = context.getSharedPreferences("sync_prefs", Context.MODE_PRIVATE)
        var deviceId = sharedPrefs.getString("device_id", null)

        if (deviceId == null) {
            deviceId = "device_${System.currentTimeMillis()}_${(1000..9999).random()}"
            sharedPrefs.edit().putString("device_id", deviceId).apply()
        }

        return deviceId
    }

    /**
     * Clean up resources
     */
    fun cleanup() {
        stopRealtimeSync()
    }
}

/**
 * Extension function to convert POI to Firebase-compatible map
 */
private fun POI.toFirebaseMap(): Map<String, Any> {
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
        "lastModified" to System.currentTimeMillis()
    )
}

/**
 * Extension function to convert Firebase map to POI
 */
private fun Map<String, Any>.toPOI(): POI? {
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
        Log.e("POISyncService", "Failed to convert Firebase data to POI: ${e.message}")
        null
    }
}