package com.example.indoornavigation.ui.debug

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import com.example.indoornavigation.data.models.Position
import kotlin.math.min

/**
 * Custom view for visualizing indoor positioning in debug mode
 * Shows user position, beacons, signal ranges, and movement history
 */
class FloorPlanView @JvmOverloads constructor(
    context: Context, 
    attrs: AttributeSet? = null, 
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // User position
    private var userPosition: Position? = null
    private var accuracy: Double = 0.0
    
    // Position history for movement trail
    private val positionHistory = mutableListOf<Position>()
    
    // Connected beacons
    private var beacons: List<BeaconData> = emptyList()
    
    // Paint objects
    private val userPaint = Paint().apply {
        color = Color.RED
        style = Paint.Style.FILL
        isAntiAlias = true
    }
    
    private val userAccuracyPaint = Paint().apply {
        color = Color.RED
        alpha = 50 // 20% opacity
        style = Paint.Style.STROKE
        strokeWidth = 2f
        isAntiAlias = true
    }
    
    private val beaconPaint = Paint().apply {
        color = Color.BLUE
        style = Paint.Style.FILL
        isAntiAlias = true 
    }
    
    private val rangePaint = Paint().apply {
        color = Color.BLUE
        alpha = 50 // 20% opacity
        style = Paint.Style.STROKE
        strokeWidth = 1f
        isAntiAlias = true
    }
    
    private val pathPaint = Paint().apply {
        color = Color.GREEN
        style = Paint.Style.STROKE
        strokeWidth = 4f
        isAntiAlias = true
    }
    
    // Floor plan dimensions in meters
    private var floorPlanWidthMeters = 20f
    private var floorPlanHeightMeters = 20f
    
    // Track screen space
    private var pixelsPerMeter = 0f
    
    /**
     * Update the user's current position
     */
    fun updateUserPosition(position: Position?, accuracy: Double = 0.0) {
        position?.let {
            userPosition = it
            this.accuracy = accuracy
            
            // Add position to history, limiting to last 20 points
            positionHistory.add(it)
            if (positionHistory.size > 20) {
                positionHistory.removeAt(0)
            }
        }
        invalidate()
    }
    
    /**
     * Update list of detected beacons
     */
    fun updateBeacons(beacons: List<BeaconData>) {
        this.beacons = beacons
        invalidate()
    }
    
    /**
     * Set floor plan dimensions in meters
     */
    fun setFloorPlanDimensions(widthMeters: Float, heightMeters: Float) {
        floorPlanWidthMeters = widthMeters
        floorPlanHeightMeters = heightMeters
        invalidate()
    }
    
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        
        // Calculate pixels per meter
        pixelsPerMeter = min(
            w / floorPlanWidthMeters,
            h / floorPlanHeightMeters
        )
    }
    
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        val width = width.toFloat()
        val height = height.toFloat()
        
        // Calculate scaling factors to fit the view
        val scaleX = width / floorPlanWidthMeters
        val scaleY = height / floorPlanHeightMeters
        val scale = min(scaleX, scaleY)
        
        // Center the floor plan in the view
        val offsetX = (width - (floorPlanWidthMeters * scale)) / 2
        val offsetY = (height - (floorPlanHeightMeters * scale)) / 2
        
        // Draw floor grid
        drawGrid(canvas, offsetX, offsetY, scale)
        
        // Draw beacons and their ranges
        beacons.forEach { beacon ->
            val x = offsetX + (beacon.position.x * scale).toFloat()
            val y = offsetY + (beacon.position.y * scale).toFloat()
            
            // Draw range circle
            canvas.drawCircle(
                x, y, (beacon.distance * scale).toFloat(), rangePaint
            )
            
            // Draw beacon dot
            canvas.drawCircle(
                x, y, 10f, beaconPaint
            )
        }
        
        // Draw position history path
        if (positionHistory.size > 1) {
            val path = Path()
            val first = positionHistory.first()
            path.moveTo(
                offsetX + (first.x * scale).toFloat(),
                offsetY + (first.y * scale).toFloat()
            )
            
            for (i in 1 until positionHistory.size) {
                val pos = positionHistory[i]
                path.lineTo(
                    offsetX + (pos.x * scale).toFloat(),
                    offsetY + (pos.y * scale).toFloat()
                )
            }
            canvas.drawPath(path, pathPaint)
        }
        
        // Draw user position
        userPosition?.let { pos ->
            canvas.drawCircle(
                offsetX + (pos.x * scale).toFloat(),
                offsetY + (pos.y * scale).toFloat(),
                15f,
                userPaint
            )
            
            // Draw accuracy circle if available
            if (accuracy > 0) {
                canvas.drawCircle(
                    offsetX + (pos.x * scale).toFloat(),
                    offsetY + (pos.y * scale).toFloat(),
                    (accuracy * scale).toFloat(),
                    userAccuracyPaint
                )
            }
        }
    }
    
    /**
     * Draw grid lines for the floor plan
     */
    private fun drawGrid(canvas: Canvas, offsetX: Float, offsetY: Float, scale: Float) {
        val gridPaint = Paint().apply {
            color = Color.LTGRAY
            strokeWidth = 1f
            style = Paint.Style.STROKE
        }
        
        // Draw vertical grid lines every 5 meters
        for (x in 0..floorPlanWidthMeters.toInt() step 5) {
            val xPos = offsetX + (x * scale)
            canvas.drawLine(
                xPos, offsetY,
                xPos, offsetY + (floorPlanHeightMeters * scale),
                gridPaint
            )
        }
        
        // Draw horizontal grid lines every 5 meters
        for (y in 0..floorPlanHeightMeters.toInt() step 5) {
            val yPos = offsetY + (y * scale)
            canvas.drawLine(
                offsetX, yPos,
                offsetX + (floorPlanWidthMeters * scale), yPos,
                gridPaint
            )
        }
    }
    
    /**
     * Clear position history
     */
    fun clearPositionHistory() {
        positionHistory.clear()
        invalidate()
    }
    
    /**
     * Data class for beacon information
     */
    data class BeaconData(
        val position: Position,
        val distance: Double,
        val rssi: Int
    )
}