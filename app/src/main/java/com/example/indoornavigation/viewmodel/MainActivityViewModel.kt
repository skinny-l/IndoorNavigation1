package com.example.indoornavigation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainActivityViewModel : ViewModel() {

    // Theme mode (light/dark)
    private val _isDarkMode = MutableStateFlow(false)
    val isDarkMode: StateFlow<Boolean> = _isDarkMode.asStateFlow()

    // Debug mode
    private val _isDebugMode = MutableStateFlow(false)
    val isDebugMode: StateFlow<Boolean> = _isDebugMode.asStateFlow()

    // Language selection
    private val _selectedLanguage = MutableStateFlow("English")
    val selectedLanguage: StateFlow<String> = _selectedLanguage.asStateFlow()

    /**
     * Toggle theme mode
     */
    fun toggleDarkMode() {
        viewModelScope.launch {
            _isDarkMode.value = !_isDarkMode.value
        }
    }

    /**
     * Toggle debug mode
     */
    fun toggleDebugMode() {
        viewModelScope.launch {
            _isDebugMode.value = !_isDebugMode.value
        }
    }

    /**
     * Set the selected language
     */
    fun setLanguage(language: String) {
        viewModelScope.launch {
            _selectedLanguage.value = language
        }
    }
}
