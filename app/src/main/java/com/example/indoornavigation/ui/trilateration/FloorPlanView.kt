package com.example.indoornavigation.ui.trilateration

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import com.example.indoornavigation.data.models.ManagedBeacon
import com.example.indoornavigation.data.models.Position
import kotlin.math.max
import kotlin.math.min

/**
 * A custom view for rendering a floor plan with beacons and user position
 */
class FloorPlanView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // Floor plan properties
    var floorPlan: Bitmap? = null
        set(value) {
            field = value
            invalidate()
        }
    
    // Scale and translation for zooming/panning
    private var scaleFactor = 1f
    private var minScale = 0.1f
    private var maxScale = 5f
    private var translationX = 0f
    private var translationY = 0f
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var dragging = false
    
    // Map coordinates to screen coordinates ratio
    var mapToScreenRatio = 10f // 1 meter = 10 pixels by default
    
    // User position and accuracy
    var userPosition: Position? = null
        set(value) {
            field = value
            invalidate()
        }
    
    var positionAccuracy: Double = 0.0
        set(value) {
            field = value
            invalidate()
        }
    
    // Beacons to display
    var beacons: List<ManagedBeacon> = emptyList()
        set(value) {
            field = value
            invalidate()
        }
    
    // Paint objects
    private val userPositionPaint = Paint().apply {
        color = Color.RED
        style = Paint.Style.FILL
        isAntiAlias = true
    }
    
    private val accuracyCirclePaint = Paint().apply {
        color = Color.parseColor("#4D3F51B5") // Semi-transparent blue
        style = Paint.Style.FILL
        isAntiAlias = true
    }
    
    private val beaconPaint = Paint().apply {
        color = Color.BLUE
        style = Paint.Style.FILL
        isAntiAlias = true
    }
    
    private val beaconRangePaint = Paint().apply {
        color = Color.parseColor("#1A0000FF") // Very transparent blue
        style = Paint.Style.FILL
        isAntiAlias = true
    }
    
    private val textPaint = Paint().apply {
        color = Color.BLACK
        textSize = 30f
        isAntiAlias = true
    }
    
    // Gesture detectors for zoom and pan
    private val scaleDetector = ScaleGestureDetector(context, ScaleListener())
    
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        // Save canvas state
        canvas.save()
        
        // Apply zoom and translation
        canvas.scale(scaleFactor, scaleFactor)
        canvas.translate(translationX / scaleFactor, translationY / scaleFactor)
        
        // Draw floor plan if available
        floorPlan?.let { bitmap ->
            canvas.drawBitmap(bitmap, 0f, 0f, null)
        }
        
        // Draw beacons
        beacons.forEach { beacon ->
            val x = (beacon.x * mapToScreenRatio).toFloat()
            val y = (beacon.y * mapToScreenRatio).toFloat()
            
            // Draw beacon range circle if there's distance data
            if (beacon.lastDistance > 0) {
                val rangeRadius = (beacon.lastDistance * mapToScreenRatio).toFloat()
                canvas.drawCircle(x, y, rangeRadius, beaconRangePaint)
            }
            
            // Draw beacon marker
            canvas.drawCircle(x, y, 10f, beaconPaint)
            
            // Draw beacon label
            val label = beacon.name.ifEmpty { beacon.uuid.takeLast(8) }
            canvas.drawText(label, x + 15f, y - 15f, textPaint)
        }
        
        // Draw user position if available
        userPosition?.let { position ->
            val x = (position.x * mapToScreenRatio).toFloat()
            val y = (position.y * mapToScreenRatio).toFloat()
            
            // Draw accuracy circle
            val accuracyRadius = (positionAccuracy * mapToScreenRatio).toFloat()
            canvas.drawCircle(x, y, accuracyRadius, accuracyCirclePaint)
            
            // Draw user position marker
            canvas.drawCircle(x, y, 15f, userPositionPaint)
        }
        
        // Restore canvas state
        canvas.restore()
    }
    
    override fun onTouchEvent(event: MotionEvent): Boolean {
        // Let the scale detector inspect the event first
        scaleDetector.onTouchEvent(event)
        
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastTouchX = event.x
                lastTouchY = event.y
                dragging = true
            }
            MotionEvent.ACTION_MOVE -> {
                if (dragging && !scaleDetector.isInProgress) {
                    val dx = event.x - lastTouchX
                    val dy = event.y - lastTouchY
                    
                    translationX += dx
                    translationY += dy
                    
                    lastTouchX = event.x
                    lastTouchY = event.y
                    
                    invalidate()
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                dragging = false
            }
        }
        
        return true
    }
    
    /**
     * Reset zoom and translation to default
     */
    fun resetZoomAndPan() {
        scaleFactor = 1f
        translationX = 0f
        translationY = 0f
        invalidate()
    }
    
    /**
     * Center view on user position
     */
    fun centerOnUser() {
        userPosition?.let { position ->
            val x = (position.x * mapToScreenRatio).toFloat()
            val y = (position.y * mapToScreenRatio).toFloat()
            
            // Center the user position on the screen
            translationX = width / 2f - x * scaleFactor
            translationY = height / 2f - y * scaleFactor
            
            invalidate()
        }
    }
    
    /**
     * Convert screen coordinates to map coordinates
     */
    fun screenToMapCoordinates(screenX: Float, screenY: Float): PointF {
        val mapX = (screenX / scaleFactor - translationX / scaleFactor) / mapToScreenRatio
        val mapY = (screenY / scaleFactor - translationY / scaleFactor) / mapToScreenRatio
        return PointF(mapX, mapY)
    }
    
    /**
     * Scale gesture listener for handling pinch-to-zoom
     */
    private inner class ScaleListener : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            scaleFactor *= detector.scaleFactor
            
            // Constrain scale factor
            scaleFactor = max(minScale, min(scaleFactor, maxScale))
            
            invalidate()
            return true
        }
    }
}