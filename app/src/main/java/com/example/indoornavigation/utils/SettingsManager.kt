package com.example.indoornavigation.utils

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.google.gson.Gson

/**
 * Manages application settings for the indoor navigation system
 */
class SettingsManager(private val context: Context) {
    
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()
    
    // Settings LiveData to observe changes
    private val _settings = MutableLiveData<AppSettings>()
    val settings: LiveData<AppSettings> = _settings
    
    init {
        // Load settings when the manager is created
        _settings.value = loadSettings()
    }
    
    /**
     * Load settings from shared preferences
     */
    private fun loadSettings(): AppSettings {
        val defaultSettings = AppSettings.defaults
        
        val savedJson = prefs.getString(KEY_SETTINGS, null)
        if (savedJson != null) {
            try {
                val savedSettings = gson.fromJson(savedJson, AppSettings::class.java)
                return mergeWithDefaults(savedSettings, defaultSettings)
            } catch (e: Exception) {
                // If there's an error parsing, return defaults
                return defaultSettings
            }
        }
        return defaultSettings
    }
    
    /**
     * Merge saved settings with defaults to ensure we have all required settings
     */
    private fun mergeWithDefaults(saved: AppSettings, defaults: AppSettings): AppSettings {
        return AppSettings(
            theme = saved.theme ?: defaults.theme,
            mapRotation = saved.mapRotation ?: defaults.mapRotation,
            distanceUnit = saved.distanceUnit ?: defaults.distanceUnit,
            showBreadcrumbs = saved.showBreadcrumbs ?: defaults.showBreadcrumbs,
            breadcrumbDuration = saved.breadcrumbDuration ?: defaults.breadcrumbDuration,
            notificationsEnabled = saved.notificationsEnabled ?: defaults.notificationsEnabled,
            notificationDistance = saved.notificationDistance ?: defaults.notificationDistance,
            accessibility = saved.accessibility?.let { savedAccessibility ->
                AccessibilitySettings(
                    highContrast = savedAccessibility.highContrast ?: defaults.accessibility?.highContrast ?: false,
                    largeText = savedAccessibility.largeText ?: defaults.accessibility?.largeText ?: false,
                    screenReader = savedAccessibility.screenReader ?: defaults.accessibility?.screenReader ?: false,
                    reduceMotion = savedAccessibility.reduceMotion ?: defaults.accessibility?.reduceMotion ?: false
                )
            } ?: defaults.accessibility,
            privacy = saved.privacy?.let { savedPrivacy ->
                PrivacySettings(
                    saveSearchHistory = savedPrivacy.saveSearchHistory ?: defaults.privacy?.saveSearchHistory ?: true,
                    locationTracking = savedPrivacy.locationTracking ?: defaults.privacy?.locationTracking ?: "always",
                    analyticsEnabled = savedPrivacy.analyticsEnabled ?: defaults.privacy?.analyticsEnabled ?: true
                )
            } ?: defaults.privacy,
            development = saved.development?.let { savedDev ->
                DevelopmentSettings(
                    showDebugInfo = savedDev.showDebugInfo ?: defaults.development?.showDebugInfo ?: false,
                    simulatePositionErrors = savedDev.simulatePositionErrors ?: defaults.development?.simulatePositionErrors ?: false,
                    logLevel = savedDev.logLevel ?: defaults.development?.logLevel ?: "error"
                )
            } ?: defaults.development
        )
    }
    
    /**
     * Save settings to shared preferences
     */
    private fun saveSettings() {
        val settingsJson = gson.toJson(_settings.value)
        prefs.edit().putString(KEY_SETTINGS, settingsJson).apply()
    }
    
