package com.example.indoornavigation.utils

import android.content.Context
import android.util.Log
import com.example.indoornavigation.data.models.Position
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Configuration manager for building data
 * Creates editable JSON files for POIs and beacons
 */
class BuildingConfigManager(private val context: Context) {
    
    private val configDir = File(context.filesDir, "building_config")
    private val poisFile = File(configDir, "pois.json")
    private val beaconsFile = File(configDir, "beacons.json")
    
    init {
        // Create config directory if it doesn't exist
        if (!configDir.exists()) {
            configDir.mkdirs()
        }
    }
    
    /**
     * Save POIs to JSON file
     */
    fun savePOIs(pois: List<POIConfig>) {
        try {
            val jsonArray = JSONArray()
            
            pois.forEach { poi ->
                val jsonObject = JSONObject().apply {
                    put("name", poi.name)
                    put("type", poi.type)
                    put("description", poi.description)
                    put("x", poi.position.x)
                    put("y", poi.position.y)
                    put("floor", poi.position.floor)
                    put("enabled", poi.enabled)
                    put("searchable", poi.searchable)
                    put("category", poi.category)
                }
                jsonArray.put(jsonObject)
            }
            
            poisFile.writeText(jsonArray.toString(2)) // Pretty print with indent
            Log.d("ConfigManager", "Saved ${pois.size} POIs to ${poisFile.absolutePath}")
            
        } catch (e: Exception) {
            Log.e("ConfigManager", "Error saving POIs: ${e.message}")
            e.printStackTrace()
        }
    }
    
    /**
     * Load POIs from JSON file
     */
    fun loadPOIs(): List<POIConfig> {
        val pois = mutableListOf<POIConfig>()
        
        try {
            if (!poisFile.exists()) {
                createSamplePOIFile()
            }
            
            val jsonString = poisFile.readText()
            val jsonArray = JSONArray(jsonString)
            
            for (i in 0 until jsonArray.length()) {
                val jsonObject = jsonArray.getJSONObject(i)
                
                val poi = POIConfig(
                    name = jsonObject.getString("name"),
                    type = jsonObject.getString("type"),
                    description = jsonObject.optString("description", ""),
                    position = Position(
                        jsonObject.getDouble("x"),
                        jsonObject.getDouble("y"),
                        jsonObject.getInt("floor")
                    ),
                    enabled = jsonObject.optBoolean("enabled", true),
                    searchable = jsonObject.optBoolean("searchable", true),
                    category = jsonObject.optString("category", "general")
                )
                pois.add(poi)
            }
            
            Log.d("ConfigManager", "Loaded ${pois.size} POIs from ${poisFile.absolutePath}")
            
        } catch (e: Exception) {
            Log.e("ConfigManager", "Error loading POIs: ${e.message}")
            e.printStackTrace()
        }
        
        return pois
    }
    
    /**
     * Save beacons to JSON file
     */
    fun saveBeacons(beacons: List<BeaconConfig>) {
        try {
            val jsonArray = JSONArray()
            
            beacons.forEach { beacon ->
                val jsonObject = JSONObject().apply {
                    put("name", beacon.name)
                    put("macAddress", beacon.macAddress)
                    put("uuid", beacon.uuid)
                    put("type", beacon.type)
                    put("x", beacon.position.x)
                    put("y", beacon.position.y)
                    put("floor", beacon.position.floor)
                    put("txPower", beacon.txPower)
                    put("enabled", beacon.enabled)
                    put("isPublic", beacon.isPublic)
                    put("ssid", beacon.ssid) // For WiFi APs
                }
                jsonArray.put(jsonObject)
            }
            
            beaconsFile.writeText(jsonArray.toString(2))
            Log.d("ConfigManager", "Saved ${beacons.size} beacons to ${beaconsFile.absolutePath}")
            
        } catch (e: Exception) {
            Log.e("ConfigManager", "Error saving beacons: ${e.message}")
            e.printStackTrace()
        }
    }
    
    /**
     * Load beacons from JSON file
     */
    fun loadBeacons(): List<BeaconConfig> {
        val beacons = mutableListOf<BeaconConfig>()
        
        try {
            if (!beaconsFile.exists()) {
                createSampleBeaconFile()
            }
            
            val jsonString = beaconsFile.readText()
            val jsonArray = JSONArray(jsonString)
            
            for (i in 0 until jsonArray.length()) {
                val jsonObject = jsonArray.getJSONObject(i)
                
                val beacon = BeaconConfig(
                    name = jsonObject.getString("name"),
                    macAddress = jsonObject.optString("macAddress", ""),
                    uuid = jsonObject.optString("uuid", ""),
                    type = jsonObject.getString("type"),
                    position = Position(
                        jsonObject.getDouble("x"),
                        jsonObject.getDouble("y"),
                        jsonObject.getInt("floor")
                    ),
                    txPower = jsonObject.optInt("txPower", -59),
                    enabled = jsonObject.optBoolean("enabled", true),
                    isPublic = jsonObject.optBoolean("isPublic", false),
                    ssid = jsonObject.optString("ssid", "")
                )
                beacons.add(beacon)
            }
            
            Log.d("ConfigManager", "Loaded ${beacons.size} beacons from ${beaconsFile.absolutePath}")
            
        } catch (e: Exception) {
            Log.e("ConfigManager", "Error loading beacons: ${e.message}")
            e.printStackTrace()
        }
        
        return beacons
    }
    
