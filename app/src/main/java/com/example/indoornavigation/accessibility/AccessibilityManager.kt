package com.example.indoornavigation.accessibility

import android.content.Context
import android.content.SharedPreferences
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import android.view.accessibility.AccessibilityManager
import androidx.core.content.ContextCompat
import com.example.indoornavigation.analytics.AnalyticsManager
import com.example.indoornavigation.data.models.Position
import com.example.indoornavigation.utils.ProximityNotificationManager
import com.example.indoornavigation.utils.SettingsManager
import kotlinx.coroutines.*
import java.util.*
import kotlin.coroutines.CoroutineContext

/**
 * Comprehensive accessibility manager for users with disabilities
 * Provides TTS, voice guidance, audio cues, and accessibility features
 */
class AccessibilityManager(
    private val context: Context
) : CoroutineScope {

    private val job = SupervisorJob()
    override val coroutineContext: CoroutineContext = Dispatchers.Main + job

    private var textToSpeech: TextToSpeech? = null
    private var isTTSInitialized = false
    private var isVoiceGuidanceEnabled = true
    private var isAudioCuesEnabled = true
    private var speechRate = 1.0f
    private var speechPitch = 1.0f
    private var voiceGuidanceVolume = 0.8f
    
    private val handler = Handler(Looper.getMainLooper())
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val systemAccessibilityManager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
    private val analytics = AnalyticsManager.getInstance(context)
    private val proximityNotifier = ProximityNotificationManager(context, SettingsManager(context))
    
    private val preferences: SharedPreferences = context.getSharedPreferences("accessibility_prefs", Context.MODE_PRIVATE)
    
    // Speech queue to manage multiple announcements
    private val speechQueue = mutableListOf<SpeechItem>()
    private var currentSpeechId: String? = null
    private var isCurrentlySpeaking = false
    
    // Voice guidance phrases
    private val guidancePhrases = mapOf(
        "navigation_started" to "Navigation started to %s",
        "navigation_ended" to "You have arrived at %s",
        "turn_left" to "Turn left",
        "turn_right" to "Turn right", 
        "go_straight" to "Continue straight",
        "go_upstairs" to "Go upstairs to floor %s",
        "go_downstairs" to "Go downstairs to floor %s",
        "take_elevator" to "Take the elevator to floor %s",
        "destination_ahead" to "Destination is %s meters ahead",
        "off_route" to "You are off the route. Recalculating path.",
        "weak_signal" to "Position signal is weak. Please move closer to beacons.",
        "beacon_detected" to "Beacon detected. Position updated.",
        "poi_nearby" to "%s is nearby on your %s",
        "obstacle_ahead" to "Obstacle detected ahead. Please navigate carefully.",
        "emergency_exit" to "Emergency exit is on your %s"
    )

    init {
        loadPreferences()
        initializeTextToSpeech()
        setupAccessibilityFeatures()
    }

    /**
     * Initialize Text-to-Speech engine
     */
    private fun initializeTextToSpeech() {
        textToSpeech = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                isTTSInitialized = true
                configureTTS()
                analytics.trackAccessibility("tts_initialized", true)
                Log.d("Accessibility", "TTS initialized successfully")
            } else {
                Log.e("Accessibility", "TTS initialization failed")
                analytics.trackError(Exception("TTS initialization failed"), "accessibility")
            }
        }
    }

    /**
     * Configure TTS settings
     */
    private fun configureTTS() {
        textToSpeech?.let { tts ->
            // Set language
            val locale = Locale.getDefault()
            val result = tts.setLanguage(locale)
            
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.w("Accessibility", "Language not supported, using default")
                tts.setLanguage(Locale.US)
            }
            
            // Set speech parameters
            tts.setSpeechRate(speechRate)
            tts.setPitch(speechPitch)
            
            // Set up utterance progress listener
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    isCurrentlySpeaking = true
                    currentSpeechId = utteranceId
                }

                override fun onDone(utteranceId: String?) {
                    isCurrentlySpeaking = false
                    currentSpeechId = null
                    processNextSpeechItem()
                }

                override fun onError(utteranceId: String?) {
                    isCurrentlySpeaking = false
                    currentSpeechId = null
                    processNextSpeechItem()
                    Log.e("Accessibility", "TTS error for utterance: $utteranceId")
                }
            })
        }
    }

    /**
     * Setup accessibility features
     */
    private fun setupAccessibilityFeatures() {
        // Check if accessibility services are enabled
        if (systemAccessibilityManager.isEnabled) {
            analytics.trackAccessibility("system_accessibility_enabled", true)
        }
        
        // Setup proximity notifications for audio cues
        // proximityNotifier.setAccessibilityMode(true) // Method not available
    }

    /**
     * Speak text with voice guidance
     */
    fun speak(text: String, priority: SpeechPriority = SpeechPriority.NORMAL, interrupt: Boolean = false) {
        if (!isVoiceGuidanceEnabled) return
        
        val speechItem = SpeechItem(
            text = text,
            priority = priority,
            timestamp = System.currentTimeMillis(),
            id = UUID.randomUUID().toString()
        )
        
        if (interrupt) {
            stopSpeaking()
            speechQueue.clear()
        }
        
        when (priority) {
            SpeechPriority.EMERGENCY -> {
                speechQueue.add(0, speechItem) // Add to front
                if (!isCurrentlySpeaking) {
                    processSpeechItem(speechItem)
                }
            }
            SpeechPriority.HIGH -> {
                val insertIndex = speechQueue.indexOfFirst { it.priority == SpeechPriority.NORMAL }
                if (insertIndex != -1) {
                    speechQueue.add(insertIndex, speechItem)
                } else {
                    speechQueue.add(speechItem)
                }
                if (!isCurrentlySpeaking) {
                    processNextSpeechItem()
                }
            }
            SpeechPriority.NORMAL -> {
                speechQueue.add(speechItem)
                if (!isCurrentlySpeaking) {
                    processNextSpeechItem()
                }
            }
        }
    }

    /**
     * Process next speech item in queue
     */
    private fun processNextSpeechItem() {
        if (speechQueue.isNotEmpty()) {
            val nextItem = speechQueue.removeAt(0)
            processSpeechItem(nextItem)
        }
    }

    /**
     * Process individual speech item
     */
    private fun processSpeechItem(item: SpeechItem) {
        if (!isTTSInitialized || textToSpeech == null) {
            Log.w("Accessibility", "TTS not initialized, cannot speak: ${item.text}")
            return
        }
        
        // Adjust volume for voice guidance
        val originalVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val targetVolume = (maxVolume * voiceGuidanceVolume).toInt()
        
        // Set volume temporarily
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetVolume, 0)
        
        // Create speech parameters
        val params = Bundle()
        params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, item.id)
        params.putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, voiceGuidanceVolume)
        
        // Speak the text
        val result = textToSpeech?.speak(item.text, TextToSpeech.QUEUE_FLUSH, params, item.id)
        
        if (result == TextToSpeech.ERROR) {
            Log.e("Accessibility", "Failed to speak: ${item.text}")
            isCurrentlySpeaking = false
            processNextSpeechItem()
        }
        
        // Restore original volume after delay
        handler.postDelayed({
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, originalVolume, 0)
        }, 2000)
    }

    /**
     * Stop current speech
     */
    fun stopSpeaking() {
        textToSpeech?.stop()
        isCurrentlySpeaking = false
        currentSpeechId = null
    }

    /**
     * Clear speech queue
     */
    fun clearSpeechQueue() {
        speechQueue.clear()
    }

    /**
     * Navigation voice guidance
     */
    fun announceNavigation(instruction: String, extraInfo: String = "") {
        val phrase = guidancePhrases[instruction] ?: instruction
        val announcement = if (extraInfo.isNotEmpty()) {
            String.format(phrase, extraInfo)
        } else {
            phrase
        }
        
        speak(announcement, SpeechPriority.HIGH)
        analytics.trackAccessibility("voice_guidance_used", true)
    }

    /**
     * Announce POI information
     */
    fun announcePOI(poiName: String, direction: String? = null, distance: Double? = null) {
        val announcement = when {
            direction != null && distance != null -> {
                "Point of interest: $poiName is ${distance.toInt()} meters to your $direction"
            }
            direction != null -> {
                "Point of interest: $poiName is to your $direction"
            }
            else -> {
                "Point of interest: $poiName"
            }
        }
        
        speak(announcement, SpeechPriority.NORMAL)
    }

    /**
     * Announce position update
     */
    fun announcePositionUpdate(position: Position, accuracy: Float) {
        if (accuracy > 10.0f) { // Low accuracy warning
            speak("Position signal is weak. Please move closer to beacons.", SpeechPriority.HIGH)
        } else {
            val floorName = when (position.floor) {
                0 -> "ground floor"
                1 -> "first floor"
                2 -> "second floor"
                else -> "floor ${position.floor}"
            }
            
            speak("Position updated on $floorName", SpeechPriority.NORMAL)
        }
    }

    /**
     * Announce emergency information
     */
    fun announceEmergency(message: String) {
        speak("Emergency: $message", SpeechPriority.EMERGENCY, interrupt = true)
        
        // Also trigger haptic feedback if available
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // proximityNotifier.triggerEmergencyVibration() // Method not available
        }
    }

    /**
     * Play audio cue for specific events
     */
    fun playAudioCue(cueType: AudioCueType) {
        if (!isAudioCuesEnabled) return
        
        when (cueType) {
            AudioCueType.BEACON_DETECTED -> {
                // Play short beep
                // proximityNotifier.playBeaconDetectedSound() // Method not available
            }
            AudioCueType.DESTINATION_REACHED -> {
                // Play success sound
                // proximityNotifier.playDestinationReachedSound() // Method not available
            }
            AudioCueType.OFF_ROUTE -> {
                // Play warning sound
                // proximityNotifier.playOffRouteSound() // Method not available
            }
            AudioCueType.TURN_APPROACHING -> {
                // Play turn notification sound
                // proximityNotifier.playTurnNotificationSound() // Method not available
            }
            AudioCueType.OBSTACLE_DETECTED -> {
                // Play obstacle warning
                // proximityNotifier.playObstacleWarningSound() // Method not available
            }
        }
        
        analytics.trackAccessibility("audio_cue_played", true)
    }

    /**
     * Enable/disable voice guidance
     */
    fun setVoiceGuidanceEnabled(enabled: Boolean) {
        isVoiceGuidanceEnabled = enabled
        preferences.edit().putBoolean("voice_guidance_enabled", enabled).apply()
        analytics.trackAccessibility("voice_guidance_enabled", enabled)
        
        if (!enabled) {
            stopSpeaking()
            clearSpeechQueue()
        }
    }

    /**
     * Enable/disable audio cues
     */
    fun setAudioCuesEnabled(enabled: Boolean) {
        isAudioCuesEnabled = enabled
        preferences.edit().putBoolean("audio_cues_enabled", enabled).apply()
        analytics.trackAccessibility("audio_cues_enabled", enabled)
    }

    /**
     * Set speech rate
     */
    fun setSpeechRate(rate: Float) {
        speechRate = rate.coerceIn(0.1f, 3.0f)
        textToSpeech?.setSpeechRate(speechRate)
        preferences.edit().putFloat("speech_rate", speechRate).apply()
        analytics.trackAccessibility("speech_rate_changed", true)
    }

    /**
     * Set speech pitch
     */
    fun setSpeechPitch(pitch: Float) {
        speechPitch = pitch.coerceIn(0.1f, 2.0f)
        textToSpeech?.setPitch(speechPitch)
        preferences.edit().putFloat("speech_pitch", speechPitch).apply()
        analytics.trackAccessibility("speech_pitch_changed", true)
    }

    /**
     * Set voice guidance volume
     */
    fun setVoiceGuidanceVolume(volume: Float) {
        voiceGuidanceVolume = volume.coerceIn(0.1f, 1.0f)
        preferences.edit().putFloat("voice_guidance_volume", voiceGuidanceVolume).apply()
        analytics.trackAccessibility("voice_volume_changed", true)
    }

    /**
     * Load preferences
     */
    private fun loadPreferences() {
        isVoiceGuidanceEnabled = preferences.getBoolean("voice_guidance_enabled", true)
        isAudioCuesEnabled = preferences.getBoolean("audio_cues_enabled", true)
        speechRate = preferences.getFloat("speech_rate", 1.0f)
        speechPitch = preferences.getFloat("speech_pitch", 1.0f)
        voiceGuidanceVolume = preferences.getFloat("voice_guidance_volume", 0.8f)
    }

    /**
     * Get current accessibility settings
     */
    fun getAccessibilitySettings(): AccessibilitySettings {
        return AccessibilitySettings(
            voiceGuidanceEnabled = isVoiceGuidanceEnabled,
            audioCuesEnabled = isAudioCuesEnabled,
            speechRate = speechRate,
            speechPitch = speechPitch,
            voiceGuidanceVolume = voiceGuidanceVolume,
            ttsInitialized = isTTSInitialized,
            systemAccessibilityEnabled = systemAccessibilityManager.isEnabled
        )
    }

    /**
     * Check if accessibility features are needed
     */
    fun isAccessibilityRequired(): Boolean {
        return systemAccessibilityManager.isEnabled || 
               systemAccessibilityManager.isTouchExplorationEnabled ||
               isVoiceGuidanceEnabled
    }

    /**
     * Cleanup resources
     */
    fun cleanup() {
        stopSpeaking()
        clearSpeechQueue()
        textToSpeech?.shutdown()
        // proximityNotifier.cleanup() // Method not available
        job.cancel()
    }
}

/**
 * Speech priority levels
 */
enum class SpeechPriority {
    NORMAL,
    HIGH,
    EMERGENCY
}

/**
 * Audio cue types
 */
enum class AudioCueType {
    BEACON_DETECTED,
    DESTINATION_REACHED,
    OFF_ROUTE,
    TURN_APPROACHING,
    OBSTACLE_DETECTED
}

/**
 * Speech item for queue management
 */
data class SpeechItem(
    val text: String,
    val priority: SpeechPriority,
    val timestamp: Long,
    val id: String
)

/**
 * Accessibility settings data class
 */
data class AccessibilitySettings(
    val voiceGuidanceEnabled: Boolean,
    val audioCuesEnabled: Boolean,
    val speechRate: Float,
    val speechPitch: Float,
    val voiceGuidanceVolume: Float,
    val ttsInitialized: Boolean,
    val systemAccessibilityEnabled: Boolean
)

