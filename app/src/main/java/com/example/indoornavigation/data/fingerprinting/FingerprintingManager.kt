package com.example.indoornavigation.data.fingerprinting

import android.util.Log
import com.example.indoornavigation.data.models.Beacon
import com.example.indoornavigation.data.models.Fingerprint
import com.example.indoornavigation.data.models.Position
import com.example.indoornavigation.data.models.WifiAccessPoint
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Manager for RSSI fingerprinting-based positioning
 */
class FingerprintingManager(private val buildingId: String) {
    
    private val TAG = "FingerprintingManager"
    
    // Firestore reference
    private val db = FirebaseFirestore.getInstance()
    
    // Cached fingerprints
    private val cachedFingerprints = mutableListOf<Fingerprint>()
    
    // Nearest neighbor count for positioning
    private var kNearestNeighbors = 3
    
    /**
     * Load fingerprints from Firebase
     */
    suspend fun loadFingerprints(floor: Int): List<Fingerprint> {
        try {
            // Check if fingerprints are already cached
            if (cachedFingerprints.isNotEmpty() && 
                cachedFingerprints.any { it.position.floor == floor }) {
                return cachedFingerprints.filter { it.position.floor == floor }
            }
            
            // Query Firestore
            val querySnapshot = db.collection("buildings")
                .document(buildingId)
                .collection("fingerprints")
                .whereEqualTo("floor", floor)
                .get()
                .await()
            
            // Parse fingerprints
            val fingerprints = querySnapshot.documents.mapNotNull { doc ->
                try {
                    // Parse position
                    val posMap = doc.get("position") as? Map<*, *> ?: return@mapNotNull null
                    val x = (posMap["x"] as? Number)?.toDouble() ?: return@mapNotNull null
                    val y = (posMap["y"] as? Number)?.toDouble() ?: return@mapNotNull null
                    val floor = (posMap["floor"] as? Number)?.toInt() ?: return@mapNotNull null
                    val position = Position(x, y, floor)
                    
                    // Parse BLE signatures
                    val bleMap = doc.get("bleSignatures") as? Map<*, *> ?: emptyMap<String, Any>()
                    val bleSignatures = bleMap.entries.associate { 
                        (it.key as String) to (it.value as Number).toInt() 
                    }
                    
                    // Parse WiFi signatures
                    val wifiMap = doc.get("wifiSignatures") as? Map<*, *> ?: emptyMap<String, Any>()
                    val wifiSignatures = wifiMap.entries.associate { 
                        (it.key as String) to (it.value as Number).toInt() 
                    }
                    
                    // Create fingerprint
                    Fingerprint(
                        id = doc.id,
                        position = position,
                        bleSignatures = bleSignatures,
                        wifiSignatures = wifiSignatures,
                        timestamp = doc.getLong("timestamp") ?: System.currentTimeMillis()
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing fingerprint", e)
                    null
                }
            }
            
            // Update cache
            cachedFingerprints.addAll(fingerprints)
            
            Log.d(TAG, "Loaded ${fingerprints.size} fingerprints for floor $floor")
            return fingerprints
        } catch (e: Exception) {
            Log.e(TAG, "Error loading fingerprints", e)
            return emptyList()
        }
    }
    
    /**
     * Get position using fingerprinting
     */
    suspend fun getPosition(
        bleBeacons: Map<String, Beacon>,
        wifiAccessPoints: Map<String, WifiAccessPoint>,
        floor: Int
    ): Position? {
        // Ensure fingerprints are loaded
        val fingerprints = loadFingerprints(floor)
        if (fingerprints.isEmpty()) {
            Log.d(TAG, "No fingerprints available for floor $floor")
            return null
        }
        
        // Extract signatures
        val bleSignatures = bleBeacons.mapValues { it.value.rssi }
        val wifiSignatures = wifiAccessPoints.mapValues { it.value.rssi }
        
        // Calculate distances to all fingerprints
        val fingerprintDistances = fingerprints.map { fingerprint ->
            val bleDistance = calculateEuclideanDistance(bleSignatures, fingerprint.bleSignatures)
            val wifiDistance = calculateEuclideanDistance(wifiSignatures, fingerprint.wifiSignatures)
            
            // Weighted combination of distances
            val combinedDistance = 0.7 * bleDistance + 0.3 * wifiDistance
            
            Pair(fingerprint, combinedDistance)
        }
        
        // Get K nearest neighbors
        val nearestNeighbors = fingerprintDistances
            .sortedBy { it.second }
            .take(kNearestNeighbors)
        
        if (nearestNeighbors.isEmpty()) {
            Log.d(TAG, "No matching fingerprints found")
            return null
        }
        
        // Calculate weighted average position
        var totalWeight = 0.0
        var weightedX = 0.0
        var weightedY = 0.0
        
        for ((fingerprint, distance) in nearestNeighbors) {
            // Convert distance to weight (closer = higher weight)
            val weight = if (distance <= 0.1) {
                // Very close match, high weight
                10.0
            } else {
                // Inverse distance weighting
                1.0 / distance
            }
            
            totalWeight += weight
            weightedX += fingerprint.position.x * weight
            weightedY += fingerprint.position.y * weight
        }
        
        // Calculate final position
        return if (totalWeight > 0) {
            Position(
                x = weightedX / totalWeight,
                y = weightedY / totalWeight,
                floor = floor
            )
        } else {
            // Fallback to closest fingerprint
            nearestNeighbors.firstOrNull()?.first?.position
        }
    }
    
    /**
     * Calculate Euclidean distance between RSSI signatures
     */
    private fun calculateEuclideanDistance(
        signatures1: Map<String, Int>,
        signatures2: Map<String, Int>
    ): Double {
        // Find common IDs
        val commonIds = signatures1.keys.intersect(signatures2.keys)
        
        if (commonIds.isEmpty()) {
            // No common signatures, maximum distance
            return Double.MAX_VALUE
        }
        
        // Calculate Euclidean distance
        var sumSquaredDiff = 0.0
        
        for (id in commonIds) {
            val rssi1 = signatures1[id] ?: continue
            val rssi2 = signatures2[id] ?: continue
            
            val diff = rssi1 - rssi2
            sumSquaredDiff += diff.toDouble().pow(2)
        }
        
        return sqrt(sumSquaredDiff / commonIds.size)
    }
    
    /**
     * Save a new fingerprint
     */
    suspend fun saveFingerprint(
        position: Position,
        bleBeacons: Map<String, Beacon>,
        wifiAccessPoints: Map<String, WifiAccessPoint>
    ): Boolean {
        try {
            // Extract signatures
            val bleSignatures = bleBeacons.mapValues { it.value.rssi }
            val wifiSignatures = wifiAccessPoints.mapValues { it.value.rssi }
            
            // Create fingerprint data
            val fingerprintData = hashMapOf(
                "position" to hashMapOf(
                    "x" to position.x,
                    "y" to position.y,
                    "floor" to position.floor
                ),
                "bleSignatures" to bleSignatures,
                "wifiSignatures" to wifiSignatures,
                "timestamp" to System.currentTimeMillis()
            )
            
            // Save to Firestore
            db.collection("buildings")
                .document(buildingId)
                .collection("fingerprints")
                .add(fingerprintData)
                .await()
            
            Log.d(TAG, "Fingerprint saved successfully")
            
            // Clear cache to force reload
            cachedFingerprints.clear()
            
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error saving fingerprint", e)
            return false
        }
    }
    
    /**
     * Set K nearest neighbors parameter
     */
    fun setKNearestNeighbors(k: Int) {
        kNearestNeighbors = k
    }
    
    /**
     * Clear fingerprint cache
     */
    fun clearCache() {
        cachedFingerprints.clear()
    }
}