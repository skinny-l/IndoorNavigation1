package com.example.indoornavigation.bookmarks

import android.content.Context
import android.util.Log
import com.example.indoornavigation.data.models.Position
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Manages location bookmarks for quick access to frequently visited places
 */
class BookmarkManager(private val context: Context) {
    
    private val _bookmarks = mutableListOf<Bookmark>()
    val bookmarks: List<Bookmark> get() = _bookmarks.toList()
    
    private val gson = Gson()
    private val prefsName = "navigation"
    private val bookmarksKey = "bookmarks"
    
    init {
        loadBookmarks()
    }
    
    /**
     * Load bookmarks from SharedPreferences
     */
    private fun loadBookmarks() {
        try {
            val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
            val json = prefs.getString(bookmarksKey, null)
            
            if (json != null) {
                val type = object : TypeToken<List<Bookmark>>() {}.type
                val loadedBookmarks = gson.fromJson<List<Bookmark>>(json, type)
                
                _bookmarks.clear()
                _bookmarks.addAll(loadedBookmarks)
                
                Log.d("BookmarkManager", "Loaded ${_bookmarks.size} bookmarks")
            }
        } catch (e: Exception) {
            Log.e("BookmarkManager", "Failed to load bookmarks: ${e.message}")
        }
    }
    
    /**
     * Save bookmarks to SharedPreferences
     */
    private fun saveBookmarks() {
        try {
            val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
            val json = gson.toJson(_bookmarks)
            
            prefs.edit().putString(bookmarksKey, json).apply()
            
            Log.d("BookmarkManager", "Saved ${_bookmarks.size} bookmarks")
        } catch (e: Exception) {
            Log.e("BookmarkManager", "Failed to save bookmarks: ${e.message}")
        }
    }
    
    /**
     * Add a bookmark
     */
    fun addBookmark(location: Location): Bookmark {
        // Check if already exists by comparing positions
        val existingIndex = _bookmarks.indexOfFirst { 
            it.floor == location.floor && 
            it.position.x == location.position.x && 
            it.position.y == location.position.y 
        }
        
        val now = getCurrentTimestamp()
        
        val bookmark = if (existingIndex >= 0) {
            // Update existing bookmark
            val existing = _bookmarks[existingIndex]
            val updated = existing.copy(
                name = location.name,
                updatedAt = now
            )
            
            _bookmarks[existingIndex] = updated
            updated
        } else {
            // Create new bookmark
            val newBookmark = Bookmark(
                id = "bookmark_${System.currentTimeMillis()}",
                name = location.name,
                floor = location.floor,
                position = location.position,
                category = location.category,
                icon = location.icon,
                createdAt = now,
                updatedAt = now
            )
            
            _bookmarks.add(newBookmark)
            newBookmark
        }
        
        saveBookmarks()
        return bookmark
    }
    
    /**
     * Remove a bookmark by ID
     */
    fun removeBookmark(id: String): Boolean {
        val initialSize = _bookmarks.size
        _bookmarks.removeAll { it.id == id }
        
        val removed = initialSize != _bookmarks.size
        if (removed) {
            saveBookmarks()
        }
        
        return removed
    }
    
    /**
     * Get bookmarks by category
     */
    fun getBookmarksByCategory(category: String): List<Bookmark> {
        return bookmarks.filter { it.category == category }
    }
    
    /**
     * Get bookmarks near current position
     */
    fun getNearbyBookmarks(
        currentPosition: Position?,
        maxDistance: Double = 50.0,
        sameFloorOnly: Boolean = true
    ): List<BookmarkWithDistance> {
        if (currentPosition == null) return emptyList()
        
        return bookmarks
            .filter { !sameFloorOnly || it.floor == currentPosition.floor }
            .map { bookmark ->
                BookmarkWithDistance(
                    bookmark = bookmark,
                    distance = calculateDistance(bookmark.position, currentPosition)
                )
            }
            .filter { it.distance <= maxDistance }
            .sortedBy { it.distance }
    }
    
    /**
     * Calculate Euclidean distance between two positions
     */
    private fun calculateDistance(pos1: Position, pos2: Position): Double {
        val floorFactor = if (pos1.floor == pos2.floor) 1.0 else 10.0 // Penalize different floors
        
        return sqrt(
            (pos1.x - pos2.x).pow(2) + 
            (pos1.y - pos2.y).pow(2)
        ) * floorFactor
    }
    
    /**
     * Get current timestamp in ISO 8601 format
     */
    private fun getCurrentTimestamp(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        return sdf.format(Date())
    }
}

/**
 * Data class representing a bookmarked location
 */
data class Bookmark(
    val id: String,
    val name: String,
    val floor: Int,
    val position: Position,
    val category: String? = null,
    val icon: String? = null,
    val createdAt: String,
    val updatedAt: String
)

/**
 * Data class representing a location to bookmark
 */
data class Location(
    val name: String,
    val floor: Int,
    val position: Position,
    val category: String? = null,
    val icon: String? = null
)

/**
 * Data class representing a bookmark with distance to current position
 */
data class BookmarkWithDistance(
    val bookmark: Bookmark,
    val distance: Double
)