package com.example.indoornavigation.data.wifi

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.util.Log
import androidx.core.app.ActivityCompat
import com.example.indoornavigation.data.models.WiFiFingerprint

/**
 * Collects WiFi fingerprints at specified locations
 */
class WiFiFingerprintCollector(private val context: Context) {
    private val TAG = "WiFiFingerprintCollector"
    private val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as WifiManager
    
    /**
     * Collect a WiFi fingerprint at the current location
     * @param locationId An identifier for the location
     * @return A WiFiFingerprint object with the scan results or null if permissions are missing
     */
    fun collectFingerprint(locationId: String): WiFiFingerprint? {
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e(TAG, "Location permission not granted")
            return null
        }
        
        val scanResults = wifiManager.scanResults
        val signalMap = scanResults.associate { it.BSSID to it.level }
        
        return WiFiFingerprint(
            locationId = locationId,
            signalMap = signalMap,
            timestamp = System.currentTimeMillis()
        )
    }
    
    /**
     * Collect multiple fingerprints at the same location for better accuracy
     * @param locationId An identifier for the location
     * @param count Number of fingerprints to collect
     * @param delayMillis Delay between collections in milliseconds
     * @return A single merged fingerprint with averaged signal strengths
     */
    suspend fun collectAveragedFingerprint(
        locationId: String, 
        count: Int = 5,
        delayMillis: Long = 500
    ): WiFiFingerprint? {
        val fingerprints = mutableListOf<WiFiFingerprint>()
        
        // Collect multiple fingerprints
        for (i in 0 until count) {
            collectFingerprint(locationId)?.let { fingerprints.add(it) }
            kotlinx.coroutines.delay(delayMillis)
        }
        
        if (fingerprints.isEmpty()) {
            return null
        }
        
        // Merge the fingerprints by averaging the signal strengths
        val aggregatedSignals = mutableMapOf<String, MutableList<Int>>()
        
        for (fingerprint in fingerprints) {
            for ((bssid, rssi) in fingerprint.signalMap) {
                if (bssid !in aggregatedSignals) {
                    aggregatedSignals[bssid] = mutableListOf()
                }
                aggregatedSignals[bssid]?.add(rssi)
            }
        }
        
        val averagedSignalMap = aggregatedSignals.mapValues { (_, rssiList) ->
            rssiList.sum() / rssiList.size
        }
        
        return WiFiFingerprint(
            locationId = locationId,
            signalMap = averagedSignalMap,
            timestamp = System.currentTimeMillis()
        )
    }
}