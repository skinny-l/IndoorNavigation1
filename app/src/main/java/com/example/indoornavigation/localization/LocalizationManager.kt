package com.example.indoornavigation.localization

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.os.Build
import android.util.Log
import java.util.*

/**
 * Comprehensive localization manager for multi-language support
 */
class LocalizationManager(private val context: Context) {
    
    private val preferences: SharedPreferences = context.getSharedPreferences("localization_prefs", Context.MODE_PRIVATE)
    
    companion object {
        private const val SELECTED_LANGUAGE = "selected_language"
        private const val SELECTED_COUNTRY = "selected_country"
        private const val DEFAULT_LANGUAGE = "en"
        private const val DEFAULT_COUNTRY = "US"
        
        // Supported languages
        val SUPPORTED_LANGUAGES = mapOf(
            "en" to LanguageInfo("English", "English", "US", false),
            "es" to LanguageInfo("Español", "Spanish", "ES", false),
            "fr" to LanguageInfo("Français", "French", "FR", false),
            "de" to LanguageInfo("Deutsch", "German", "DE", false),
            "it" to LanguageInfo("Italiano", "Italian", "IT", false),
            "pt" to LanguageInfo("Português", "Portuguese", "BR", false),
            "zh" to LanguageInfo("中文", "Chinese", "CN", false),
            "ja" to LanguageInfo("日本語", "Japanese", "JP", false),
            "ko" to LanguageInfo("한국어", "Korean", "KR", false),
            "ar" to LanguageInfo("العربية", "Arabic", "SA", true),
            "he" to LanguageInfo("עברית", "Hebrew", "IL", true),
            "hi" to LanguageInfo("हिन्दी", "Hindi", "IN", false),
            "ru" to LanguageInfo("Русский", "Russian", "RU", false),
            "th" to LanguageInfo("ไทย", "Thai", "TH", false),
            "vi" to LanguageInfo("Tiếng Việt", "Vietnamese", "VN", false)
        )
        
        @Volatile
        private var INSTANCE: LocalizationManager? = null
        
        fun getInstance(context: Context): LocalizationManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: LocalizationManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    /**
     * Get current language code
     */
    fun getCurrentLanguage(): String {
        return preferences.getString(SELECTED_LANGUAGE, getSystemLanguage()) ?: DEFAULT_LANGUAGE
    }
    
    /**
     * Get current country code
     */
    fun getCurrentCountry(): String {
        return preferences.getString(SELECTED_COUNTRY, getSystemCountry()) ?: DEFAULT_COUNTRY
    }
    
    /**
     * Get current locale
     */
    fun getCurrentLocale(): Locale {
        return Locale(getCurrentLanguage(), getCurrentCountry())
    }
    
    /**
     * Set app language
     */
    fun setLanguage(languageCode: String, countryCode: String? = null) {
        val language = if (SUPPORTED_LANGUAGES.containsKey(languageCode)) {
            languageCode
        } else {
            DEFAULT_LANGUAGE
        }
        
        val country = countryCode ?: SUPPORTED_LANGUAGES[language]?.defaultCountry ?: DEFAULT_COUNTRY
        
        preferences.edit()
            .putString(SELECTED_LANGUAGE, language)
            .putString(SELECTED_COUNTRY, country)
            .apply()
        
        Log.d("Localization", "Language set to: $language-$country")
    }
    
    /**
     * Apply locale to context
     */
    fun applyLocale(context: Context): Context {
        val locale = getCurrentLocale()
        return updateContextLocale(context, locale)
    }
    
    /**
     * Update context with new locale
     */
    private fun updateContextLocale(context: Context, locale: Locale): Context {
        Locale.setDefault(locale)
        
        val configuration = Configuration(context.resources.configuration)
        
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            configuration.setLocale(locale)
            configuration.setLayoutDirection(locale)
            context.createConfigurationContext(configuration)
        } else {
            @Suppress("DEPRECATION")
            configuration.locale = locale
            context.resources.updateConfiguration(configuration, context.resources.displayMetrics)
            context
        }
    }
    
    /**
     * Check if current language is RTL
     */
    fun isRTL(): Boolean {
        val currentLanguage = getCurrentLanguage()
        return SUPPORTED_LANGUAGES[currentLanguage]?.isRTL == true
    }
    
