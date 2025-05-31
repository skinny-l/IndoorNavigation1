package com.example.indoornavigation.ui.debug

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import com.example.indoornavigation.data.models.Position
import kotlin.math.max
import kotlin.math.min

/**
 * Enhanced floor plan visualization for debug purposes
 * Provides multiple visualization modes and interactive features
 */
class EnhancedFloorPlanView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    companion object {
        const val VISUALIZATION_POSITION_ACCURACY = 0
        const val VISUALIZATION_MOVEMENT_HISTORY = 1
        
        private const val MAX_POSITION_HISTORY = 30
        private const val DEFAULT_BEACON_RADIUS = 10f
        private const val DEFAULT_USER_RADIUS = 15f
    }
    
    // User position
    private var userPosition: Position? = null
    private var accuracy: Float = 0.0f
    
    // Position history for movement trail
    private val positionHistory = mutableListOf<Position>()
    
    // Connected beacons
    private var beacons: List<BeaconData> = emptyList()
    
    // Current visualization mode
    private var visualizationMode = VISUALIZATION_POSITION_ACCURACY
    
    // Display flags
    private var showDistanceRings = false
    private var showAccuracy = false
    private var positioningMethod: String = "Unknown"
    
    // Zoom and pan variables
    private var scaleFactor = 1f
    private var translateX = 0f
    private var translateY = 0f
    private val scaleDetector = ScaleGestureDetector(context, ScaleListener())
    private val gestureDetector = GestureDetector(context, GestureListener())
    
    // Paint objects
    private val userPaint = Paint().apply {
        color = Color.parseColor("#E57373") // Red for user position
        style = Paint.Style.FILL
        isAntiAlias = true
        setShadowLayer(5f, 0f, 0f, Color.GRAY) // Add shadow for better visibility
    }
    
    private val userAccuracyPaint = Paint().apply {
        color = Color.parseColor("#E57373") // Red matching user
        alpha = 40  // More transparent
        style = Paint.Style.STROKE
        strokeWidth = 2f
        isAntiAlias = true
    }
    
    // Beacon colors - colorful beacons as in the example
    private val beaconColors = listOf(
        Color.parseColor("#9C27B0"), // Purple
        Color.parseColor("#F9A825"), // Gold
        Color.parseColor("#E57373"), // Red
        Color.parseColor("#42A5F5"), // Blue
        Color.parseColor("#66BB6A")  // Green
    )
    
    private val rangePaint = Paint().apply {
        color = Color.GRAY
        alpha = 40  // Very subtle
        style = Paint.Style.STROKE
        strokeWidth = 1f
        isAntiAlias = true
    }
    
    private val beaconTextPaint = Paint().apply {
        color = Color.WHITE
        textSize = 16f
        textAlign = Paint.Align.CENTER
        isAntiAlias = true
        isFakeBoldText = true
    }
    
    private val beaconLabelPaint = Paint().apply {
        color = Color.DKGRAY
        textSize = 14f
        textAlign = Paint.Align.CENTER
        isAntiAlias = true
    }
    
    private val pathPaint = Paint().apply {
        color = Color.parseColor("#E57373") // Red path
        alpha = 150
        style = Paint.Style.STROKE
        strokeWidth = 3f
        isAntiAlias = true
    }
    
    private val gridPaint = Paint().apply {
        color = Color.parseColor("#E0E0E0") // Very light gray
        strokeWidth = 0.5f
        style = Paint.Style.STROKE
        alpha = 80 // More transparent
    }
    
    private val textPaint = Paint().apply {
        color = Color.DKGRAY
        textSize = 16f
        isAntiAlias = true
    }
    
    // Simplified distance text paint
    private val distanceTextPaint = Paint().apply {
        color = Color.DKGRAY
        textSize = 12f
        textAlign = Paint.Align.CENTER
        isAntiAlias = true
    }
    
    // Method info paint
    private val methodInfoPaint = Paint().apply {
        color = Color.BLACK
        textSize = 24f
        isAntiAlias = true
        isFakeBoldText = true
        textAlign = Paint.Align.LEFT
    }
    
    // Method background paint
    private val methodBgPaint = Paint().apply {
        color = Color.WHITE
        alpha = 180
        style = Paint.Style.FILL
    }
    
    // Method colors for different positioning methods
    private val methodColors = mapOf(
        "Trilateration" to Color.parseColor("#4CAF50"), // Green
        "Weighted Centroid" to Color.parseColor("#FF9800"), // Orange
        "Kalman Filter" to Color.parseColor("#2196F3"), // Blue
        "Fingerprinting" to Color.parseColor("#9C27B0"), // Purple
        "Sensor Fusion" to Color.parseColor("#F44336"), // Red
        "Fallback" to Color.parseColor("#795548") // Brown
    )
    
    // Floor plan dimensions in meters
    private var floorPlanWidthMeters = 50f
    private var floorPlanHeightMeters = 50f
    
    /**
     * Set the visualization mode
     */
    fun setVisualizationMode(mode: Int) {
        if (mode in 0..1) {
            visualizationMode = mode
            invalidate()
        }
    }
    
    /**
     * Update the user's current position
     */
    fun updateUserPosition(position: Position?, accuracy: Float = 0.0f, method: String = "Unknown") {
        position?.let {
            userPosition = it
            this.accuracy = accuracy
            this.positioningMethod = method
            
            // Add position to history, limiting to MAX_POSITION_HISTORY points
            positionHistory.add(it)
            if (positionHistory.size > MAX_POSITION_HISTORY) {
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
     * Clear position history
     */
    fun clearPositionHistory() {
        positionHistory.clear()
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
    
    /**
     * Set whether to show distance rings around beacons
     */
    fun setShowDistanceRings(show: Boolean) {
        showDistanceRings = show
        invalidate()
    }
    
    /**
     * Set whether to show accuracy circle around user position
     */
    fun setShowAccuracy(show: Boolean) {
        showAccuracy = show
        invalidate()
    }
    
    /**
     * Set the positioning method being used
     */
    fun setPositioningMethod(method: String) {
        positioningMethod = method
        invalidate()
    }
    
    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        gestureDetector.onTouchEvent(event)
        return true
    }
    
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        val width = width.toFloat()
        val height = height.toFloat()
        
        // Apply zoom and pan
        canvas.save()
        canvas.translate(translateX, translateY)
        canvas.scale(scaleFactor, scaleFactor, width / 2f, height / 2f)
        
        // Calculate scaling factors to fit the view
        val scaleX = width / floorPlanWidthMeters
        val scaleY = height / floorPlanHeightMeters
        val scale = min(scaleX, scaleY)
        
        // Center the floor plan in the view
        val offsetX = (width - (floorPlanWidthMeters * scale)) / 2
        val offsetY = (height - (floorPlanHeightMeters * scale)) / 2
        
        // Draw floor grid
        drawGrid(canvas, offsetX, offsetY, scale)
        
        // Draw visualization based on selected mode
        when (visualizationMode) {
            VISUALIZATION_POSITION_ACCURACY -> drawPositionAccuracyVisualization(canvas, offsetX, offsetY, scale)
            VISUALIZATION_MOVEMENT_HISTORY -> drawMovementHistoryVisualization(canvas, offsetX, offsetY, scale)
        }
        
        canvas.restore()
    }
    
    /**
     * Draw visualization showing beacon signal strengths
     */
    private fun drawSignalStrengthVisualization(canvas: Canvas, offsetX: Float, offsetY: Float, scale: Float) {
        // Draw beacons with colorful circles like in the example
        beacons.forEachIndexed { index, beacon ->
            val x = offsetX + (beacon.position.x * scale).toFloat()
            val y = offsetY + (beacon.position.y * scale).toFloat()
            
            // Get color based on index
            val beaconColor = beaconColors[index % beaconColors.size]
            val beaconPaint = Paint().apply {
                color = beaconColor
                style = Paint.Style.FILL
                isAntiAlias = true
            }
            
            // Draw range circle if enabled
            if (showDistanceRings) {
                rangePaint.alpha = 40
                rangePaint.color = beaconColor
                canvas.drawCircle(
                    x, y, (beacon.distance * scale).toFloat(), rangePaint
                )
            }
            
            // Draw beacon circle (25px radius)
            val beaconRadius = 25f
            canvas.drawCircle(x, y, beaconRadius, beaconPaint)
            
            // Extract beacon ID from position index
            val beaconName = "BN" + (index + 1).toString().padStart(3, '0')
            
            // Draw the beacon icon in white text
            canvas.drawText("⚑", x, y + 7f, beaconTextPaint)
            
            // Draw beacon name below circle
            canvas.drawText(beaconName, x, y + beaconRadius + 15f, beaconLabelPaint)
        }
        
        // Draw user position
        userPosition?.let { pos ->
            val x = offsetX + (pos.x * scale).toFloat()
            val y = offsetY + (pos.y * scale).toFloat()
            
            // Draw accuracy circle if enabled
            if (showAccuracy) {
                canvas.drawCircle(x, y, (accuracy * scale).toFloat(), userAccuracyPaint)
            }
            
            // Draw user position as a red circle with coordinates
            val userPinPaint = Paint().apply {
                color = Color.parseColor("#E57373") // Red
                style = Paint.Style.FILL
                isAntiAlias = true
            }
            
            // Draw a simple location pin (circle with dot in center)
            canvas.drawCircle(x, y, 18f, Paint().apply { 
                color = Color.WHITE
                style = Paint.Style.FILL
                isAntiAlias = true
            })
            
            canvas.drawCircle(x, y, 15f, userPinPaint)
            
            // Draw coordinates in pin
            val coordText = "(${pos.x.toInt()}, ${pos.y.toInt()})"
            canvas.drawText(coordText, x, y + 5f, Paint().apply {
                color = Color.WHITE
                textSize = 10f
                textAlign = Paint.Align.CENTER
                isAntiAlias = true
            })
        }
    }
    
    /**
     * Draw visualization focusing on position accuracy
     */
    private fun drawPositionAccuracyVisualization(canvas: Canvas, offsetX: Float, offsetY: Float, scale: Float) {
        // Draw beacons
        beacons.forEach { beacon ->
            val x = offsetX + (beacon.position.x * scale).toFloat()
            val y = offsetY + (beacon.position.y * scale).toFloat()
            
            canvas.drawCircle(x, y, DEFAULT_BEACON_RADIUS, Paint().apply {
                color = Color.parseColor("#9C27B0") // Purple
                style = Paint.Style.FILL
                isAntiAlias = true
            })
            
            // Draw beacon range circle with distance
            val radius = beacon.distance * scale
            canvas.drawCircle(x, y, radius.toFloat(), rangePaint)
            
            // Draw distance text
            canvas.drawText(
                String.format("%.1fm", beacon.distance),
                x, y + radius.toFloat() + 20f,
                distanceTextPaint
            )
            
            // Draw line from beacon to user position if available
            userPosition?.let { pos ->
                val userX = offsetX + (pos.x * scale).toFloat()
                val userY = offsetY + (pos.y * scale).toFloat()
                
                // Draw line from beacon to user
                val linePaint = Paint().apply {
                    color = Color.GRAY
                    strokeWidth = 1f
                    style = Paint.Style.STROKE
                }
                canvas.drawLine(x, y, userX, userY, linePaint)
                
                // Calculate actual distance between beacon and user
                val dx = pos.x - beacon.position.x
                val dy = pos.y - beacon.position.y
                val actualDistance = Math.sqrt((dx * dx + dy * dy).toDouble())
                
                // Draw midpoint distance text
                val midX = (x + userX) / 2f
                val midY = (y + userY) / 2f
                
                // Draw text background
                val distText = String.format("%.1fm", actualDistance)
                val textBounds = android.graphics.Rect()
                distanceTextPaint.getTextBounds(distText, 0, distText.length, textBounds)
                val textBgPaint = Paint().apply {
                    color = Color.WHITE
                    alpha = 180
                }
                canvas.drawRect(
                    midX - textBounds.width() / 2f - 5f,
                    midY - textBounds.height() / 2f - 5f,
                    midX + textBounds.width() / 2f + 5f,
                    midY + textBounds.height() / 2f + 5f,
                    textBgPaint
                )
                
                // Draw text
                canvas.drawText(distText, midX, midY + 5f, distanceTextPaint)
            }
        }
        
        // Draw user position with accuracy circle
        userPosition?.let { pos ->
            val x = offsetX + (pos.x * scale).toFloat()
            val y = offsetY + (pos.y * scale).toFloat()
            
            // Draw accuracy circle if enabled
            if (showAccuracy) {
                canvas.drawCircle(x, y, (accuracy * scale).toFloat(), userAccuracyPaint)
            }
            
            // Draw location pin for user position (like in the example)
            val pinPaint = Paint().apply {
                color = Color.parseColor("#E57373") // Red
                style = Paint.Style.FILL
                isAntiAlias = true
            }
            
            // Draw a simple location pin (circle with dot in center)
            canvas.drawCircle(x, y, 18f, Paint().apply { 
                color = Color.WHITE
                style = Paint.Style.FILL
                isAntiAlias = true
            })
            
            canvas.drawCircle(x, y, 15f, pinPaint)
            
            // Draw coordinates in pin
            val coordText = "(${pos.x.toInt()}, ${pos.y.toInt()})"
            canvas.drawText(coordText, x, y + 5f, Paint().apply {
                color = Color.WHITE
                textSize = 10f
                textAlign = Paint.Align.CENTER
                isAntiAlias = true
            })
            
            // Draw positioning method info
            val methodColor = methodColors[positioningMethod] ?: Color.BLACK
            val accuracyText = if (accuracy > 0f) {
                String.format("±%.1fm", accuracy)
            } else {
                ""
            }
            
            val methodText = "$positioningMethod $accuracyText"
            val textBounds = android.graphics.Rect()
            methodInfoPaint.getTextBounds(methodText, 0, methodText.length, textBounds)
            
            // Draw background for method info
            val padding = 8f
            val bgRect = android.graphics.RectF(
                10f,
                height - textBounds.height() - 30f,
                10f + textBounds.width() + 2 * padding,
                height - 10f
            )
            canvas.drawRoundRect(bgRect, 8f, 8f, methodBgPaint)
            
            // Draw colored indicator for method
            canvas.drawRect(
                10f,
                height - textBounds.height() - 30f,
                18f,
                height - 10f,
                Paint().apply { color = methodColor; style = Paint.Style.FILL }
            )
            
            // Draw method text
            methodInfoPaint.color = methodColor
            canvas.drawText(
                methodText,
                20f,
                height - 20f,
                methodInfoPaint
            )
        }
    }
    
    /**
     * Draw visualization showing movement history
     */
    private fun drawMovementHistoryVisualization(canvas: Canvas, offsetX: Float, offsetY: Float, scale: Float) {
        // Draw beacons
        beacons.forEach { beacon ->
            val x = offsetX + (beacon.position.x * scale).toFloat()
            val y = offsetY + (beacon.position.y * scale).toFloat()
            
            canvas.drawCircle(x, y, DEFAULT_BEACON_RADIUS, Paint().apply {
                color = Color.parseColor("#9C27B0") // Purple
                style = Paint.Style.FILL
                isAntiAlias = true
            })
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
            
            // Draw dots for each position
            positionHistory.forEachIndexed { index, pos ->
                val x = offsetX + (pos.x * scale).toFloat()
                val y = offsetY + (pos.y * scale).toFloat()
                
                // Make older positions more transparent
                val alpha = (255 * (index + 1) / positionHistory.size)
                userPaint.alpha = alpha
                canvas.drawCircle(x, y, 5f, userPaint)
            }
            
            // Reset alpha
            userPaint.alpha = 255
        }
        
        // Draw current position
        userPosition?.let { pos ->
            val x = offsetX + (pos.x * scale).toFloat()
            val y = offsetY + (pos.y * scale).toFloat()
            
            // Draw accuracy circle if enabled
            if (showAccuracy) {
                canvas.drawCircle(x, y, (accuracy * scale).toFloat(), userAccuracyPaint)
            }
            
            // Draw location pin for user position (like in the example)
            val pinPaint = Paint().apply {
                color = Color.parseColor("#E57373") // Red
                style = Paint.Style.FILL
                isAntiAlias = true
            }
            
            // Draw a simple location pin (circle with dot in center)
            canvas.drawCircle(x, y, 18f, Paint().apply { 
                color = Color.WHITE
                style = Paint.Style.FILL
                isAntiAlias = true
            })
            
            canvas.drawCircle(x, y, 15f, pinPaint)
            
            // Draw coordinates in pin
            val coordText = "(${pos.x.toInt()}, ${pos.y.toInt()})"
            canvas.drawText(coordText, x, y + 5f, Paint().apply {
                color = Color.WHITE
                textSize = 10f
                textAlign = Paint.Align.CENTER
                isAntiAlias = true
            })
            
            // Draw positioning method if known
            if (positioningMethod != "Unknown") {
                textPaint.textSize = 20f
                canvas.drawText(
                    "Method: $positioningMethod",
                    x + 20f, y + 20f, textPaint
                )
            }
        }
    }
    
    /**
     * Draw grid lines for the floor plan
     */
    private fun drawGrid(canvas: Canvas, offsetX: Float, offsetY: Float, scale: Float) {
        // Draw vertical grid lines every 5 meters (no labels, just subtle lines)
        for (x in 0..floorPlanWidthMeters.toInt() step 5) {
            val xPos = offsetX + (x * scale)
            canvas.drawLine(
                xPos, offsetY,
                xPos, offsetY + (floorPlanHeightMeters * scale),
                gridPaint
            )
        }
        
        // Draw horizontal grid lines every 5 meters (no labels)
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
     * Handle pinch zoom gestures
     */
    private inner class ScaleListener : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            scaleFactor *= detector.scaleFactor
            
            // Constrain scale
            scaleFactor = max(0.5f, min(scaleFactor, 3.0f))
            
            invalidate()
            return true
        }
    }
    
    /**
     * Handle pan gestures
     */
    private inner class GestureListener : GestureDetector.SimpleOnGestureListener() {
        override fun onScroll(
            e1: MotionEvent?,
            e2: MotionEvent,
            distanceX: Float,
            distanceY: Float
        ): Boolean {
            translateX -= distanceX
            translateY -= distanceY
            
            // Constrain translation
            val maxTranslate = 1000f * scaleFactor
            translateX = max(-maxTranslate, min(translateX, maxTranslate))
            translateY = max(-maxTranslate, min(translateY, maxTranslate))
            
            invalidate()
            return true
        }
        
        override fun onDoubleTap(e: MotionEvent): Boolean {
            // Reset zoom and pan
            scaleFactor = 1f
            translateX = 0f
            translateY = 0f
            invalidate()
            return true
        }
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