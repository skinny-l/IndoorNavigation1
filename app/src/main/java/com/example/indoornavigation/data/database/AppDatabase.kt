package com.example.indoornavigation.data.database

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import android.content.Context
import net.sqlcipher.database.SQLiteDatabase
import net.sqlcipher.database.SupportFactory
import com.example.indoornavigation.data.database.dao.*
import com.example.indoornavigation.data.database.entities.*
import com.example.indoornavigation.data.models.POIEntity

@Database(
    entities = [
        POIEntity::class,
        CachedPOIEntity::class,
        CachedRouteEntity::class,
        OfflineMapEntity::class,
        UserLocationHistoryEntity::class,
        BeaconEntity::class,
        WifiAccessPointEntity::class,
        NavigationHistoryEntity::class,
        UserPreferencesEntity::class,
        AnalyticsEventEntity::class,
        CrashReportEntity::class,
        FingerprintEntity::class,
        FloorPlanEntity::class,
        AccessibilityRouteEntity::class,
        EmergencyContactEntity::class,
        NotificationEntity::class
    ],
    version = 2,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    
    abstract fun poiDao(): POIDao
    abstract fun routeDao(): RouteDao
    abstract fun offlineMapDao(): OfflineMapDao
    abstract fun locationHistoryDao(): LocationHistoryDao
    abstract fun beaconDao(): BeaconDao
    abstract fun wifiDao(): WifiDao
    abstract fun navigationHistoryDao(): NavigationHistoryDao
    abstract fun userPreferencesDao(): UserPreferencesDao
    abstract fun analyticsDao(): AnalyticsDao
    abstract fun crashReportDao(): CrashReportDao
    abstract fun fingerprintDao(): FingerprintDao
    abstract fun floorPlanDao(): FloorPlanDao
    abstract fun accessibilityDao(): AccessibilityDao
    abstract fun emergencyDao(): EmergencyDao
    abstract fun notificationDao(): NotificationDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null
        
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Add new tables for enhanced features
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS `offline_maps` (
                        `id` TEXT NOT NULL PRIMARY KEY,
                        `floor` INTEGER NOT NULL,
                        `map_data` BLOB NOT NULL,
                        `metadata` TEXT NOT NULL,
                        `last_updated` INTEGER NOT NULL,
                        `version` INTEGER NOT NULL
                    )
                """)
                
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS `analytics_events` (
                        `id` TEXT NOT NULL PRIMARY KEY,
                        `event_type` TEXT NOT NULL,
                        `event_data` TEXT NOT NULL,
                        `timestamp` INTEGER NOT NULL,
                        `user_id` TEXT,
                        `session_id` TEXT NOT NULL,
                        `uploaded` INTEGER NOT NULL DEFAULT 0
                    )
                """)
                
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS `crash_reports` (
                        `id` TEXT NOT NULL PRIMARY KEY,
                        `crash_message` TEXT NOT NULL,
                        `stack_trace` TEXT NOT NULL,
                        `device_info` TEXT NOT NULL,
                        `app_version` TEXT NOT NULL,
                        `timestamp` INTEGER NOT NULL,
                        `uploaded` INTEGER NOT NULL DEFAULT 0
                    )
                """)
                
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS `accessibility_routes` (
                        `id` TEXT NOT NULL PRIMARY KEY,
                        `start_x` REAL NOT NULL,
                        `start_y` REAL NOT NULL,
                        `start_floor` INTEGER NOT NULL,
                        `end_x` REAL NOT NULL,
                        `end_y` REAL NOT NULL,
                        `end_floor` INTEGER NOT NULL,
                        `waypoints` TEXT NOT NULL,
                        `accessibility_features` TEXT NOT NULL,
                        `difficulty_level` INTEGER NOT NULL
                    )
                """)
                
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS `emergency_contacts` (
                        `id` TEXT NOT NULL PRIMARY KEY,
                        `name` TEXT NOT NULL,
                        `phone` TEXT NOT NULL,
                        `email` TEXT,
                        `relationship` TEXT NOT NULL,
                        `is_primary` INTEGER NOT NULL DEFAULT 0
                    )
                """)
                
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS `notifications` (
                        `id` TEXT NOT NULL PRIMARY KEY,
                        `title` TEXT NOT NULL,
                        `message` TEXT NOT NULL,
                        `type` TEXT NOT NULL,
                        `priority` INTEGER NOT NULL,
                        `timestamp` INTEGER NOT NULL,
                        `read` INTEGER NOT NULL DEFAULT 0,
                        `action_data` TEXT
                    )
                """)
            }
        }

        fun getDatabase(
            context: Context,
            useEncryption: Boolean = true
        ): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val builder = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "indoor_navigation_database"
                )
                
                if (useEncryption) {
                    // Use SQLCipher for encryption
                    val passphrase = getOrCreatePassphrase(context)
                    val factory = SupportFactory(SQLiteDatabase.getBytes(passphrase.toCharArray()))
                    builder.openHelperFactory(factory)
                }
                
                val instance = builder
                    .addMigrations(MIGRATION_1_2)
                    .fallbackToDestructiveMigration()
                    .build()
                    
                INSTANCE = instance
                instance
            }
        }
        
        private fun getOrCreatePassphrase(context: Context): String {
            val prefs = context.getSharedPreferences("secure_prefs", Context.MODE_PRIVATE)
            var passphrase = prefs.getString("db_passphrase", null)
            
            if (passphrase == null) {
                passphrase = generateSecurePassphrase()
                prefs.edit().putString("db_passphrase", passphrase).apply()
            }
            
            return passphrase
        }
        
        private fun generateSecurePassphrase(): String {
            val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%^&*"
            return (1..32)
                .map { chars.random() }
                .joinToString("")
        }
    }
}
