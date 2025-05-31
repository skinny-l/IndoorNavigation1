package com.example.indoornavigation.analytics

import android.content.Context
import android.os.Build
import android.util.Log
import com.example.indoornavigation.data.database.AppDatabase
import com.example.indoornavigation.data.database.entities.AnalyticsEventEntity
import com.example.indoornavigation.data.database.entities.CrashReportEntity
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.perf.FirebasePerformance
import com.google.firebase.perf.metrics.Trace
import kotlinx.coroutines.*
import java.util.*
import kotlin.coroutines.CoroutineContext

/**
 * Comprehensive analytics manager for tracking user behavior, performance, and crashes
 */
class AnalyticsManager private constructor(
    private val context: Context
) : CoroutineScope {

    private val job = SupervisorJob()
    override val coroutineContext: CoroutineContext = Dispatchers.IO + job

    private val firebaseAnalytics = FirebaseAnalytics.getInstance(context)
    private val crashlytics = FirebaseCrashlytics.getInstance()
    private val performance = FirebasePerformance.getInstance()
    private val database = AppDatabase.getDatabase(context)
    
    private val sessionId = UUID.randomUUID().toString()
    private val sessionStartTime = System.currentTimeMillis()
    
    private var userId: String? = null
    private var isAnalyticsEnabled = true
    
    // Active performance traces
    private val activeTraces = mutableMapOf<String, Trace>()

    init {
        setupCrashReporting()
        setupPerformanceMonitoring()
    }

    companion object {
        @Volatile
        private var INSTANCE: AnalyticsManager? = null
        
        fun getInstance(context: Context): AnalyticsManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AnalyticsManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    /**
     * Set the current user ID for analytics
     */
    fun setUserId(userId: String?) {
        this.userId = userId
        firebaseAnalytics.setUserId(userId)
        crashlytics.setUserId(userId ?: "anonymous")
    }

    /**
     * Enable or disable analytics collection
     */
    fun setAnalyticsEnabled(enabled: Boolean) {
        isAnalyticsEnabled = enabled
        firebaseAnalytics.setAnalyticsCollectionEnabled(enabled)
    }

    /**
     * Track navigation events
     */
    fun trackNavigation(startPOI: String, endPOI: String, method: String, duration: Long, successful: Boolean) {
        if (!isAnalyticsEnabled) return
        
        val params = mapOf(
            "start_poi" to startPOI,
            "end_poi" to endPOI,
            "navigation_method" to method,
            "duration_ms" to duration,
            "successful" to successful
        )
        
        trackEvent("navigation_completed", params)
    }

    /**
     * Track positioning events
     */
    fun trackPositioning(method: String, accuracy: Float, responseTime: Long, beaconCount: Int = 0) {
        if (!isAnalyticsEnabled) return
        
        val params = mapOf(
            "positioning_method" to method,
            "accuracy_meters" to accuracy,
            "response_time_ms" to responseTime,
            "beacon_count" to beaconCount
        )
        
        trackEvent("positioning_update", params)
    }

    /**
     * Track search events
     */
    fun trackSearch(query: String, resultCount: Int, selectedResultIndex: Int = -1) {
        if (!isAnalyticsEnabled) return
        
        val params = mapOf(
            "search_query" to query,
            "result_count" to resultCount,
            "selected_index" to selectedResultIndex
        )
        
        trackEvent("poi_search", params)
    }

    /**
     * Track user interface interactions
     */
    fun trackUIInteraction(screen: String, action: String, element: String? = null) {
        if (!isAnalyticsEnabled) return
        
        val params = mutableMapOf(
            "screen_name" to screen,
            "action" to action
        )
        
        element?.let { params["element"] = it }
        
        trackEvent("ui_interaction", params)
    }

    /**
     * Track app performance metrics
     */
    fun trackPerformance(metric: String, value: Double, unit: String = "ms") {
        if (!isAnalyticsEnabled) return
        
        val params = mapOf(
            "metric_name" to metric,
            "value" to value,
            "unit" to unit
        )
        
        trackEvent("performance_metric", params)
    }

    /**
     * Track errors and exceptions
     */
    fun trackError(error: Throwable, context: String = "unknown", fatal: Boolean = false) {
        val params = mapOf(
            "error_message" to (error.message ?: "unknown"),
            "error_type" to error.javaClass.simpleName,
            "context" to context,
            "fatal" to fatal
        )
        
        trackEvent("app_error", params)
        
        // Also report to Crashlytics
        crashlytics.recordException(error)
        
        // Store crash report locally
        if (fatal) {
            storeCrashReport(error, context)
        }
    }

    /**
     * Track accessibility feature usage
     */
    fun trackAccessibility(feature: String, enabled: Boolean) {
        if (!isAnalyticsEnabled) return
        
        val params = mapOf(
            "accessibility_feature" to feature,
            "enabled" to enabled
        )
        
        trackEvent("accessibility_usage", params)
    }

    /**
     * Track offline mode usage
     */
    fun trackOfflineMode(action: String, success: Boolean, dataSize: Long = 0) {
        if (!isAnalyticsEnabled) return
        
        val params = mapOf(
            "offline_action" to action,
            "success" to success,
            "data_size_bytes" to dataSize
        )
        
        trackEvent("offline_usage", params)
    }

    /**
     * Start a performance trace
     */
    fun startTrace(traceName: String): String {
        val trace = performance.newTrace(traceName)
        trace.start()
        
        val traceId = "${traceName}_${System.currentTimeMillis()}"
        activeTraces[traceId] = trace
        
        return traceId
    }

    /**
     * Stop a performance trace
     */
    fun stopTrace(traceId: String, attributes: Map<String, String> = emptyMap()) {
        activeTraces[traceId]?.let { trace ->
            attributes.forEach { (key, value) ->
                trace.putAttribute(key, value)
            }
            trace.stop()
            activeTraces.remove(traceId)
        }
    }

    /**
     * Add custom metric to active trace
     */
    fun addMetricToTrace(traceId: String, metricName: String, value: Long) {
        activeTraces[traceId]?.putMetric(metricName, value)
    }

    /**
     * Track user session metrics
     */
    fun trackSessionMetrics() {
        if (!isAnalyticsEnabled) return
        
        val sessionDuration = System.currentTimeMillis() - sessionStartTime
        
        val params = mapOf(
            "session_duration_ms" to sessionDuration,
            "session_id" to sessionId
        )
        
        trackEvent("session_metrics", params)
    }

    /**
     * Upload pending analytics events
     */
    fun uploadPendingEvents() {
        launch {
            try {
                val pendingEvents = database.analyticsDao().getUnuploadedEvents()
                
                if (pendingEvents.isNotEmpty()) {
                    // In a real implementation, this would upload to your analytics service
                    Log.d("Analytics", "Uploading ${pendingEvents.size} pending events")
                    
                    // Mark events as uploaded
                    val eventIds = pendingEvents.map { it.id }
                    database.analyticsDao().markEventsUploaded(eventIds)
                }
                
                // Clean up old events
                val cutoffTime = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000) // 7 days
                database.analyticsDao().deleteOldEvents(cutoffTime)
                
            } catch (e: Exception) {
                Log.e("Analytics", "Failed to upload events", e)
            }
        }
    }

    /**
     * Upload pending crash reports
     */
    fun uploadPendingCrashReports() {
        launch {
            try {
                val pendingReports = database.crashReportDao().getUnuploadedCrashReports()
                
                if (pendingReports.isNotEmpty()) {
                    Log.d("Analytics", "Uploading ${pendingReports.size} crash reports")
                    
                    // Mark reports as uploaded
                    val reportIds = pendingReports.map { it.id }
                    database.crashReportDao().markReportsUploaded(reportIds)
                }
                
                // Clean up old reports
                val cutoffTime = System.currentTimeMillis() - (30 * 24 * 60 * 60 * 1000) // 30 days
                database.crashReportDao().deleteOldReports(cutoffTime)
                
            } catch (e: Exception) {
                Log.e("Analytics", "Failed to upload crash reports", e)
            }
        }
    }

    /**
     * Generic event tracking method
     */
    private fun trackEvent(eventName: String, parameters: Map<String, Any>) {
        if (!isAnalyticsEnabled) return
        
        // Track with Firebase Analytics
        val bundle = android.os.Bundle()
        parameters.forEach { (key, value) ->
            when (value) {
                is String -> bundle.putString(key, value)
                is Int -> bundle.putInt(key, value)
                is Long -> bundle.putLong(key, value)
                is Double -> bundle.putDouble(key, value)
                is Boolean -> bundle.putBoolean(key, value)
                is Float -> bundle.putFloat(key, value)
                else -> bundle.putString(key, value.toString())
            }
        }
        
        firebaseAnalytics.logEvent(eventName, bundle)
        
        // Store locally for offline support
        storeEventLocally(eventName, parameters)
    }

    /**
     * Store analytics event locally
     */
    private fun storeEventLocally(eventName: String, parameters: Map<String, Any>) {
        launch {
            try {
                val event = AnalyticsEventEntity(
                    id = UUID.randomUUID().toString(),
                    eventType = eventName,
                    eventData = com.google.gson.Gson().toJson(parameters),
                    timestamp = System.currentTimeMillis(),
                    userId = userId,
                    sessionId = sessionId,
                    uploaded = false
                )
                
                database.analyticsDao().insertEvent(event)
            } catch (e: Exception) {
                Log.e("Analytics", "Failed to store event locally", e)
            }
        }
    }

    /**
     * Store crash report locally
     */
    private fun storeCrashReport(error: Throwable, context: String) {
        launch {
            try {
                val deviceInfo = mapOf(
                    "manufacturer" to Build.MANUFACTURER,
                    "model" to Build.MODEL,
                    "android_version" to Build.VERSION.RELEASE,
                    "sdk_int" to Build.VERSION.SDK_INT,
                    "app_version" to getAppVersion()
                )
                
                val crashReport = CrashReportEntity(
                    id = UUID.randomUUID().toString(),
                    crashMessage = error.message ?: "Unknown error",
                    stackTrace = error.stackTraceToString(),
                    deviceInfo = com.google.gson.Gson().toJson(deviceInfo),
                    appVersion = getAppVersion(),
                    timestamp = System.currentTimeMillis(),
                    uploaded = false
                )
                
                database.crashReportDao().insertCrashReport(crashReport)
            } catch (e: Exception) {
                Log.e("Analytics", "Failed to store crash report", e)
            }
        }
    }

    /**
     * Setup crash reporting
     */
    private fun setupCrashReporting() {
        // Set up uncaught exception handler
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        
        Thread.setDefaultUncaughtExceptionHandler { thread, exception ->
            trackError(exception, "uncaught_exception", fatal = true)
            
            // Call the default handler
            defaultHandler?.uncaughtException(thread, exception)
        }
        
        // Set custom keys for crashlytics
        crashlytics.setCustomKey("session_id", sessionId)
        crashlytics.setCustomKey("user_id", userId ?: "anonymous")
    }

    /**
     * Setup performance monitoring
     */
    private fun setupPerformanceMonitoring() {
        // Enable automatic performance monitoring
        performance.isPerformanceCollectionEnabled = isAnalyticsEnabled
        
        // Start app startup trace
        val startupTrace = performance.newTrace("app_startup")
        startupTrace.start()
        
        // Stop the trace after a delay (in a real app, this would be when the app is fully loaded)
        launch {
            delay(3000) // 3 seconds
            startupTrace.stop()
        }
    }

    /**
     * Get app version
     */
    private fun getAppVersion(): String {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            packageInfo.versionName ?: "unknown"
        } catch (e: Exception) {
            "unknown"
        }
    }

    /**
     * Clean up resources
     */
    fun cleanup() {
        trackSessionMetrics()
        uploadPendingEvents()
        uploadPendingCrashReports()
        
        // Stop all active traces
        activeTraces.values.forEach { it.stop() }
        activeTraces.clear()
        
        job.cancel()
    }
}

/**
 * Analytics event types
 */
object AnalyticsEvents {
    const val NAVIGATION_STARTED = "navigation_started"
    const val NAVIGATION_COMPLETED = "navigation_completed"
    const val NAVIGATION_CANCELLED = "navigation_cancelled"
    const val POI_SEARCHED = "poi_searched"
    const val POI_SELECTED = "poi_selected"
    const val FLOOR_CHANGED = "floor_changed"
    const val POSITIONING_METHOD_CHANGED = "positioning_method_changed"
    const val BEACON_DETECTED = "beacon_detected"
    const val WIFI_SCAN_COMPLETED = "wifi_scan_completed"
    const val OFFLINE_MODE_ENABLED = "offline_mode_enabled"
    const val ACCESSIBILITY_FEATURE_USED = "accessibility_feature_used"
    const val SETTINGS_CHANGED = "settings_changed"
    const val ERROR_OCCURRED = "error_occurred"
}