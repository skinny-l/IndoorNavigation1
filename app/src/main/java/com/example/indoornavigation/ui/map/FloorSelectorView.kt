package com.example.indoornavigation.ui.map

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.example.indoornavigation.R

/**
 * View for selecting different floors in a building
 */
class FloorSelectorView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {
    
    private var floorButtons = mutableListOf<TextView>()
    private var selectedFloor = 0
    private var onFloorSelectedListener: ((Int) -> Unit)? = null
    
    init {
        orientation = VERTICAL
        
        // Set background
        setBackgroundColor(ContextCompat.getColor(context, R.color.colorPrimary))
        
        // Set padding
        val padding = 16 // 16dp padding
        setPadding(padding, padding, padding, padding)
        
        // Set elevation
        elevation = 8f // 8dp elevation
    }
    
    /**
     * Set up floor buttons for a building with given floors
     * @param floors List of floor IDs (typically 0 for ground floor, negative for basement, positive for upper floors)
     */
    fun setupFloors(floors: List<Int>) {
        // Clear existing buttons
        removeAllViews()
        floorButtons.clear()
        
        // Sort floors with highest floor at the top
        val sortedFloors = floors.sortedDescending()
        
        // Create a button for each floor
        for (floorId in sortedFloors) {
            val floorButton = createFloorButton(floorId)
            addView(floorButton)
            floorButtons.add(floorButton)
            
            // Set up click listener
            floorButton.setOnClickListener {
                setSelectedFloor(floorId)
                onFloorSelectedListener?.invoke(floorId)
            }
        }
        
        // Select default floor
        if (floors.isNotEmpty()) {
            setSelectedFloor(floors.first())
        }
    }
    
    /**
     * Create a button for a specific floor
     */
    private fun createFloorButton(floorId: Int): TextView {
        val button = TextView(context)
        val params = LayoutParams(
            LayoutParams.MATCH_PARENT,
            LayoutParams.WRAP_CONTENT
        )
        
        // Add vertical margin
        val margin = 4 // 4dp margin
        params.topMargin = margin
        params.bottomMargin = margin
        button.layoutParams = params
        
        // Set simple background
        button.setBackgroundColor(ContextCompat.getColor(context, android.R.color.white))
        button.setTextColor(ContextCompat.getColor(context, android.R.color.black))
        button.textAlignment = TextView.TEXT_ALIGNMENT_CENTER
        
        // Simple padding
        val paddingHorizontal = 24 // 24dp
        val paddingVertical = 16 // 16dp
        button.setPadding(paddingHorizontal, paddingVertical, paddingHorizontal, paddingVertical)
        
        // Set text based on floor ID
        button.text = getFloorText(floorId)
        
        return button
    }
    
    /**
     * Get display text for a floor ID
     */
    private fun getFloorText(floorId: Int): String {
        return when {
            floorId == 0 -> "G"
            floorId > 0 -> floorId.toString()
            else -> "B${-floorId}" // Basement floors
        }
    }
    
    /**
     * Set the selected floor
     */
    fun setSelectedFloor(floorId: Int) {
        selectedFloor = floorId
        
        // Update button states
        for (button in floorButtons) {
            val buttonFloorId = getFloorIdFromText(button.text.toString())
            if (buttonFloorId == floorId) {
                button.isSelected = true
                button.setTextColor(ContextCompat.getColor(context, R.color.colorPrimary))
            } else {
                button.isSelected = false
                button.setTextColor(ContextCompat.getColor(context, android.R.color.black))
            }
        }
    }
    
    /**
     * Get floor ID from button text
     */
    private fun getFloorIdFromText(text: String): Int {
        return when {
            text == "G" -> 0
            text.startsWith("B") -> -(text.substring(1).toIntOrNull() ?: 0)
            else -> text.toIntOrNull() ?: 0 // Default to ground floor if parsing fails
        }
    }
    
    /**
     * Set listener for floor selection events
     */
    fun setOnFloorSelectedListener(listener: (Int) -> Unit) {
        onFloorSelectedListener = listener
    }
    
    /**
     * Get the currently selected floor
     */
    fun getSelectedFloor(): Int {
        return selectedFloor
    }
}