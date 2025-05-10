package com.example.indoornavigation

import android.app.Application
import android.util.Log
import androidx.preference.PreferenceManager
import com.google.firebase.FirebaseApp
import org.osmdroid.config.Configuration

class IndoorNavigationApp : Application() {
    
    private val TAG = "IndoorNavigationApp"
    
    override fun onCreate() {
        super.onCreate()
        
        // Initialize Firebase
        try {
            FirebaseApp.initializeApp(this)
            Log.d(TAG, "Firebase initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Firebase", e)
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
    }
}