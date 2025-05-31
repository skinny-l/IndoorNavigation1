package com.example.indoornavigation

import android.app.Application
import android.util.Log
import androidx.preference.PreferenceManager
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import org.osmdroid.config.Configuration
import com.example.indoornavigation.positioning.PositioningEngine

class IndoorNavigationApp : Application() {
    
    private val TAG = "IndoorNavigationApp"
    
    override fun onCreate() {
        super.onCreate()
        
        // Initialize Firebase
        try {
            FirebaseApp.initializeApp(this)
            Log.d(TAG, "Firebase initialized successfully using google-services.json")
        } catch (e: Exception) {
            when {
                e.message?.contains("Default FirebaseApp is not initialized") == true -> {
                    Log.e(TAG, "Firebase not initialized - check google-services.json", e)
                }
                FirebaseApp.getApps(this).isNotEmpty() -> {
                    Log.w(TAG, "Firebase already initialized", e)
                }
                else -> {
                    Log.e(TAG, "Unknown Firebase initialization error", e)
                }
            }
        }
        
        // Initialize OSMdroid
        try {
            // Load default OSMdroid configuration
            Configuration.getInstance().load(
                applicationContext,
                PreferenceManager.getDefaultSharedPreferences(applicationContext)
            )
            
            // Set User-Agent to avoid getting banned by OSM servers
            Configuration.getInstance().userAgentValue = packageName
            
            // Removed the cache size setting as it's not critical
            // Configuration.getInstance().setCacheSizeBytes(250L * 1024 * 1024)
            
            Log.d(TAG, "OSMdroid initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize OSMdroid", e)
        }
        
        // Initialize PositioningEngine
        try {
            PositioningEngine.initialize(emptyList(), applicationContext)
            Log.d(TAG, "PositioningEngine initialized")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize PositioningEngine", e)
        }
    }
}
