package com.example.indoornavigation.data.models

import android.os.Parcelable
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.indoornavigation.R
import kotlinx.parcelize.Parcelize

/**
 * Enhanced Point of Interest (POI) with categories and additional metadata
 */
@Parcelize
data class EnhancedPOI(
    val id: String,
    val name: String,
    val position: Position,
    val floorId: Int,
    val category: POICategory,
    val description: String = "",
    val isUserCreated: Boolean = false,
    val icon: Int = R.drawable.ic_location_marker
) : Parcelable {
    
    /**
     * Get the appropriate icon for this POI's category
     */
    fun getIconForCategory(): Int = when(category) {
        POICategory.RESTROOM -> R.drawable.ic_location_marker // Should be replaced with restroom icon
        POICategory.RESTAURANT -> R.drawable.ic_location_marker // Should be replaced with restaurant icon
        POICategory.SHOP -> R.drawable.ic_location_marker // Should be replaced with shop icon
        POICategory.ELEVATOR -> R.drawable.ic_location_marker // Should be replaced with elevator icon
        POICategory.STAIRS -> R.drawable.ic_location_marker // Should be replaced with stairs icon
        POICategory.EXIT -> R.drawable.ic_location_marker // Should be replaced with exit icon
        POICategory.OFFICE -> R.drawable.ic_location_marker // Should be replaced with office icon
        POICategory.INFORMATION -> R.drawable.ic_location_marker // Should be replaced with info icon
        POICategory.CUSTOM -> icon
    }
    
    /**
     * Convert to a database entity
     */
    fun toEntity(): POIEntity {
        return POIEntity(
            id = id,
            name = name,
            x = position.x,
            y = position.y,
            floorId = floorId,
            category = category.name,
            description = description,
            isUserCreated = isUserCreated,
            icon = icon
        )
    }
    
    companion object {
        /**
         * Convert from a database entity
         */
        fun fromEntity(entity: POIEntity): EnhancedPOI {
            return EnhancedPOI(
                id = entity.id,
                name = entity.name,
                position = Position(entity.x, entity.y, entity.floorId),
                floorId = entity.floorId,
                category = try {
                    POICategory.valueOf(entity.category)
                } catch (e: Exception) {
                    POICategory.CUSTOM
                },
                description = entity.description,
                isUserCreated = entity.isUserCreated,
                icon = entity.icon
            )
        }
    }
}

/**
 * Room Entity for Points of Interest
 */
@Entity(tableName = "points_of_interest")
data class POIEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val x: Double,
    val y: Double,
    val floorId: Int,
    val category: String,
    val description: String,
    val isUserCreated: Boolean,
    val icon: Int
)