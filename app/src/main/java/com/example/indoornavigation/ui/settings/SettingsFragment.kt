package com.example.indoornavigation.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.activityViewModels
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import com.example.indoornavigation.R
import com.example.indoornavigation.viewmodel.PositioningViewModel

class SettingsFragment : PreferenceFragmentCompat() {
    
    private val positioningViewModel: PositioningViewModel by activityViewModels()
    
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences, rootKey)
        
        try {
            // Setup positioning mode preference
            findPreference<ListPreference>("pref_positioning_mode")?.setOnPreferenceChangeListener { _, newValue ->
                when (newValue) {
                    "ble_only" -> positioningViewModel.setPositioningMode(PositioningViewModel.PositioningMode.BLE_ONLY)
                    "wifi_only" -> positioningViewModel.setPositioningMode(PositioningViewModel.PositioningMode.WIFI_ONLY)
                    "fusion" -> positioningViewModel.setPositioningMode(PositioningViewModel.PositioningMode.FUSION)
                }
                true
            }
            
            // Setup Wi-Fi positioning enable/disable preference
            findPreference<SwitchPreferenceCompat>("pref_enable_wifi_positioning")?.setOnPreferenceChangeListener { _, newValue ->
                val enabled = newValue as Boolean
                if (enabled) {
                    // If Wi-Fi positioning is enabled, set mode to fusion
                    positioningViewModel.setPositioningMode(PositioningViewModel.PositioningMode.FUSION)
                } else {
                    // If Wi-Fi positioning is disabled, use BLE only
                    positioningViewModel.setPositioningMode(PositioningViewModel.PositioningMode.BLE_ONLY)
                }
                true
            }
            
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
            
            // Handle POI mapping tool preference
            findPreference<Preference>("pref_poi_mapping_tool")?.setOnPreferenceClickListener {
                // Launch POI Mapping Activity
                activity?.let { fragmentActivity ->
                    val intent = android.content.Intent(fragmentActivity, com.example.indoornavigation.ui.map.POIMappingActivity::class.java)
                    fragmentActivity.startActivity(intent)
                }
                true
            }
            
            // Update UI to match current positioning mode
            updateUIForCurrentPositioningMode()
        } catch (e: Exception) {
            // Log any exceptions during preference setup
            android.util.Log.e("SettingsFragment", "Error setting up preferences", e)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val view = super.onCreateView(inflater, container, savedInstanceState)
        
        // Add a toolbar to the top of the view
        val toolbarView = inflater.inflate(R.layout.settings_toolbar, container, false)
        val rootView = container?.context?.let {
            androidx.constraintlayout.widget.ConstraintLayout(it)
        } ?: return view
        
        rootView.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        
        val toolbarLayoutParams = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            resources.getDimensionPixelSize(R.dimen.action_bar_size)
        )
        toolbarLayoutParams.topToTop = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID
        toolbarLayoutParams.startToStart = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID
        toolbarLayoutParams.endToEnd = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID
        
        val viewLayoutParams = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            0
        )
        viewLayoutParams.topToBottom = R.id.settings_toolbar
        viewLayoutParams.bottomToBottom = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID
        viewLayoutParams.startToStart = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID
        viewLayoutParams.endToEnd = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID
        
        rootView.addView(toolbarView, toolbarLayoutParams)
        rootView.addView(view, viewLayoutParams)
        
        // Setup menu button click handler
        toolbarView.findViewById<View>(R.id.btnMenu).setOnClickListener {
            activity?.findViewById<DrawerLayout>(R.id.drawer_layout)?.openDrawer(GravityCompat.START)
        }
        
        return rootView
    }
    
    private fun updateUIForCurrentPositioningMode() {
        val positioningModePreference = findPreference<ListPreference>("pref_positioning_mode")
        val wifiPositioningPreference = findPreference<SwitchPreferenceCompat>("pref_enable_wifi_positioning")
        
        if (positioningModePreference != null && wifiPositioningPreference != null) {
            try {
                when (positioningViewModel.positioningMode.value) {
                    PositioningViewModel.PositioningMode.BLE_ONLY -> {
                        positioningModePreference.value = "ble_only"
                        wifiPositioningPreference.isChecked = false
                    }
                    PositioningViewModel.PositioningMode.WIFI_ONLY -> {
                        positioningModePreference.value = "wifi_only"
                        wifiPositioningPreference.isChecked = true
                    }
                    PositioningViewModel.PositioningMode.FUSION -> {
                        positioningModePreference.value = "fusion"
                        wifiPositioningPreference.isChecked = true
                    }
                    else -> {
                        // Default case
                        positioningModePreference.value = "fusion"
                        wifiPositioningPreference.isChecked = true
                    }
                }
            } catch (e: Exception) {
                // Log any exceptions during UI update
                android.util.Log.e("SettingsFragment", "Error updating UI for positioning mode", e)
            }
        }
    }
}
