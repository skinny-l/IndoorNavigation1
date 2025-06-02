package com.example.indoornavigation.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class MainActivityViewModel : ViewModel() {

    // Theme mode (light/dark)
    private val _isDarkMode = MutableLiveData(false)
    val isDarkMode: LiveData<Boolean> = _isDarkMode

    // Debug mode
    private val _isDebugMode = MutableLiveData(false)
    val isDebugMode: LiveData<Boolean> = _isDebugMode

    // Language selection
    private val _selectedLanguage = MutableLiveData("English")
    val selectedLanguage: LiveData<String> = _selectedLanguage

    /**
     * Toggle theme mode
     */
    fun toggleDarkMode() {
        _isDarkMode.value = _isDarkMode.value?.not() ?: false
    }

    /**
     * Toggle debug mode
     */
    fun toggleDebugMode() {
        _isDebugMode.value = _isDebugMode.value?.not() ?: false
    }

    /**
     * Set the selected language
     */
    fun setLanguage(language: String) {
        _selectedLanguage.value = language
    }
}