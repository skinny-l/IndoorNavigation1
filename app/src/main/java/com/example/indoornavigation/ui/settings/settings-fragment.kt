package com.example.indoornavigation.ui.settings

import android.os.Bundle
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import com.example.indoornavigation.R

class SettingsFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences, rootKey)
        
        // Handle scanning mode preference
        val scanningPref = findPreference<SwitchPreferenceCompat>("pref_continuous_scanning")
        scanningPref?.setOnPreferenceChangeListener { _, newValue ->
            val enabled = newValue as Boolean
            // Update scanning settings
            true
        }
        
        // Handle position smoothing preference
        val smoothingPref = findPreference<SwitchPreferenceCompat>("pref_position_smoothing")
        smoothingPref?.setOnPreferenceChangeListener { _, newValue ->
            val enabled = newValue as Boolean
            // Update smoothing settings
            true
        }
    }
}