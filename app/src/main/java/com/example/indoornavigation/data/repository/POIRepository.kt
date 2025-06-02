package com.example.indoornavigation.data.repository

import android.content.Context
import androidx.room.Room
import com.example.indoornavigation.data.database.AppDatabase
import com.example.indoornavigation.data.models.EnhancedPOI
import com.example.indoornavigation.data.models.POICategory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Repository for accessing and managing Points of Interest data
 */
class POIRepository(context: Context) {
    private val database = Room.databaseBuilder(
        context, 
        AppDatabase::class.java, 
        "poi-database"
    ).build()
    
    private val poiDao = database.poiDao()
    
    /**
     * Get all POIs
     */
    suspend fun getAllPOIs(): List<EnhancedPOI> = withContext(Dispatchers.IO) {
        poiDao.getAll().map { EnhancedPOI.fromEntity(it) }
    }
    
    /**
     * Get POIs by category
     */
    suspend fun getPOIsByCategory(category: POICategory): List<EnhancedPOI> = withContext(Dispatchers.IO) {
        poiDao.getByCategory(category.name).map { EnhancedPOI.fromEntity(it) }
    }
    
    /**
     * Search POIs by name or description
     */
    suspend fun searchPOIs(query: String): List<EnhancedPOI> = withContext(Dispatchers.IO) {
        poiDao.search("%$query%").map { EnhancedPOI.fromEntity(it) }
    }
    
    /**
     * Get POIs for a specific floor
     */
    suspend fun getPOIsByFloor(floorId: Int): List<EnhancedPOI> = withContext(Dispatchers.IO) {
        poiDao.getByFloor(floorId).map { EnhancedPOI.fromEntity(it) }
    }
    
    /**
     * Add a custom POI
     */
    suspend fun addCustomPOI(poi: EnhancedPOI) = withContext(Dispatchers.IO) {
        poiDao.insert(poi.toEntity())
    }
    
    /**
     * Update an existing POI
     */
    suspend fun updatePOI(poi: EnhancedPOI) = withContext(Dispatchers.IO) {
        poiDao.update(poi.toEntity())
    }
    
    /**
     * Delete a POI
     */
    suspend fun deletePOI(poi: EnhancedPOI) = withContext(Dispatchers.IO) {
        poiDao.delete(poi.toEntity())
    }
    
    /**
     * Delete a POI by ID
     */
    suspend fun deletePOIById(poiId: String) = withContext(Dispatchers.IO) {
        poiDao.deleteById(poiId)
    }
    
    /**
     * Delete all user-created POIs
     */
    suspend fun deleteAllUserCreatedPOIs() = withContext(Dispatchers.IO) {
        poiDao.deleteAllUserCreated()
    }
    
    /**
     * Add multiple POIs at once
     */
    suspend fun addPOIs(pois: List<EnhancedPOI>) = withContext(Dispatchers.IO) {
        poiDao.insertAll(pois.map { it.toEntity() })
    }
}