    /**
     * Get system language
     */
    private fun getSystemLanguage(): String {
        val systemLocale = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            context.resources.configuration.locales[0]
        } else {
            @Suppress("DEPRECATION")
            context.resources.configuration.locale
        }
        return systemLocale.language
    }
    
    /**
     * Get system country
     */
    private fun getSystemCountry(): String {
        val systemLocale = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            context.resources.configuration.locales[0]
        } else {
            @Suppress("DEPRECATION")
            context.resources.configuration.locale
        }
        return systemLocale.country
    }
    
    /**
     * Get localized navigation instructions
     */
    fun getNavigationInstruction(instructionKey: String, vararg args: Any): String {
        val currentLanguage = getCurrentLanguage()
        return getLocalizedString(instructionKey, currentLanguage, *args)
    }
    
    /**
     * Get localized POI categories
     */
    fun getPOICategory(categoryKey: String): String {
        val currentLanguage = getCurrentLanguage()
        return getLocalizedString("poi_category_$categoryKey", currentLanguage)
    }
    
    /**
     * Get localized floor names
     */
    fun getFloorName(floor: Int): String {
        val currentLanguage = getCurrentLanguage()
        return when {
            floor == 0 -> getLocalizedString("floor_ground", currentLanguage)
            floor > 0 -> getLocalizedString("floor_above", currentLanguage, floor)
            else -> getLocalizedString("floor_below", currentLanguage, Math.abs(floor))
        }
    }
    
    /**
     * Get localized distance format
     */
    fun formatDistance(distanceMeters: Double): String {
        val currentLanguage = getCurrentLanguage()
        val country = getCurrentCountry()
        
        return if (country == "US" || country == "LR" || country == "MM") {
            // Imperial system
            formatDistanceImperial(distanceMeters, currentLanguage)
        } else {
            // Metric system
            formatDistanceMetric(distanceMeters, currentLanguage)
        }
    }
    
    /**
     * Format distance in metric system
     */
    private fun formatDistanceMetric(distanceMeters: Double, language: String): String {
        return when {
            distanceMeters < 1.0 -> {
                val cm = (distanceMeters * 100).toInt()
                getLocalizedString("distance_centimeters", language, cm)
            }
            distanceMeters < 1000.0 -> {
                val meters = distanceMeters.toInt()
                getLocalizedString("distance_meters", language, meters)
            }
            else -> {
                val km = distanceMeters / 1000.0
                val formatted = String.format("%.1f", km)
                getLocalizedString("distance_kilometers", language, formatted)
            }
        }
    }
    
    /**
     * Format distance in imperial system
     */
    private fun formatDistanceImperial(distanceMeters: Double, language: String): String {
        val feet = distanceMeters * 3.28084
        return when {
            feet < 5280.0 -> {
                val feetInt = feet.toInt()
                getLocalizedString("distance_feet", language, feetInt)
            }
            else -> {
                val miles = feet / 5280.0
                val formatted = String.format("%.1f", miles)
                getLocalizedString("distance_miles", language, formatted)
            }
        }
    }
    
    /**
     * Get localized time format
     */
    fun formatTime(timeSeconds: Long): String {
        val currentLanguage = getCurrentLanguage()
        
        return when {
            timeSeconds < 60 -> {
                getLocalizedString("time_seconds", currentLanguage, timeSeconds)
            }
            timeSeconds < 3600 -> {
                val minutes = timeSeconds / 60
                val seconds = timeSeconds % 60
                if (seconds == 0L) {
                    getLocalizedString("time_minutes", currentLanguage, minutes)
                } else {
                    getLocalizedString("time_minutes_seconds", currentLanguage, minutes, seconds)
                }
            }
            else -> {
                val hours = timeSeconds / 3600
                val minutes = (timeSeconds % 3600) / 60
                if (minutes == 0L) {
                    getLocalizedString("time_hours", currentLanguage, hours)
                } else {
                    getLocalizedString("time_hours_minutes", currentLanguage, hours, minutes)
                }
            }
        }
    }
    
    /**
     * Get localized accessibility announcements
     */
    fun getAccessibilityAnnouncement(announcementKey: String, vararg args: Any): String {
        val currentLanguage = getCurrentLanguage()
        return getLocalizedString("accessibility_$announcementKey", currentLanguage, *args)
    }
    
    /**
     * Get localized string from resources or fallback
     */
    private fun getLocalizedString(key: String, language: String, vararg args: Any): String {
        return try {
            // In a real implementation, this would load from string resources
            // For now, we'll use a fallback system
            getLocalizedStringFallback(key, language, *args)
        } catch (e: Exception) {
            Log.e("Localization", "Failed to get localized string for key: $key", e)
            key // Return the key as fallback
        }
    }
    
    /**
     * Fallback localization system
     */
    private fun getLocalizedStringFallback(key: String, language: String, vararg args: Any): String {
        val localizedStrings = getLocalizedStrings(language)
        val template = localizedStrings[key] ?: getLocalizedStrings("en")[key] ?: key
        
        return if (args.isNotEmpty()) {
            String.format(template, *args)
        } else {
            template
        }
    }
    
    /**
     * Get localized strings map for a language
     */
    private fun getLocalizedStrings(language: String): Map<String, String> {
        return when (language) {
            "en" -> englishStrings
            "es" -> spanishStrings
            "fr" -> frenchStrings
            "de" -> germanStrings
            "zh" -> chineseStrings
            "ja" -> japaneseStrings
            "ar" -> arabicStrings
            else -> englishStrings
        }
    }
    
    /**
     * Get supported languages list
     */
    fun getSupportedLanguages(): List<LanguageInfo> {
        return SUPPORTED_LANGUAGES.values.toList()
    }
    
    /**
     * Get language info for current language
     */
    fun getCurrentLanguageInfo(): LanguageInfo {
        val currentLanguage = getCurrentLanguage()
        return SUPPORTED_LANGUAGES[currentLanguage] ?: SUPPORTED_LANGUAGES["en"]!!
    }
    
    // Localized strings - In a real app, these would be in string resources
    private val englishStrings = mapOf(
        "navigation_started" to "Navigation started to %s",
        "navigation_ended" to "You have arrived at %s",
        "turn_left" to "Turn left",
        "turn_right" to "Turn right",
        "go_straight" to "Continue straight",
        "go_upstairs" to "Go upstairs to %s",
        "go_downstairs" to "Go downstairs to %s",
        "floor_ground" to "Ground floor",
        "floor_above" to "Floor %d",
        "floor_below" to "Basement %d",
        "distance_centimeters" to "%d cm",
        "distance_meters" to "%d m",
        "distance_kilometers" to "%s km",
        "distance_feet" to "%d ft",
        "distance_miles" to "%s mi",
        "time_seconds" to "%d seconds",
        "time_minutes" to "%d minutes",
        "time_minutes_seconds" to "%d min %d sec",
        "time_hours" to "%d hours",
        "time_hours_minutes" to "%d hr %d min"
    )
    
    private val spanishStrings = mapOf(
        "navigation_started" to "Navegación iniciada hacia %s",
        "navigation_ended" to "Has llegado a %s",
        "turn_left" to "Gira a la izquierda",
        "turn_right" to "Gira a la derecha",
        "go_straight" to "Continúa recto",
        "go_upstairs" to "Sube al %s",
        "go_downstairs" to "Baja al %s",
        "floor_ground" to "Planta baja",
        "floor_above" to "Piso %d",
        "floor_below" to "Sótano %d",
        "distance_centimeters" to "%d cm",
        "distance_meters" to "%d m",
        "distance_kilometers" to "%s km",
        "time_seconds" to "%d segundos",
        "time_minutes" to "%d minutos",
        "time_minutes_seconds" to "%d min %d seg",
        "time_hours" to "%d horas",
        "time_hours_minutes" to "%d hr %d min"
    )
    
    private val frenchStrings = mapOf(
        "navigation_started" to "Navigation commencée vers %s",
        "navigation_ended" to "Vous êtes arrivé à %s",
        "turn_left" to "Tournez à gauche",
        "turn_right" to "Tournez à droite",
        "go_straight" to "Continuez tout droit",
        "go_upstairs" to "Montez au %s",
        "go_downstairs" to "Descendez au %s",
        "floor_ground" to "Rez-de-chaussée",
        "floor_above" to "Étage %d",
        "floor_below" to "Sous-sol %d",
        "distance_centimeters" to "%d cm",
        "distance_meters" to "%d m",
        "distance_kilometers" to "%s km",
        "time_seconds" to "%d secondes",
        "time_minutes" to "%d minutes",
        "time_minutes_seconds" to "%d min %d sec",
        "time_hours" to "%d heures",
        "time_hours_minutes" to "%d h %d min"
    )
    
    private val germanStrings = mapOf(
        "navigation_started" to "Navigation zu %s gestartet",
        "navigation_ended" to "Sie sind bei %s angekommen",
        "turn_left" to "Links abbiegen",
        "turn_right" to "Rechts abbiegen",
        "go_straight" to "Geradeaus gehen",
        "go_upstairs" to "Gehen Sie hoch zum %s",
        "go_downstairs" to "Gehen Sie runter zum %s",
        "floor_ground" to "Erdgeschoss",
        "floor_above" to "Stock %d",
        "floor_below" to "Untergeschoss %d",
        "distance_centimeters" to "%d cm",
        "distance_meters" to "%d m",
        "distance_kilometers" to "%s km",
        "time_seconds" to "%d Sekunden",
        "time_minutes" to "%d Minuten",
        "time_minutes_seconds" to "%d Min %d Sek",
        "time_hours" to "%d Stunden",
        "time_hours_minutes" to "%d Std %d Min"
    )
    
    private val chineseStrings = mapOf(
        "navigation_started" to "开始导航到 %s",
        "navigation_ended" to "您已到达 %s",
        "turn_left" to "左转",
        "turn_right" to "右转",
        "go_straight" to "直行",
        "go_upstairs" to "上楼到 %s",
        "go_downstairs" to "下楼到 %s",
        "floor_ground" to "一楼",
        "floor_above" to "%d楼",
        "floor_below" to "地下%d层",
        "distance_centimeters" to "%d厘米",
        "distance_meters" to "%d米",
        "distance_kilometers" to "%s公里",
        "time_seconds" to "%d秒",
        "time_minutes" to "%d分钟",
        "time_minutes_seconds" to "%d分%d秒",
        "time_hours" to "%d小时",
        "time_hours_minutes" to "%d小时%d分钟"
    )
    
    private val japaneseStrings = mapOf(
        "navigation_started" to "%sへのナビゲーションを開始しました",
        "navigation_ended" to "%sに到着しました",
        "turn_left" to "左に曲がってください",
        "turn_right" to "右に曲がってください",
        "go_straight" to "直進してください",
        "go_upstairs" to "%sに上がってください",
        "go_downstairs" to "%sに下がってください",
        "floor_ground" to "1階",
        "floor_above" to "%d階",
        "floor_below" to "地下%d階",
        "distance_centimeters" to "%dcm",
        "distance_meters" to "%dm",
        "distance_kilometers" to "%skm",
        "time_seconds" to "%d秒",
        "time_minutes" to "%d分",
        "time_minutes_seconds" to "%d分%d秒",
        "time_hours" to "%d時間",
        "time_hours_minutes" to "%d時間%d分"
    )
    
    private val arabicStrings = mapOf(
        "navigation_started" to "بدأت الملاحة إلى %s",
        "navigation_ended" to "وصلت إلى %s",
        "turn_left" to "اتجه يساراً",
        "turn_right" to "اتجه يميناً",
        "go_straight" to "استمر مستقيماً",
        "go_upstairs" to "اصعد إلى %s",
        "go_downstairs" to "انزل إلى %s",
        "floor_ground" to "الطابق الأرضي",
        "floor_above" to "الطابق %d",
        "floor_below" to "القبو %d",
        "distance_centimeters" to "%d سم",
        "distance_meters" to "%d م",
        "distance_kilometers" to "%s كم",
        "time_seconds" to "%d ثانية",
        "time_minutes" to "%d دقيقة",
        "time_minutes_seconds" to "%d د %d ث",
        "time_hours" to "%d ساعة",
        "time_hours_minutes" to "%d س %d د"
    )
}

/**
 * Language information data class
 */
data class LanguageInfo(
    val nativeName: String,
    val englishName: String,
    val defaultCountry: String,
    val isRTL: Boolean
)