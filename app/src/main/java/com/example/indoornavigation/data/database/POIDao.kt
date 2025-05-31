package com.example.indoornavigation.data.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.indoornavigation.data.models.POIEntity

/**
 * Data Access Object for Points of Interest
 */
@Dao
interface POIDao {
    
    @Query("SELECT * FROM points_of_interest")
    suspend fun getAll(): List<POIEntity>
    
    @Query("SELECT * FROM points_of_interest WHERE id = :poiId")
    suspend fun getById(poiId: String): POIEntity?
    
    @Query("SELECT * FROM points_of_interest WHERE category = :category")
    suspend fun getByCategory(category: String): List<POIEntity>
    
    @Query("SELECT * FROM points_of_interest WHERE name LIKE :query OR description LIKE :query")
    suspend fun search(query: String): List<POIEntity>
    
    @Query("SELECT * FROM points_of_interest WHERE floorId = :floorId")
    suspend fun getByFloor(floorId: Int): List<POIEntity>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(poi: POIEntity)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(pois: List<POIEntity>)
    
    @Update
    suspend fun update(poi: POIEntity)
    
    @Delete
    suspend fun delete(poi: POIEntity)
    
    @Query("DELETE FROM points_of_interest WHERE id = :poiId")
    suspend fun deleteById(poiId: String)
    
    @Query("DELETE FROM points_of_interest WHERE isUserCreated = 1")
    suspend fun deleteAllUserCreated()

    @Query("DELETE FROM points_of_interest")
    suspend fun deleteAll()
}
