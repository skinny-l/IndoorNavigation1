package com.example.indoornavigation.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.*
import com.example.indoornavigation.MainActivity
import com.example.indoornavigation.R
import com.example.indoornavigation.analytics.AnalyticsManager
import com.example.indoornavigation.data.database.AppDatabase
import com.example.indoornavigation.data.database.entities.NotificationEntity
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.*
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.flow.first

/**
 * Comprehensive notification manager for handling all types of notifications
 */
class IndoorNotificationManager(
    private val context: Context
) : CoroutineScope {

    private val job = SupervisorJob()
    override val coroutineContext: CoroutineContext = Dispatchers.IO + job

    private val notificationManager = NotificationManagerCompat.from(context)
    private val database = AppDatabase.getDatabase(context)
    private val analytics = AnalyticsManager.getInstance(context)
    private val firebaseMessaging = FirebaseMessaging.getInstance()

    // Notification channels
    private val NAVIGATION_CHANNEL_ID = "navigation_channel"
    private val EMERGENCY_CHANNEL_ID = "emergency_channel"
    private val UPDATES_CHANNEL_ID = "updates_channel"
    private val SYSTEM_CHANNEL_ID = "system_channel"

    init {
        createNotificationChannels()
        subscribeToTopics()
    }

    companion object {
        @Volatile
        private var INSTANCE: IndoorNotificationManager? = null

        fun getInstance(context: Context): IndoorNotificationManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: IndoorNotificationManager(context.applicationContext).also { INSTANCE = it }
            }
        }

        // Notification IDs
        const val NAVIGATION_NOTIFICATION_ID = 1001
        const val EMERGENCY_NOTIFICATION_ID = 1002
        const val UPDATE_NOTIFICATION_ID = 1003
        const val SYSTEM_NOTIFICATION_ID = 1004
    }

    /**
     * Create notification channels for different types of notifications
     */
    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channels = listOf(
                NotificationChannel(
                    NAVIGATION_CHANNEL_ID,
                    "Navigation",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Navigation guidance and route updates"
                    enableVibration(true)
                    setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION), null)
                },
                
                NotificationChannel(
                    EMERGENCY_CHANNEL_ID,
                    "Emergency",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Emergency alerts and critical notifications"
                    enableVibration(true)
                    enableLights(true)
                    setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM), null)
                },
                
                NotificationChannel(
                    UPDATES_CHANNEL_ID,
                    "Updates",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "App updates and feature announcements"
                    enableVibration(false)
                },
                
                NotificationChannel(
                    SYSTEM_CHANNEL_ID,
                    "System",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "System notifications and status updates"
                    enableVibration(false)
                }
            )

            val systemNotificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            channels.forEach { channel ->
                systemNotificationManager.createNotificationChannel(channel)
            }
        }
    }

    /**
     * Subscribe to Firebase messaging topics
     */
    private fun subscribeToTopics() {
        firebaseMessaging.subscribeToTopic("general_updates")
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    Log.d("Notifications", "Subscribed to general updates")
                } else {
                    Log.e("Notifications", "Failed to subscribe to topics", task.exception)
                }
            }

        firebaseMessaging.subscribeToTopic("emergency_alerts")
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    Log.d("Notifications", "Subscribed to emergency alerts")
                } else {
                    Log.e("Notifications", "Failed to subscribe to emergency alerts", task.exception)
                }
            }
    }

    /**
     * Show navigation notification
     */
    fun showNavigationNotification(
        title: String,
        message: String,
        ongoing: Boolean = false,
        actionText: String? = null,
        actionIntent: PendingIntent? = null
    ) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notificationBuilder = NotificationCompat.Builder(context, NAVIGATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_navigation)
            .setContentTitle(title)
            .setContentText(message)
            .setContentIntent(pendingIntent)
            .setAutoCancel(!ongoing)
            .setOngoing(ongoing)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_NAVIGATION)

        // Add action if provided
        if (actionText != null && actionIntent != null) {
            notificationBuilder.addAction(
                R.drawable.ic_navigation,
                actionText,
                actionIntent
            )
        }

        notificationManager.notify(NAVIGATION_NOTIFICATION_ID, notificationBuilder.build())

        // Store notification in database
        storeNotification(title, message, "NAVIGATION", NotificationCompat.PRIORITY_HIGH)
        
        analytics.trackUIInteraction("notification", "navigation_shown", title)
    }

    /**
     * Show emergency notification
     */
    fun showEmergencyNotification(
        title: String,
        message: String,
        actionText: String = "View Details",
        urgent: Boolean = true
    ) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("emergency_notification", true)
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notificationBuilder = NotificationCompat.Builder(context, EMERGENCY_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_emergency)
            .setLargeIcon(BitmapFactory.decodeResource(context.resources, R.drawable.ic_emergency))
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setFullScreenIntent(pendingIntent, urgent)
            .addAction(R.drawable.ic_emergency, actionText, pendingIntent)

        if (urgent) {
            notificationBuilder.setDefaults(NotificationCompat.DEFAULT_ALL)
        }

        notificationManager.notify(EMERGENCY_NOTIFICATION_ID, notificationBuilder.build())

        // Store notification in database
        storeNotification(title, message, "EMERGENCY", 5)
        
        analytics.trackUIInteraction("notification", "emergency_shown", title)
    }

    /**
     * Show update notification
     */
    fun showUpdateNotification(
        title: String,
        message: String,
        actionText: String = "Update Now",
        updateUrl: String? = null
    ) {
        val intent = if (updateUrl != null) {
            Intent(Intent.ACTION_VIEW, Uri.parse(updateUrl))
        } else {
            Intent(context, MainActivity::class.java)
        }
        
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notificationBuilder = NotificationCompat.Builder(context, UPDATES_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_update)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .addAction(R.drawable.ic_update, actionText, pendingIntent)

        notificationManager.notify(UPDATE_NOTIFICATION_ID, notificationBuilder.build())

        // Store notification in database
        storeNotification(title, message, "UPDATE", NotificationCompat.PRIORITY_DEFAULT)
        
        analytics.trackUIInteraction("notification", "update_shown", title)
    }

    /**
     * Show system notification
     */
    fun showSystemNotification(
        title: String,
        message: String,
        priority: Int = NotificationCompat.PRIORITY_LOW
    ) {
        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notificationBuilder = NotificationCompat.Builder(context, SYSTEM_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_system)
            .setContentTitle(title)
            .setContentText(message)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(priority)

        notificationManager.notify(SYSTEM_NOTIFICATION_ID, notificationBuilder.build())

        // Store notification in database
        storeNotification(title, message, "SYSTEM", priority)
        
        analytics.trackUIInteraction("notification", "system_shown", title)
    }

    /**
     * Schedule a notification for later
     */
    fun scheduleNotification(
        title: String,
        message: String,
        type: String,
        delayMinutes: Long,
        data: Map<String, String> = emptyMap()
    ) {
        val workRequest = OneTimeWorkRequestBuilder<ScheduledNotificationWorker>()
            .setInitialDelay(delayMinutes, TimeUnit.MINUTES)
            .setInputData(
                Data.Builder()
                    .putString("title", title)
                    .putString("message", message)
                    .putString("type", type)
                    .putAll(data)
                    .build()
            )
            .build()

        WorkManager.getInstance(context).enqueue(workRequest)
        
        analytics.trackUIInteraction("notification", "scheduled", "$type:$delayMinutes")
    }

    /**
     * Handle periodic notifications (like position updates)
     */
    fun schedulePeriodicNotifications() {
        val workRequest = PeriodicWorkRequestBuilder<PeriodicNotificationWorker>(
            15, TimeUnit.MINUTES
        ).build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "periodic_notifications",
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )
    }

    /**
     * Cancel all notifications of a specific type
     */
    fun cancelNotification(notificationId: Int) {
        notificationManager.cancel(notificationId)
    }

    /**
     * Cancel all notifications
     */
    fun cancelAllNotifications() {
        notificationManager.cancelAll()
    }

    /**
     * Store notification in local database
     */
    private fun storeNotification(title: String, message: String, type: String, priority: Int) {
        launch {
            try {
                val notification = NotificationEntity(
                    id = UUID.randomUUID().toString(),
                    title = title,
                    message = message,
                    type = type,
                    priority = priority,
                    timestamp = System.currentTimeMillis(),
                    read = false,
                    actionData = null
                )
                
                database.notificationDao().insertNotification(notification)
            } catch (e: Exception) {
                Log.e("Notifications", "Failed to store notification", e)
            }
        }
    }

    /**
     * Get unread notifications count
     */
    suspend fun getUnreadNotificationsCount(): Int {
        return try {
            database.notificationDao().getUnreadNotifications().first().size
        } catch (e: Exception) {
            0
        }
    }

    /**
     * Mark notification as read
     */
    fun markNotificationAsRead(notificationId: String) {
        launch {
            try {
                database.notificationDao().markAsRead(notificationId)
            } catch (e: Exception) {
                Log.e("Notifications", "Failed to mark notification as read", e)
            }
        }
    }

    /**
     * Clean up old notifications
     */
    fun cleanupOldNotifications() {
        launch {
            try {
                val cutoffTime = System.currentTimeMillis() - (30 * 24 * 60 * 60 * 1000) // 30 days
                database.notificationDao().deleteOldNotifications(cutoffTime)
            } catch (e: Exception) {
                Log.e("Notifications", "Failed to cleanup old notifications", e)
            }
        }
    }

    /**
     * Get FCM token for push notifications
     */
    fun getFCMToken(callback: (String?) -> Unit) {
        firebaseMessaging.token.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val token = task.result
                Log.d("Notifications", "FCM Token: $token")
                callback(token)
            } else {
                Log.e("Notifications", "Failed to get FCM token", task.exception)
                callback(null)
            }
        }
    }

    /**
     * Subscribe to a topic
     */
    fun subscribeToTopic(topic: String) {
        firebaseMessaging.subscribeToTopic(topic)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    Log.d("Notifications", "Subscribed to topic: $topic")
                    analytics.trackUIInteraction("notification", "topic_subscribed", topic)
                } else {
                    Log.e("Notifications", "Failed to subscribe to topic: $topic", task.exception)
                }
            }
    }

    /**
     * Unsubscribe from a topic
     */
    fun unsubscribeFromTopic(topic: String) {
        firebaseMessaging.unsubscribeFromTopic(topic)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    Log.d("Notifications", "Unsubscribed from topic: $topic")
                    analytics.trackUIInteraction("notification", "topic_unsubscribed", topic)
                } else {
                    Log.e("Notifications", "Failed to unsubscribe from topic: $topic", task.exception)
                }
            }
    }

    /**
     * Cleanup resources
     */
    fun cleanup() {
        cleanupOldNotifications()
        job.cancel()
    }
}

