package com.example.indoornavigation.data.wifi

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.util.Log
import androidx.core.app.ActivityCompat
import android.Manifest

/**
 * BroadcastReceiver for handling Wi-Fi scan results
 */
class WifiScanReceiver(private val onScanResultsReceived: (List<ScanResult>) -> Unit) : BroadcastReceiver() {
    
    private val TAG = "WifiScanReceiver"
    
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == WifiManager.SCAN_RESULTS_AVAILABLE_ACTION) {
            val success = intent.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false)
            
            if (success) {
                // Check for permissions
                if (ActivityCompat.checkSelfPermission(
                        context,
                        Manifest.permission.ACCESS_WIFI_STATE
                    ) != PackageManager.PERMISSION_GRANTED || 
                    ActivityCompat.checkSelfPermission(
                        context,
                        Manifest.permission.ACCESS_FINE_LOCATION
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    Log.e(TAG, "Permission denied: Cannot access Wi-Fi scan results")
                    return
                }
                
                // Get scan results
                val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as WifiManager
                val results = wifiManager.scanResults
                
                Log.d(TAG, "Wi-Fi scan results received: ${results.size} access points")
                
                // Pass results to callback
                onScanResultsReceived(results)
            } else {
                Log.d(TAG, "Wi-Fi scan failed")
            }
        }
    }
}