    /**
     * Update a single setting value
     */
    fun updateSetting(path: String, value: Any) {
        val currentSettings = _settings.value ?: AppSettings.defaults
        val updatedSettings = when {
            path == "theme" -> currentSettings.copy(theme = value as String)
            path == "mapRotation" -> currentSettings.copy(mapRotation = value as String)
            path == "distanceUnit" -> currentSettings.copy(distanceUnit = value as String)
            path == "showBreadcrumbs" -> currentSettings.copy(showBreadcrumbs = value as Boolean)
            path == "breadcrumbDuration" -> currentSettings.copy(breadcrumbDuration = value as Int)
            path == "notificationsEnabled" -> currentSettings.copy(notificationsEnabled = value as Boolean)
            path == "notificationDistance" -> currentSettings.copy(notificationDistance = value as Int)
            
            path.startsWith("accessibility.") -> {
                val property = path.substringAfter("accessibility.")
                val accessibility = currentSettings.accessibility ?: AccessibilitySettings()
                when (property) {
                    "highContrast" -> currentSettings.copy(accessibility = accessibility.copy(highContrast = value as Boolean))
                    "largeText" -> currentSettings.copy(accessibility = accessibility.copy(largeText = value as Boolean))
                    "screenReader" -> currentSettings.copy(accessibility = accessibility.copy(screenReader = value as Boolean))
                    "reduceMotion" -> currentSettings.copy(accessibility = accessibility.copy(reduceMotion = value as Boolean))
                    else -> currentSettings
                }
            }
            
            path.startsWith("privacy.") -> {
                val property = path.substringAfter("privacy.")
                val privacy = currentSettings.privacy ?: PrivacySettings()
                when (property) {
                    "saveSearchHistory" -> currentSettings.copy(privacy = privacy.copy(saveSearchHistory = value as Boolean))
                    "locationTracking" -> currentSettings.copy(privacy = privacy.copy(locationTracking = value as String))
                    "analyticsEnabled" -> currentSettings.copy(privacy = privacy.copy(analyticsEnabled = value as Boolean))
                    else -> currentSettings
                }
            }
            
            path.startsWith("development.") -> {
                val property = path.substringAfter("development.")
                val development = currentSettings.development ?: DevelopmentSettings()
                when (property) {
                    "showDebugInfo" -> currentSettings.copy(development = development.copy(showDebugInfo = value as Boolean))
                    "simulatePositionErrors" -> currentSettings.copy(development = development.copy(simulatePositionErrors = value as Boolean))
                    "logLevel" -> currentSettings.copy(development = development.copy(logLevel = value as String))
                    else -> currentSettings
                }
            }
            
            else -> currentSettings
        }
        
        _settings.value = updatedSettings
        saveSettings()
    }
    
    /**
     * Reset all settings to defaults
     */
    fun resetToDefaults() {
        _settings.value = AppSettings.defaults
        saveSettings()
    }
    
    companion object {
        private const val PREFS_NAME = "indoor_navigation_settings"
        private const val KEY_SETTINGS = "app_settings"
    }
}

/**
 * Data class representing all application settings
 */
data class AppSettings(
    val theme: String? = null,
    val mapRotation: String? = null, // 'fixed' or 'follow-user'
    val distanceUnit: String? = null,
    val showBreadcrumbs: Boolean? = null,
    val breadcrumbDuration: Int? = null, // seconds
    val notificationsEnabled: Boolean? = null,
    val notificationDistance: Int? = null, // meters
    val accessibility: AccessibilitySettings? = null,
    val privacy: PrivacySettings? = null,
    val development: DevelopmentSettings? = null
) {
    companion object {
        val defaults = AppSettings(
            theme = "light",
            mapRotation = "fixed",
            distanceUnit = "meters",
            showBreadcrumbs = true,
            breadcrumbDuration = 300,
            notificationsEnabled = true,
            notificationDistance = 10,
            accessibility = AccessibilitySettings(
                highContrast = false,
                largeText = false,
                screenReader = false,
                reduceMotion = false
            ),
            privacy = PrivacySettings(
                saveSearchHistory = true,
                locationTracking = "always", // 'always', 'while-using', 'never'
                analyticsEnabled = true
            ),
            development = DevelopmentSettings(
                showDebugInfo = false,
                simulatePositionErrors = false,
                logLevel = "error" // 'debug', 'info', 'warn', 'error'
            )
        )
    }
}

/**
 * Settings related to accessibility features
 */
data class AccessibilitySettings(
    val highContrast: Boolean? = null,
    val largeText: Boolean? = null,
    val screenReader: Boolean? = null,
    val reduceMotion: Boolean? = null
)

/**
 * Settings related to privacy
 */
data class PrivacySettings(
    val saveSearchHistory: Boolean? = null,
    val locationTracking: String? = null, // 'always', 'while-using', 'never'
    val analyticsEnabled: Boolean? = null
)

/**
 * Settings for development and debugging
 */
data class DevelopmentSettings(
    val showDebugInfo: Boolean? = null,
    val simulatePositionErrors: Boolean? = null,
    val logLevel: String? = null // 'debug', 'info', 'warn', 'error'
)