/**
 * Firebase Cloud Messaging Service
 */
class IndoorNavigationMessagingService : FirebaseMessagingService() {
    
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        
        val notificationManager = IndoorNotificationManager.getInstance(this)
        val analytics = AnalyticsManager.getInstance(this)
        
        Log.d("FCM", "Message received from: ${remoteMessage.from}")
        
        // Handle data payload
        remoteMessage.data.isNotEmpty().let {
            Log.d("FCM", "Message data payload: ${remoteMessage.data}")
            
            val type = remoteMessage.data["type"] ?: "system"
            val title = remoteMessage.data["title"] ?: "Indoor Navigation"
            val message = remoteMessage.data["message"] ?: ""
            
            when (type) {
                "emergency" -> {
                    notificationManager.showEmergencyNotification(title, message)
                }
                "navigation" -> {
                    notificationManager.showNavigationNotification(title, message)
                }
                "update" -> {
                    val updateUrl = remoteMessage.data["update_url"]
                    notificationManager.showUpdateNotification(title, message, updateUrl = updateUrl)
                }
                else -> {
                    notificationManager.showSystemNotification(title, message)
                }
            }
        }
        
        // Handle notification payload
        remoteMessage.notification?.let { notification ->
            Log.d("FCM", "Message Notification Body: ${notification.body}")
            
            val title = notification.title ?: "Indoor Navigation"
            val message = notification.body ?: ""
            
            notificationManager.showSystemNotification(title, message)
        }
        
