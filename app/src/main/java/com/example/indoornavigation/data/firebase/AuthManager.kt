package com.example.indoornavigation.data.firebase

import android.content.Context
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.example.indoornavigation.data.models.UserDetails
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.UserProfileChangeRequest
import kotlinx.coroutines.tasks.await

/**
 * Manages authentication operations using Firebase Auth
 */
class AuthManager(private val context: Context) {
    
    private val auth = FirebaseAuth.getInstance()
    
    // Authentication state for the app to observe
    private val _authState = MutableLiveData<AuthState>()
    val authState: LiveData<AuthState> = _authState
    
    // User profile data
    private val _userDetails = MutableLiveData<UserDetails?>()
    val userDetails: LiveData<UserDetails?> = _userDetails
    
    init {
        // Initialize auth state based on current Firebase auth state
        updateAuthState(auth.currentUser)
        
        // Listen for auth state changes
        auth.addAuthStateListener { firebaseAuth ->
            updateAuthState(firebaseAuth.currentUser)
        }
    }
    
    /**
     * Update authentication state when Firebase auth changes
     */
    private fun updateAuthState(user: FirebaseUser?) {
        if (user != null) {
            _authState.value = AuthState(
                isAuthenticated = true,
                userId = user.uid,
                email = user.email
            )
            
            // Load user details
            loadUserDetails(user)
        } else {
            _authState.value = AuthState(
                isAuthenticated = false,
                userId = null,
                email = null
            )
            _userDetails.value = null
        }
    }
    
    /**
     * Load user details from Firestore or other backend service
     */
    private fun loadUserDetails(user: FirebaseUser) {
        // In a real implementation, this would query Firestore or another backend
        // For now, create a basic UserDetails from Firebase user
        _userDetails.value = UserDetails(
            username = user.displayName ?: "",
            email = user.email ?: "",
            phone = user.phoneNumber ?: ""
        )
    }
    
    /**
     * Sign in with email and password
     */
    suspend fun signIn(email: String, password: String): AuthResult {
        return try {
            val authResult = auth.signInWithEmailAndPassword(email, password).await()
            AuthResult(success = true, user = authResult.user)
        } catch (e: Exception) {
            Log.e(TAG, "Sign in failed", e)
            AuthResult(success = false, errorMessage = e.localizedMessage ?: "Authentication failed")
        }
    }
    
    /**
     * Create a new user with email and password
     */
    suspend fun createAccount(email: String, password: String, displayName: String): AuthResult {
        return try {
            val authResult = auth.createUserWithEmailAndPassword(email, password).await()
            
            // Set display name
            val profileUpdates = UserProfileChangeRequest.Builder()
                .setDisplayName(displayName)
                .build()
            
            authResult.user?.updateProfile(profileUpdates)?.await()
            
            AuthResult(success = true, user = authResult.user)
        } catch (e: Exception) {
            Log.e(TAG, "Create account failed", e)
            AuthResult(success = false, errorMessage = e.localizedMessage ?: "Account creation failed")
        }
    }
    
    /**
     * Sign out the current user
     */
    fun signOut() {
        clearLocalData()
        auth.signOut()
    }
    
    /**
     * Send password reset email
     */
    suspend fun sendPasswordResetEmail(email: String): Boolean {
        return try {
            auth.sendPasswordResetEmail(email).await()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Password reset failed", e)
            false
        }
    }
    
    /**
     * Delete the current user account
     */
    suspend fun deleteAccount(): Boolean {
        val user = auth.currentUser ?: return false
        
        return try {
            user.delete().await()
            clearLocalData()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Delete account failed", e)
            false
        }
    }
    
    /**
     * Clear all local user data
     */
    private fun clearLocalData() {
        // Clear SharedPreferences
        val prefs = context.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
        
        // Clear other local storage as needed
    }
    
    companion object {
        private const val TAG = "AuthManager"
    }
    
    /**
     * Represents the current authentication state
     */
    data class AuthState(
        val isAuthenticated: Boolean = false,
        val userId: String? = null,
        val email: String? = null
    )
    
    /**
     * Result of authentication operations
     */
    data class AuthResult(
        val success: Boolean,
        val user: FirebaseUser? = null,
        val errorMessage: String? = null
    )
}