package com.example.indoornavigation.ui.map

import android.content.Context
import android.graphics.Canvas
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

/**
 * Performance optimized view for displaying floor plans
 * Implements view recycling and hardware acceleration for better performance
 */
class OptimizedFloorPlanView @JvmOverloads constructor(
    context: Context, 
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    
    // Use hardware acceleration
    init {
        setLayerType(LAYER_TYPE_HARDWARE, null)
    }
    
    // Track current floor
    private var currentFloor = 0
    
    // Implement view recycling for large floor plans
    private val visibleRegionPath = Path()
    private val visibleElements = mutableListOf<FloorElement>()
    
    // Represents any element to be drawn on the floor plan
    interface FloorElement {
        val path: Path
        fun draw(canvas: Canvas)
        val floor: Int
    }
    
    // Track all floor elements (markers, paths, etc.)
    private val floorElements = mutableListOf<FloorElement>()
    
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        // Only draw elements in visible region
        updateVisibleElements()
        
        // Draw visible elements
        for (element in visibleElements) {
            if (element.floor == currentFloor) {
                element.draw(canvas)
            }
        }
    }
    
    /**
     * Update which elements are visible in the current view
     */
    private fun updateVisibleElements() {
        visibleElements.clear()
        val visibleRect = Rect()
        getLocalVisibleRect(visibleRect)
        
        visibleRegionPath.reset()
        visibleRegionPath.addRect(
            visibleRect.left.toFloat(),
            visibleRect.top.toFloat(),
            visibleRect.right.toFloat(),
            visibleRect.bottom.toFloat(),
            Path.Direction.CW
        )
        
        // Filter floor elements to only include visible ones
        visibleElements.addAll(
            floorElements.filter { element ->
                // Only include elements on the current floor
                if (element.floor != currentFloor) {
                    return@filter false
                }
                
                // Check if the element is visible
                val bounds = RectF()
                element.path.computeBounds(bounds, true)
                return@filter visibleRect.intersects(
                    bounds.left.toInt(),
                    bounds.top.toInt(),
                    bounds.right.toInt(),
                    bounds.bottom.toInt()
                )
            }
        )
    }
    
    /**
     * Add a floor element to be drawn
     */
    fun addFloorElement(element: FloorElement) {
        floorElements.add(element)
        invalidate()
    }
    
    /**
     * Remove a floor element
     */
    fun removeFloorElement(element: FloorElement) {
        floorElements.remove(element)
        invalidate()
    }
    
    /**
     * Clear all floor elements
     */
    fun clearFloorElements() {
        floorElements.clear()
        invalidate()
    }
    
    /**
     * Set the current floor
     */
    fun setCurrentFloor(floor: Int) {
        currentFloor = floor
        invalidate()
    }
    
    /**
     * Get the current floor
     */
    fun getCurrentFloor(): Int {
        return currentFloor
    }
}