    /**
     * Create sample POI configuration file
     */
    private fun createSamplePOIFile() {
        val samplePOIs = """
[
  {
    "name": "Main Entrance",
    "type": "entrance",
    "description": "Primary building entrance",
    "x": 35.0,
    "y": 55.0,
    "floor": 0,
    "enabled": true,
    "searchable": true,
    "category": "entrance"
  },
  {
    "name": "Restroom A",
    "type": "restroom",
    "description": "Men's restroom near entrance",
    "x": 25.0,
    "y": 50.0,
    "floor": 0,
    "enabled": true,
    "searchable": true,
    "category": "facilities"
  },
  {
    "name": "Lab 101",
    "type": "lab",
    "description": "Computer Science Lab",
    "x": 55.0,
    "y": 15.0,
    "floor": 0,
    "enabled": true,
    "searchable": true,
    "category": "academic"
  }
]
        """.trimIndent()
        
        poisFile.writeText(samplePOIs)
        Log.d("ConfigManager", "Created sample POI file at ${poisFile.absolutePath}")
    }
    
    /**
     * Create sample beacon configuration file
     */
    private fun createSampleBeaconFile() {
        val sampleBeacons = """
[
  {
    "name": "Entrance Beacon",
    "macAddress": "AA:BB:CC:DD:EE:FF",
    "uuid": "beacon-entrance-001",
    "type": "BLE",
    "x": 35.0,
    "y": 55.0,
    "floor": 0,
    "txPower": -59,
    "enabled": true,
    "isPublic": false
  },
  {
    "name": "UM_Student WiFi",
    "macAddress": "11:22:33:44:55:66",
    "uuid": "wifi-um-student",
    "type": "WiFi",
    "x": 15.0,
    "y": 45.0,
    "floor": 0,
    "txPower": -20,
    "enabled": true,
    "isPublic": true,
    "ssid": "UM_Student"
  },
  {
    "name": "Public Bluetooth Device",
    "macAddress": "",
    "uuid": "",
    "type": "BLE_PUBLIC",
    "x": 0.0,
    "y": 0.0,
    "floor": 0,
    "txPower": -59,
    "enabled": true,
    "isPublic": true
  }
]
        """.trimIndent()
        
        beaconsFile.writeText(sampleBeacons)
        Log.d("ConfigManager", "Created sample beacon file at ${beaconsFile.absolutePath}")
    }
    
    /**
     * Get file paths for manual editing
     */
    fun getConfigFilePaths(): ConfigFilePaths {
        return ConfigFilePaths(
            poisFile = poisFile.absolutePath,
            beaconsFile = beaconsFile.absolutePath,
            configDir = configDir.absolutePath
        )
    }
    
    /**
     * Export configuration to external storage for easy editing
     */
    fun exportToExternalStorage(): String? {
        try {
            val externalDir = File(context.getExternalFilesDir(null), "IndoorNavigation")
            if (!externalDir.exists()) {
                externalDir.mkdirs()
            }
            
            val externalPOIs = File(externalDir, "pois.json")
            val externalBeacons = File(externalDir, "beacons.json")
            
            if (poisFile.exists()) {
                poisFile.copyTo(externalPOIs, overwrite = true)
            }
            if (beaconsFile.exists()) {
                beaconsFile.copyTo(externalBeacons, overwrite = true)
            }
            
            return externalDir.absolutePath
        } catch (e: Exception) {
            Log.e("ConfigManager", "Error exporting config: ${e.message}")
            return null
        }
    }
    
    data class POIConfig(
        val name: String,
        val type: String,
        val description: String,
        val position: Position,
        val enabled: Boolean = true,
        val searchable: Boolean = true,
        val category: String = "general"
    )
    
    data class BeaconConfig(
        val name: String,
        val macAddress: String,
        val uuid: String,
        val type: String, // BLE, WiFi, BLE_PUBLIC, WiFi_PUBLIC
        val position: Position,
        val txPower: Int = -59,
        val enabled: Boolean = true,
        val isPublic: Boolean = false, // True for any detected beacon/AP
        val ssid: String = "" // For WiFi access points
    )
    
    data class ConfigFilePaths(
        val poisFile: String,
        val beaconsFile: String,
        val configDir: String
    )
}