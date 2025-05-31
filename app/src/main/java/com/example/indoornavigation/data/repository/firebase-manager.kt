package com.example.indoornavigation.data.repository

import android.util.Log
import com.example.indoornavigation.data.models.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * Manager class for Firebase operations
 */
class FirebaseManager {
    private val TAG = "FirebaseManager"
    
    // Firebase instances
    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()
    
    // Authentication state
    private val _currentUser = MutableStateFlow<FirebaseUser?>(auth.currentUser)
    val currentUser: Flow<FirebaseUser?> = _currentUser.asStateFlow()
    
    // Building data
    private val _currentBuilding = MutableStateFlow<Building?>(null)
    val currentBuilding: Flow<Building?> = _currentBuilding.asStateFlow()
    
    // Points of interest
    private val _pointsOfInterest = MutableStateFlow<List<PointOfInterest>>(emptyList())
    val pointsOfInterest: Flow<List<PointOfInterest>> = _pointsOfInterest.asStateFlow()
    
    // Navigation graph
    private val _navigationNodes = MutableStateFlow<Map<String, NavNode>>(emptyMap())
    val navigationNodes: Flow<Map<String, NavNode>> = _navigationNodes.asStateFlow()
    
    init {
        // Setup auth state listener
        auth.addAuthStateListener { firebaseAuth ->
            _currentUser.value = firebaseAuth.currentUser
        }
    }
    
    /**
     * Sign in with email and password
     */
    suspend fun signIn(email: String, password: String): Result<FirebaseUser> = withContext(Dispatchers.IO) {
        try {
            val authResult = auth.signInWithEmailAndPassword(email, password).await()
            authResult.user?.let {
                Result.success(it)
            } ?: Result.failure(Exception("Authentication failed"))
        } catch (e: Exception) {
            Log.e(TAG, "Sign in failed", e)
            Result.failure(e)
        }
    }
    
    /**
     * Sign out
     */
    fun signOut() {
        auth.signOut()
    }
    
    /**
     * Load building data
     */
    suspend fun loadBuilding(buildingId: String): Result<Building> = withContext(Dispatchers.IO) {
        try {
            val document = firestore.collection("buildings")
                .document(buildingId)
                .get()
                .await()
            
            if (document.exists()) {
                val building = document.toObject(Building::class.java)
                
                building?.let {
                    _currentBuilding.value = it
                    
                    // Load POIs and navigation nodes for this building
                    loadPointsOfInterest(buildingId)
                    loadNavigationNodes(buildingId)
                    
                    Result.success(it)
                } ?: Result.failure(Exception("Failed to parse building data"))
            } else {
                Result.failure(Exception("Building not found"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load building", e)
            Result.failure(e)
        }
    }
    
    /**
     * Load points of interest for a building
     */
    private suspend fun loadPointsOfInterest(buildingId: String) = withContext(Dispatchers.IO) {
        try {
            val querySnapshot = firestore.collection("buildings")
                .document(buildingId)
                .collection("pois")
                .get()
                .await()
            
            val pois = querySnapshot.documents.mapNotNull { 
                it.toObject(PointOfInterest::class.java) 
            }
            
            _pointsOfInterest.value = pois
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load POIs", e)
        }
    }
    
    /**
     * Load navigation nodes for a building
     */
    private suspend fun loadNavigationNodes(buildingId: String) = withContext(Dispatchers.IO) {
        try {
            val querySnapshot = firestore.collection("buildings")
                .document(buildingId)
                .collection("navnodes")
                .get()
                .await()
            
            val nodes = querySnapshot.documents.associate { 
                val node = it.toObject(NavNode::class.java)!!
                node.id to node
            }
            
            _navigationNodes.value = nodes
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load navigation nodes", e)
        }
    }
    
    /**
     * Send password reset email
     */
    suspend fun sendPasswordResetEmail(email: String) = withContext(Dispatchers.IO) {
        auth.sendPasswordResetEmail(email).await()
    }
    
    /**
     * Sign up with email and password
     */
    suspend fun signUp(email: String, password: String): Result<FirebaseUser> = withContext(Dispatchers.IO) {
        try {
            val authResult = auth.createUserWithEmailAndPassword(email, password).await()
            authResult.user?.let {
                Result.success(it)
            } ?: Result.failure(Exception("Authentication failed"))
        } catch (e: Exception) {
            Log.e(TAG, "Sign up failed", e)
            Result.failure(e)
        }
    }
    
    /**
     * Save user details to Firestore
     */
    suspend fun saveUserDetails(user: FirebaseUser, userInfo: Map<String, String>) = withContext(Dispatchers.IO) {
        try {
            firestore.collection("users")
                .document(user.uid)
                .set(userInfo)
                .await()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save user details", e)
            throw e
        }
    }
    
    /**
     * Save user position for analytics
     */
    suspend fun saveUserPosition(buildingId: String, position: Position) = withContext(Dispatchers.IO) {
        try {
            val user = auth.currentUser ?: return@withContext
            
            val positionData = hashMapOf(
                "userId" to user.uid,
                "buildingId" to buildingId,
                "x" to position.x,
                "y" to position.y,
                "floor" to position.floor,
                "timestamp" to System.currentTimeMillis()
            )
            
            firestore.collection("analytics")
                .document("positions")
                .collection(user.uid)
                .add(positionData)
                .await()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save user position", e)
        }
    }
}