        analytics.trackUIInteraction("notification", "fcm_received", remoteMessage.from ?: "unknown")
    }
    
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d("FCM", "Refreshed token: $token")
        
        // Send token to server
        sendTokenToServer(token)
    }
    
    private fun sendTokenToServer(token: String) {
        // In a real app, send this token to your server
        Log.d("FCM", "Sending token to server: $token")
    }
}

/**
 * Worker for scheduled notifications
 */
class ScheduledNotificationWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    
    override suspend fun doWork(): Result {
        val title = inputData.getString("title") ?: return Result.failure()
        val message = inputData.getString("message") ?: return Result.failure()
        val type = inputData.getString("type") ?: "system"
        
        val notificationManager = IndoorNotificationManager.getInstance(applicationContext)
        
        when (type.lowercase()) {
            "emergency" -> notificationManager.showEmergencyNotification(title, message)
            "navigation" -> notificationManager.showNavigationNotification(title, message)
            "update" -> notificationManager.showUpdateNotification(title, message)
            else -> notificationManager.showSystemNotification(title, message)
        }
        
        return Result.success()
    }
}

/**
 * Worker for periodic notifications
 */
class PeriodicNotificationWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    
    override suspend fun doWork(): Result {
        val notificationManager = IndoorNotificationManager.getInstance(applicationContext)
        
        // Clean up old notifications
        notificationManager.cleanupOldNotifications()
        
        // Check for any pending notifications or alerts
        // This could include position updates, beacon status, etc.
        
        return Result.success()
    }
}
