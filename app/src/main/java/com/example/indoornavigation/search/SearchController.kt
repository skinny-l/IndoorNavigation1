package com.example.indoornavigation.search

import android.content.Context
import android.util.Log
import com.example.indoornavigation.data.models.Position
import com.google.gson.Gson

/**
 * Controller for searching locations within the building
 */
class SearchController(private val context: Context) {
    
    private val index = mutableMapOf<String, MutableList<SearchResult>>()
    private val recentSearches = mutableListOf<String>()
    private val gson = Gson()
    
    /**
     * Initialize the search index with building data
     */
    fun initialize(buildingData: BuildingData) {
        Log.d("SearchController", "Initializing search index")
        index.clear()
        buildSearchIndex(buildingData)
    }
    
    /**
     * Build search index from building data
     */
    private fun buildSearchIndex(buildingData: BuildingData) {
        buildingData.floors.forEach { floor ->
            // Index rooms
            floor.rooms.forEach { room ->
                addToIndex(room.id.lowercase(), SearchResult(
                    type = SearchResultType.ROOM,
                    id = room.id,
                    name = room.name,
                    floorId = floor.id,
                    position = room.position,
                    tags = room.tags ?: emptyList()
                ))
                
                // Add room name as keyword
                addToIndex(room.name.lowercase(), SearchResult(
                    type = SearchResultType.ROOM,
                    id = room.id,
                    name = room.name,
                    floorId = floor.id,
                    position = room.position,
                    tags = room.tags ?: emptyList()
                ))
                
                // Add room number as keyword
                room.number?.let { number ->
                    addToIndex(number.lowercase(), SearchResult(
                        type = SearchResultType.ROOM,
                        id = room.id,
                        name = room.name,
                        floorId = floor.id,
                        position = room.position,
                        tags = room.tags ?: emptyList()
                    ))
                }
            }
            
            // Index points of interest
            floor.pointsOfInterest.forEach { poi ->
                addToIndex(poi.id.lowercase(), SearchResult(
                    type = SearchResultType.POI,
                    id = poi.id,
                    name = poi.name,
                    floorId = floor.id,
                    position = poi.position,
                    category = poi.category
                ))
                
                // Add POI name as keyword
                addToIndex(poi.name.lowercase(), SearchResult(
                    type = SearchResultType.POI,
                    id = poi.id,
                    name = poi.name,
                    floorId = floor.id,
                    position = poi.position,
                    category = poi.category
                ))
                
                // Add POI category as keyword
                addToIndex(poi.category.lowercase(), SearchResult(
                    type = SearchResultType.POI,
                    id = poi.id,
                    name = poi.name,
                    floorId = floor.id,
                    position = poi.position,
                    category = poi.category
                ))
            }
        }
        
        Log.d("SearchController", "Search index built with ${index.size} keywords")
    }
    
    /**
     * Add an entry to the search index
     */
    private fun addToIndex(key: String, value: SearchResult) {
        if (!index.containsKey(key)) {
            index[key] = mutableListOf()
        }
        index[key]?.add(value)
    }
    
    /**
     * Search for locations matching the query
     */
    fun search(query: String?): List<SearchResult> {
        if (query.isNullOrBlank()) {
            return emptyList()
        }
        
        val normalizedQuery = query.lowercase().trim()
        
        // Save in recent searches
        addToRecentSearches(normalizedQuery)
        
        val directMatches = index[normalizedQuery] ?: emptyList()
        val partialMatches = mutableListOf<SearchResult>()
        
        // Find partial matches
        index.forEach { (key, values) ->
            if (key.contains(normalizedQuery) && key != normalizedQuery) {
                partialMatches.addAll(values)
            }
        }
        
        // Sort results by relevance
        return sortByRelevance(directMatches + partialMatches, normalizedQuery)
    }
    
    /**
     * Sort search results by relevance to query
     */
    private fun sortByRelevance(results: List<SearchResult>, query: String): List<SearchResult> {
        return results.distinctBy { it.id } // Remove duplicates
            .sortedWith(compareBy(
                // Direct name matches get highest priority
                { !it.name.lowercase().contains(query) },
                // Then by type (rooms before POIs)
                { it.type },
                // Then sort alphabetically
                { it.name }
            ))
    }
    
    /**
     * Add query to recent searches
     */
    private fun addToRecentSearches(query: String) {
        // Remove if already exists
        recentSearches.remove(query)
        
        // Add to beginning
        recentSearches.add(0, query)
        
        // Keep only 10 most recent
        if (recentSearches.size > 10) {
            recentSearches.removeAt(recentSearches.size - 1)
        }
        
        // Save to preferences
        saveRecentSearches()
    }
    
    /**
     * Get recent searches
     */
    fun getRecentSearches(): List<String> {
        loadRecentSearches()
        return recentSearches
    }
    
    /**
     * Save recent searches to preferences
     */
    private fun saveRecentSearches() {
        try {
            val prefs = context.getSharedPreferences("search", Context.MODE_PRIVATE)
            prefs.edit().putString("recent_searches", gson.toJson(recentSearches)).apply()
        } catch (e: Exception) {
            Log.e("SearchController", "Failed to save recent searches: ${e.message}")
        }
    }
    
    /**
     * Load recent searches from preferences
     */
    private fun loadRecentSearches() {
        try {
            val prefs = context.getSharedPreferences("search", Context.MODE_PRIVATE)
            val json = prefs.getString("recent_searches", null)
            if (json != null) {
                val type = com.google.gson.reflect.TypeToken.getParameterized(
                    List::class.java, 
                    String::class.java
                ).type
                val loaded = gson.fromJson<List<String>>(json, type)
                recentSearches.clear()
                recentSearches.addAll(loaded)
            }
        } catch (e: Exception) {
            Log.e("SearchController", "Failed to load recent searches: ${e.message}")
        }
    }
    
    /**
     * Clear recent searches
     */
    fun clearRecentSearches() {
        recentSearches.clear()
        saveRecentSearches()
    }
}

/**
 * Types of search results
 */
enum class SearchResultType {
    ROOM,
    POI
}

/**
 * Data class representing a search result
 */
data class SearchResult(
    val type: SearchResultType,
    val id: String,
    val name: String,
    val floorId: String,
    val position: Position,
    val category: String? = null,
    val tags: List<String> = emptyList()
)

/**
 * Building data model for search indexing
 */
data class BuildingData(
    val id: String,
    val name: String,
    val floors: List<Floor>
)

/**
 * Floor data model for search indexing
 */
data class Floor(
    val id: String,
    val name: String,
    val level: Int,
    val rooms: List<Room>,
    val pointsOfInterest: List<POI>
)

/**
 * Room data model for search indexing
 */
data class Room(
    val id: String,
    val name: String,
    val number: String? = null,
    val position: Position,
    val tags: List<String>? = null
)

/**
 * POI data model for search indexing
 */
data class POI(
    val id: String,
    val name: String,
    val category: String,
    val position: Position
)