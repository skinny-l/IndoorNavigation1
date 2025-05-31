package com.example.indoornavigation.utils

import android.content.Context
import android.content.SharedPreferences

class SharedPreferencesHelper(context: Context) {
    private val sharedPreferences: SharedPreferences = context.getSharedPreferences(
        PREFERENCES_NAME, Context.MODE_PRIVATE
    )

    companion object {
        private const val PREFERENCES_NAME = "indoor_navigation_prefs"
        private const val KEY_IS_DARK_THEME = "is_dark_theme"
        private const val KEY_LANGUAGE_INDEX = "language_index"
        private const val KEY_USER_LOGGED_IN = "user_logged_in"
        private const val KEY_USER_EMAIL = "user_email"
    }

    fun isDarkTheme(): Boolean {
        return sharedPreferences.getBoolean(KEY_IS_DARK_THEME, false)
    }

    fun setDarkTheme(isDarkTheme: Boolean) {
        sharedPreferences.edit().putBoolean(KEY_IS_DARK_THEME, isDarkTheme).apply()
    }

    fun getLanguageIndex(): Int {
        return sharedPreferences.getInt(KEY_LANGUAGE_INDEX, 0)
    }

    fun setLanguageIndex(index: Int) {
        sharedPreferences.edit().putInt(KEY_LANGUAGE_INDEX, index).apply()
    }

    fun isUserLoggedIn(): Boolean {
        return sharedPreferences.getBoolean(KEY_USER_LOGGED_IN, false)
    }

    fun setUserLoggedIn(isLoggedIn: Boolean) {
        sharedPreferences.edit().putBoolean(KEY_USER_LOGGED_IN, isLoggedIn).apply()
    }

    fun getUserEmail(): String? {
        return sharedPreferences.getString(KEY_USER_EMAIL, null)
    }

    fun setUserEmail(email: String) {
        sharedPreferences.edit().putString(KEY_USER_EMAIL, email).apply()
    }

    fun clearUserData() {
        sharedPreferences.edit()
            .remove(KEY_USER_LOGGED_IN)
            .remove(KEY_USER_EMAIL)
            .apply()
    }
}