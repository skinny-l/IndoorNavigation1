package com.example.indoornavigation.ui.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.drawable.BitmapDrawable
import android.util.AttributeSet
import android.util.Log
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import androidx.core.content.ContextCompat
import com.example.indoornavigation.R
import com.example.indoornavigation.data.models.NavNode
import com.example.indoornavigation.data.models.Position
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Custom view for displaying indoor floor plans with position markers
 */
open class FloorPlanView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // Paint for drawing
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    // Floor plan bitmap and current floor
    private var floorPlanBitmap: Bitmap? = null
    protected var currentFloor = 0

    // Transformation matrix
    private val matrix = Matrix()
    private val savedMatrix = Matrix()

    // User position
    private var userPosition: Position? = null
    private var userPositionBitmap: Bitmap?
    
    // Fallback user marker (simple red dot)
    private val userMarkerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.RED
        style = Paint.Style.FILL
    }
    
    // Movement history for breadcrumb trail
    private val positionHistory = mutableListOf<Position>()
    private val MAX_HISTORY_POINTS = 20 // Maximum number of breadcrumb points to display
    private val breadcrumbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.breadcrumbColor)
        strokeWidth = 8f
        style = Paint.Style.FILL
        alpha = 180
    }
    private var recordHistory = true // Flag to enable/disable history recording

    // POI markers
    private val markers = mutableListOf<Marker>()

    // Route visualization
    private var routePoints: List<Position> = emptyList()
    private val routePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.pathColor)
        strokeWidth = 12f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        style = Paint.Style.STROKE
        // Add shadow for better visibility
        setShadowLayer(6f, 0f, 0f, ContextCompat.getColor(context, android.R.color.holo_blue_dark))
    }

    // Route background for better visibility
    private val routeBackgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.pathStroke)
        strokeWidth = 16f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        style = Paint.Style.STROKE
        alpha = 180 // Semi-transparent
    }

    // Start and end markers paint
    private val startPointPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.pathStart)
        style = Paint.Style.FILL
    }

    private val endPointPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.pathEnd)
        style = Paint.Style.FILL
    }

    // Arrow paint for path direction indicators
    private val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }

    // Gesture detection
    private val gestureDetector: GestureDetector
    private val scaleGestureDetector: ScaleGestureDetector

    // Scale limits
    private var minScale = 0.5f
    private var maxScale = 5.0f
    protected var currentScale = 1.0f

    // Current transformation values
    protected var translateX = 0f
    protected var translateY = 0f

    // Floor plan dimensions
    private var planWidth = 0f
    private var planHeight = 0f

    // Real-world dimensions (in meters) of the floor plan
    // These should be set based on the actual building dimensions
    private var realWorldWidth = 75.0f // CS1 building width
    private var realWorldHeight = 75.0f // CS1 building height

    // Coordinates for translation calculations
    private val lastTouchPoint = PointF()

    // Debug mode
    private var debugMode = false
    private var navigationNodes = emptyList<NavNode>()
    private val nodePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.RED
        style = Paint.Style.FILL
    }
    private val nodeConnectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.YELLOW
        strokeWidth = 3f
        style = Paint.Style.STROKE
    }

    // Overlay variables
    private var overlayBitmap: Bitmap? = null
    private var overlayAlpha = 125 // Value between 0-255
    private var showOverlay = false
    private var overlayType = OverlayType.NONE

    // Overlay paint for semi-transparency
    private val overlayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        alpha = overlayAlpha
    }

    // Heatmap paint for signal strength visualization
    private val heatmapPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    // Building dimensions in meters
    private var buildingWidth = 75.0 // CS1 building width
    private var buildingHeight = 75.0 // CS1 building height
    
    // OnTouchListener for navigation path calculations
    private var navigationTouchListener: ((Position) -> Unit)? = null
    
    // Drag and drop variables
    private var isDragging = false
    private var draggedMarker: Marker? = null
    private var dragOffset = PointF()
    private var markerDragListener: ((Marker, Position) -> Unit)? = null
    private var isEditMode = false

    init {
        // Load user position marker
        val userMarkerDrawable = ContextCompat.getDrawable(context, R.drawable.ic_user_location)
        if (userMarkerDrawable is BitmapDrawable) {
            userPositionBitmap = userMarkerDrawable.bitmap
        } else {
            // Convert vector drawable to bitmap manually
            val width = userMarkerDrawable?.intrinsicWidth ?: 48
            val height = userMarkerDrawable?.intrinsicHeight ?: 48
            
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            userMarkerDrawable?.setBounds(0, 0, canvas.width, canvas.height)
            userMarkerDrawable?.draw(canvas)
            userPositionBitmap = bitmap
        }
        
        // Setup gesture detectors
        gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onScroll(
                e1: MotionEvent?,
                e2: MotionEvent,
                distanceX: Float,
                distanceY: Float
            ): Boolean {
                translateX -= distanceX
                translateY -= distanceY
                
                // Apply constraints to keep the floor plan visible
                constrainTranslation()
                applyTransformation()
                return true
            }
            
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                // Remove the automatic marker creation on single tap in edit mode
                // This was causing duplicate markers during drag operations
                return true
            }
        })
        
        scaleGestureDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                // Apply scaling
                currentScale *= detector.scaleFactor
                
                // Limit scale to min/max
                currentScale = max(minScale, min(currentScale, maxScale))
                
                // Apply transformation
                applyTransformation()
                return true
            }
        })
    }
    
    /**
     * Set the floor plan to display
     */
    fun setFloorPlan(floor: Int) {
        currentFloor = 0  // Always use floor 0 (ground floor)
        
        // Use the detailed ground floor plan
        val resourceId = R.drawable.ground_floor
        
        try {
            val drawable = ContextCompat.getDrawable(context, resourceId)
            if (drawable != null) {
                if (drawable is BitmapDrawable) {
                    floorPlanBitmap = drawable.bitmap
                } else {
                    // Convert SVG vector drawable to bitmap
                    val width = drawable.intrinsicWidth.takeIf { it > 0 } ?: 1550
                    val height = drawable.intrinsicHeight.takeIf { it > 0 } ?: 835
                    
                    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(bitmap)
                    drawable.setBounds(0, 0, canvas.width, canvas.height)
                    drawable.draw(canvas)
                    floorPlanBitmap = bitmap
                }
                
                // Store dimensions
                floorPlanBitmap?.let {
                    planWidth = it.width.toFloat()
                    planHeight = it.height.toFloat()
                    
                    // Reset transformation
                    resetTransformation()
                }
                
                Log.d("FloorPlanView", "Floor plan loaded successfully: ${planWidth}x${planHeight}")
            } else {
                Log.e("FloorPlanView", "Failed to load drawable resource")
                createFallbackFloorPlan()
            }
        } catch (e: Exception) {
            Log.e("FloorPlanView", "Error loading floor plan: ${e.message}")
            createFallbackFloorPlan()
            e.printStackTrace()
        }
        
        invalidate()
    }
    
    /**
     * Create a fallback floor plan when the main one fails to load
     */
    private fun createFallbackFloorPlan() {
        // If the new floor plan isn't available yet, create a blank canvas
        floorPlanBitmap = Bitmap.createBitmap(1000, 1000, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(floorPlanBitmap!!)
        canvas.drawColor(android.graphics.Color.LTGRAY)
        
        // Draw a grid for better visualization
        val gridPaint = Paint().apply {
            color = Color.GRAY
            strokeWidth = 2f
            style = Paint.Style.STROKE
        }
        
        // Draw grid lines
        val gridSize = 100
        for (i in 0..10) {
            canvas.drawLine(0f, i * gridSize.toFloat(), 1000f, i * gridSize.toFloat(), gridPaint)
            canvas.drawLine(i * gridSize.toFloat(), 0f, i * gridSize.toFloat(), 1000f, gridPaint)
        }
        
        // Add text to indicate this is a fallback
        val textPaint = Paint().apply {
            color = Color.BLACK
            textSize = 40f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("Floor Plan Loading...", 500f, 500f, textPaint)
        
        // Store dimensions
        planWidth = 1000f
        planHeight = 1000f
        
        // Reset transformation
        resetTransformation()
    }
    
    /**
     * Set the user position
     * @param position The position in real-world coordinates (meters)
     */
    fun setUserPosition(position: Position?) {
        // Only update if the floor matches
        if (position != null && position.floor == currentFloor) {
            Log.d("FloorPlanView", "Setting user position: ${position.x}, ${position.y}, floor=${position.floor}")
            userPosition = position
            
            // Add to position history if recording is enabled and position is different from last one
            if (recordHistory && (positionHistory.isEmpty() || 
                position.distanceTo(positionHistory.last()) > 0.5)) { // Only add if moved more than 0.5 meters
                
                positionHistory.add(position)
                // Limit history size
                if (positionHistory.size > MAX_HISTORY_POINTS) {
                    positionHistory.removeAt(0)
                }
            }
            
            invalidate()
        } else if (position == null) {
            Log.w("FloorPlanView", "Received null position")
            userPosition = null
            invalidate()
        } else {
            Log.d("FloorPlanView", "Ignoring position on different floor: position floor=${position.floor}, current floor=$currentFloor")
        }
    }
    
    /**
     * Add a POI marker to the floor plan
     */
    fun addMarker(marker: Marker) {
        markers.add(marker)
        invalidate()
    }
    
    /**
     * Clear all markers
     */
    fun clearMarkers() {
        markers.clear()
        invalidate()
    }
    
    /**
     * Center the view on the user's position
     */
    fun centerOnUser() {
        userPosition?.let { position ->
            centerOnPosition(position)
        }
    }
    
    /**
     * Center the view on a specific position
     */
    fun centerOnPosition(position: Position) {
        // Convert real-world coordinates to pixel coordinates
        val pixelX = (position.x / realWorldWidth * planWidth).toFloat()
        val pixelY = (position.y / realWorldHeight * planHeight).toFloat()
        
        // Center the view on this position
        translateX = width / 2f - pixelX * currentScale
        translateY = height / 2f - pixelY * currentScale
        
        // Safety check
        if (translateX.isNaN() || translateY.isNaN()) {
            translateX = 0f
            translateY = 0f
        }
        
        // Apply constraints
        constrainTranslation()
        applyTransformation()
        invalidate()
    }
    
    /**
     * Reset the transformation to default
     */
    fun resetTransformation() {
        if (width == 0 || height == 0 || planWidth == 0f || planHeight == 0f) {
            Log.w("FloorPlanView", "resetTransformation skipped: viewWidth=$width, viewHeight=$height, planWidth=$planWidth, planHeight=$planHeight")
            return
        }
        
        val scaleX = width / planWidth
        val scaleY = height / planHeight
        currentScale = min(scaleX, scaleY) * 0.9f // 90% to show some margin
        
        // Center the plan
        translateX = (width - planWidth * currentScale) / 2
        translateY = (height - planHeight * currentScale) / 2
        
        applyTransformation()
        Log.d("FloorPlanView", "resetTransformation applied: newScale=$currentScale, newTx=$translateX, newTy=$translateY")
    }
    
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        
        // When size changes, reset the transformation to fit the new size
        if (floorPlanBitmap != null) {
            resetTransformation()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Critical Check: Ensure bitmap exists and view has dimensions
        if (floorPlanBitmap == null || width == 0 || height == 0) {
            Log.w("FloorPlanView", "onDraw called with null bitmap or zero/invalid dimensions. Skipping draw. Bitmap: $floorPlanBitmap, ViewWidth: $width, ViewHeight: $height")
            // Draw a placeholder background
            canvas.drawColor(Color.LTGRAY)
            paint.color = Color.RED
            paint.textSize = 40f
            canvas.drawText("Loading floor plan...", 50f, 100f, paint)
            
            // If we have a user position, still draw the user marker
            userPosition?.let { position ->
                // Draw a simple red dot in the center of the view
                canvas.drawCircle(width / 2f, height / 2f, 20f, userMarkerPaint)
            }
            return
        }

        // Draw the floor plan
        floorPlanBitmap?.let { bitmap ->
            canvas.save()
            // Apply current transformation
            canvas.setMatrix(matrix)
            canvas.drawBitmap(bitmap, 0f, 0f, paint)

            // Draw overlay if enabled
            if (showOverlay && overlayBitmap != null) {
                canvas.drawBitmap(overlayBitmap!!, 0f, 0f, overlayPaint)
            }

            canvas.restore()

            // Draw route if available
            if (routePoints.isNotEmpty()) {
                drawRoute(canvas)
            }

            // Draw navigation nodes in debug mode
            if (debugMode && navigationNodes.isNotEmpty()) {
                drawNavigationNodes(canvas)
            }
            
            // Draw breadcrumb trail
            if (positionHistory.size > 1) {
                drawBreadcrumbTrail(canvas)
            }

            // Draw user position if available
            userPosition?.let { position ->
                if (userPositionBitmap != null) {
                    // Convert real-world coordinates to pixel coordinates
                    val pixelX = (position.x / realWorldWidth * planWidth).toFloat()
                    val pixelY = (position.y / realWorldHeight * planHeight).toFloat()
                    
                    // Calculate the marker position with current transformation
                    val pts = floatArrayOf(pixelX, pixelY)
                    matrix.mapPoints(pts)
                    
                    // Draw the marker centered at the position
                    canvas.drawBitmap(
                        userPositionBitmap!!,
                        pts[0] - userPositionBitmap!!.width / 2f,
                        pts[1] - userPositionBitmap!!.height / 2f,
                        paint
                    )
                    
                    // Draw direction indicator if available
                    if (positionHistory.size > 1) {
                        drawDirectionIndicator(canvas, position, pts[0], pts[1])
                    }
                } else {
                    // Draw a simple red dot as fallback
                    val pixelX = (position.x / realWorldWidth * planWidth).toFloat()
                    val pixelY = (position.y / realWorldHeight * planHeight).toFloat()
                    
                    // Calculate the marker position with current transformation
                    val pts = floatArrayOf(pixelX, pixelY)
                    matrix.mapPoints(pts)
                    
                    canvas.drawCircle(pts[0], pts[1], 20f, userMarkerPaint)
                    
                    // Draw direction indicator if available
                    if (positionHistory.size > 1) {
                        drawDirectionIndicator(canvas, position, pts[0], pts[1])
                    }
                }
            }
            
            // Draw markers
            for (marker in markers) {
                if (marker.floor == currentFloor) {
                    marker.bitmap?.let { markerBitmap ->
                        // Convert real-world coordinates to pixel coordinates
                        val pixelX = (marker.position.x / realWorldWidth * planWidth).toFloat()
                        val pixelY = (marker.position.y / realWorldHeight * planHeight).toFloat()
                        
                        // Calculate the marker position with current transformation
                        val pts = floatArrayOf(pixelX, pixelY)
                        matrix.mapPoints(pts)
                        
                        // Draw the marker centered at the position
                        canvas.drawBitmap(
                            markerBitmap,
                            pts[0] - markerBitmap.width / 2f,
                            pts[1] - markerBitmap.height / 2f,
                            paint
                        )
                    }
                }
            }
        }
    }

    /**
     * Draw the route path
     */
    private fun drawRoute(canvas: Canvas) {
        // Filter route points for current floor
        val currentFloorRoutePoints = routePoints.filter { it.floor == currentFloor }

        if (currentFloorRoutePoints.size < 2) return

        // Create path for drawing
        val path = android.graphics.Path()

        // Start path at the first point
        val firstPoint = currentFloorRoutePoints.first()
        var pixelX = (firstPoint.x / realWorldWidth * planWidth).toFloat()
        var pixelY = (firstPoint.y / realWorldHeight * planHeight).toFloat()

        // Transform coordinates
        val pts = floatArrayOf(pixelX, pixelY)
        matrix.mapPoints(pts)
        path.moveTo(pts[0], pts[1])

        // Use path interpolation for smoother curves
        val pointCoords = mutableListOf<Float>()

        // Collect all coordinates
        for (i in 1 until currentFloorRoutePoints.size) {
            val point = currentFloorRoutePoints[i]
            pixelX = (point.x / realWorldWidth * planWidth).toFloat()
            pixelY = (point.y / realWorldHeight * planHeight).toFloat()

            // Transform coordinates
            val nextPts = floatArrayOf(pixelX, pixelY)
            matrix.mapPoints(nextPts)
            pointCoords.add(nextPts[0])
            pointCoords.add(nextPts[1])
        }

        // Create smooth path with interpolation
        if (currentFloorRoutePoints.size >= 3) {
            for (i in 0 until pointCoords.size / 2) {
                val x = pointCoords[i * 2]
                val y = pointCoords[i * 2 + 1]

                if (i == 0) {
                    // First point after moveTo - just lineTo
                    path.lineTo(x, y)
                } else {
                    // Use quadratic curve for smoother appearance
                    val prevX = if (i > 0) pointCoords[(i - 1) * 2] else pts[0]
                    val prevY = if (i > 0) pointCoords[(i - 1) * 2 + 1] else pts[1]

                    // Control point is halfway between points
                    val controlX = (prevX + x) / 2
                    val controlY = (prevY + y) / 2

                    path.quadTo(controlX, controlY, x, y)
                }
            }
        } else {
            // Simple line for just 2 points
            path.lineTo(pointCoords[0], pointCoords[1])
        }

        // Draw background path for better visibility
        canvas.drawPath(path, routeBackgroundPaint)

        // Draw the path
        canvas.drawPath(path, routePaint)

        // Draw start and end markers
        if (currentFloorRoutePoints.isNotEmpty()) {
            // Start point - draw a green circle
            val startPoint = currentFloorRoutePoints.first()
            val startX = (startPoint.x / realWorldWidth * planWidth).toFloat()
            val startY = (startPoint.y / realWorldHeight * planHeight).toFloat()
            val startPts = floatArrayOf(startX, startY)
            matrix.mapPoints(startPts)

            canvas.drawCircle(startPts[0], startPts[1], 12f * currentScale, startPointPaint)
            // Draw white border
            val startBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                style = Paint.Style.STROKE
                strokeWidth = 3f
            }
            canvas.drawCircle(startPts[0], startPts[1], 12f * currentScale, startBorderPaint)

            // End point - draw a red circle
            val endPoint = currentFloorRoutePoints.last()
            val endX = (endPoint.x / realWorldWidth * planWidth).toFloat()
            val endY = (endPoint.y / realWorldHeight * planHeight).toFloat()
            val endPts = floatArrayOf(endX, endY)
            matrix.mapPoints(endPts)

            canvas.drawCircle(endPts[0], endPts[1], 12f * currentScale, endPointPaint)
            // Draw white border
            val endBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                style = Paint.Style.STROKE
                strokeWidth = 3f
            }
            canvas.drawCircle(endPts[0], endPts[1], 12f * currentScale, endBorderPaint)

            // Draw direction arrows along the path for longer paths
            if (currentFloorRoutePoints.size > 2 && pointCoords.size >= 4) {
                drawDirectionArrows(canvas, currentFloorRoutePoints)
            }
        }
    }

    /**
     * Draw direction arrows along the path
     */
    private fun drawDirectionArrows(canvas: Canvas, points: List<Position>) {
        // Only add arrows if we have enough points
        if (points.size < 3) return
        
        // Calculate how many arrows to draw (1-3 based on path length)
        val arrowCount = kotlin.math.min(3, points.size / 2)
        val step = points.size / (arrowCount + 1)
        
        // Draw arrows at calculated positions
        for (i in 1..arrowCount) {
            val index = i * step
            if (index >= points.size - 1) continue
            
            // Get two consecutive points to determine direction
            val p1 = points[index]
            val p2 = points[index + 1]
            
            // Convert to screen coordinates
            val x1 = (p1.x / realWorldWidth * planWidth).toFloat()
            val y1 = (p1.y / realWorldHeight * planHeight).toFloat()
            val x2 = (p2.x / realWorldWidth * planWidth).toFloat()
            val y2 = (p2.y / realWorldHeight * planHeight).toFloat()
            
            // Transform coordinates
            val pts = floatArrayOf(x1, y1, x2, y2)
            matrix.mapPoints(pts)
            
            // Calculate arrow position (midpoint) and angle
            val midX = (pts[0] + pts[2]) / 2
            val midY = (pts[1] + pts[3]) / 2
            val dx = pts[2] - pts[0]
            val dy = pts[3] - pts[1]
            val angle = Math.atan2(dy.toDouble(), dx.toDouble())
            
            // Draw arrow
            val arrowSize = 10f * currentScale
            val arrowPath = android.graphics.Path()
            
            // Arrow shape: a simple triangle pointing in the direction of travel
            arrowPath.moveTo(
                (midX + arrowSize * Math.cos(angle)).toFloat(),
                (midY + arrowSize * Math.sin(angle)).toFloat()
            )
            arrowPath.lineTo(
                (midX + arrowSize * Math.cos(angle - 2.5)).toFloat(),
                (midY + arrowSize * Math.sin(angle - 2.5)).toFloat()
            )
            arrowPath.lineTo(
                (midX + arrowSize * Math.cos(angle + 2.5)).toFloat(),
                (midY + arrowSize * Math.sin(angle + 2.5)).toFloat()
            )
            arrowPath.close()
            
            canvas.drawPath(arrowPath, arrowPaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        // Handle drag and drop
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                if (isEditMode) {
                    // Check if a marker was clicked (for dragging)
                    for (marker in markers) {
                        if (marker.floor == currentFloor) {
                            // Convert marker position to screen coordinates
                            val markerX = (marker.position.x / realWorldWidth * planWidth).toFloat()
                            val markerY = (marker.position.y / realWorldHeight * planHeight).toFloat()
                            val markerPts = floatArrayOf(markerX, markerY)
                            matrix.mapPoints(markerPts)
                            
                            // Check if the touch is near the marker
                            val markerWidth = marker.bitmap?.width ?: 48
                            val markerHeight = marker.bitmap?.height ?: 48
                            val touchDistance = sqrt(
                                (event.x - markerPts[0]).toDouble().pow(2.0) +
                                (event.y - markerPts[1]).toDouble().pow(2.0)
                            )
                            
                            if (touchDistance < 60) { // 60 pixels tolerance
                                isDragging = true
                                draggedMarker = marker
                                
                                // Calculate the offset between touch point and marker center
                                dragOffset.x = event.x - markerPts[0]
                                dragOffset.y = event.y - markerPts[1]
                                
                                return true
                            }
                        }
                    }
                }
            }
            
            MotionEvent.ACTION_MOVE -> {
                if (isDragging && draggedMarker != null) {
                    // Get the current touch point
                    val touchX = event.x - dragOffset.x
                    val touchY = event.y - dragOffset.y
                    
                    // Convert touch coordinates to map coordinates
                    val mapCoords = screenToFloorPlanCoordinates(touchX, touchY)
                    
                    if (mapCoords != null) {
                        // Update the marker position directly in the markers list
                        val markerIndex = markers.indexOf(draggedMarker)
                        if (markerIndex != -1) {
                            val newPosition = Position(mapCoords.first, mapCoords.second, currentFloor)
                            markers[markerIndex] = markers[markerIndex].copy(position = newPosition)
                            draggedMarker = markers[markerIndex] // Update reference
                        }
                        
                        // Redraw the view
                        invalidate()
                    }
                    
                    return true
                }
            }
            
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isDragging && draggedMarker != null) {
                    // Final position update and notify listener
                    val touchX = event.x - dragOffset.x
                    val touchY = event.y - dragOffset.y
                    val mapCoords = screenToFloorPlanCoordinates(touchX, touchY)
                    
                    if (mapCoords != null) {
                        val finalPosition = Position(mapCoords.first, mapCoords.second, currentFloor)
                        
                        // Update the marker position in the markers list
                        val markerIndex = markers.indexOf(draggedMarker)
                        if (markerIndex != -1) {
                            markers[markerIndex] = markers[markerIndex].copy(position = finalPosition)
                            
                            // Notify the listener about the final position
                            markerDragListener?.invoke(markers[markerIndex], finalPosition)
                        }
                    }
                    
                    // Reset drag state
                    isDragging = false
                    draggedMarker = null
                    dragOffset.set(0f, 0f)
                    
                    invalidate()
                    return true
                }
            }
        }
        
        // Only handle other touch events if not dragging
        if (!isDragging) {
            // Let the gesture detectors handle the event
            scaleGestureDetector.onTouchEvent(event)

            if (!scaleGestureDetector.isInProgress) {
                gestureDetector.onTouchEvent(event)
            }

            // Handle touch for navigation purpose
            if (event.action == MotionEvent.ACTION_DOWN) {
                // Convert touch coordinates to map positions
                val floorCoords = screenToFloorPlanCoordinates(event.x, event.y)
                
                if (floorCoords != null) {
                    // Convert to real-world coordinates
                    val touchPosition = Position(floorCoords.first, floorCoords.second, currentFloor)

                    // Notify listener if set
                    navigationTouchListener?.invoke(touchPosition)
                }
            }
        }

        return true
    }

    /**
     * Set a listener for touch events that should trigger navigation
     */
    fun setNavigationTouchListener(listener: (Position) -> Unit) {
        navigationTouchListener = listener
    }
    
    /**
     * Set the building dimensions in meters
     */
    fun setBuildingDimensions(width: Double, height: Double) {
        realWorldWidth = width.toFloat()
        realWorldHeight = height.toFloat()
        invalidate()
    }
    
    /**
     * Apply the current transformation to the matrix
     */
    private fun applyTransformation() {
        matrix.reset()
        matrix.postScale(currentScale, currentScale)
        matrix.postTranslate(translateX, translateY)
        Log.d("FloorPlanView", "applyTransformation: scale=$currentScale, tx=$translateX, ty=$translateY, viewWidth=$width, viewHeight=$height, planWidth=$planWidth, planHeight=$planHeight")
        invalidate()
    }
    
    /**
     * Constrain translation to keep the floor plan visible
     */
    private fun constrainTranslation() {
        // Calculate bounds to keep at least some part of the floor plan visible
        val scaledWidth = planWidth * currentScale
        val scaledHeight = planHeight * currentScale
        
        // Right bound
        translateX = min(translateX, width.toFloat())
        
        // Left bound
        translateX = max(translateX, -(scaledWidth - width / 4f))
        
        // Bottom bound
        translateY = min(translateY, height.toFloat())
        
        // Top bound
        translateY = max(translateY, -(scaledHeight - height / 4f))
    }
    
    /**
     * Set the real-world dimensions of the floor plan (in meters)
     */
    fun setRealWorldDimensions(width: Float, height: Float) {
        realWorldWidth = width
        realWorldHeight = height
    }

    /**
     * Set a route to display on the floor plan
     */
    fun setRoute(route: List<Position>) {
        routePoints = route
        invalidate()
    }

    /**
     * Clear the current route
     */
    fun clearRoute() {
        routePoints = emptyList()
        invalidate()
    }
    
    /**
     * Remove a specific marker
     */
    fun removeMarker(marker: Marker) {
        markers.remove(marker)
        invalidate()
    }
    
    /**
     * Set debug mode to show navigation nodes
     */
    fun setDebugMode(isDebugMode: Boolean) {
        this.debugMode = isDebugMode
        invalidate()
    }
    
    /**
     * Set navigation nodes to display in debug mode
     */
    fun setNavigationNodes(nodes: List<NavNode>) {
        navigationNodes = nodes
        invalidate()
    }
    
    /**
     * Draw navigation nodes and their connections
     */
    private fun drawNavigationNodes(canvas: Canvas) {
        // Draw connections first (so nodes appear on top)
        val nodeMap = navigationNodes.associateBy { it.id }
        
        for (node in navigationNodes) {
            val nodeX = (node.position.x / realWorldWidth * planWidth).toFloat()
            val nodeY = (node.position.y / realWorldHeight * planHeight).toFloat()
            val nodePts = floatArrayOf(nodeX, nodeY)
            matrix.mapPoints(nodePts)
            
            // Draw connections to other nodes
            for (connectedNodeId in node.connections) {
                val connectedNode = nodeMap[connectedNodeId] ?: continue
                val connectedX = (connectedNode.position.x / realWorldWidth * planWidth).toFloat()
                val connectedY = (connectedNode.position.y / realWorldHeight * planHeight).toFloat()
                val connectedPts = floatArrayOf(connectedX, connectedY)
                matrix.mapPoints(connectedPts)
                
                // Draw a line between the nodes
                canvas.drawLine(
                    nodePts[0], nodePts[1],
                    connectedPts[0], connectedPts[1],
                    nodeConnectionPaint
                )
            }
        }
        
        // Draw nodes on top of connections
        for (node in navigationNodes) {
            val nodeX = (node.position.x / realWorldWidth * planWidth).toFloat()
            val nodeY = (node.position.y / realWorldHeight * planHeight).toFloat()
            val nodePts = floatArrayOf(nodeX, nodeY)
            matrix.mapPoints(nodePts)
            
            // Draw node as a small circle
            canvas.drawCircle(nodePts[0], nodePts[1], 5f, nodePaint)
        }
    }
    
    /**
     * Convert screen coordinates to floor plan coordinates
     * @param screenX X coordinate on screen
     * @param screenY Y coordinate on screen
     * @return Pair of floor plan coordinates in the range 0-100 (meters), or null if outside the plan
     */
    fun screenToFloorPlanCoordinates(screenX: Float, screenY: Float): Pair<Double, Double>? {
        // Create inverse matrix
        val inverseMatrix = Matrix()
        if (!matrix.invert(inverseMatrix)) {
            return null
        }
        
        // Transform screen coordinates to floor plan coordinates
        val points = floatArrayOf(screenX, screenY)
        inverseMatrix.mapPoints(points)
        
        val planX = points[0]
        val planY = points[1]
        
        // Check if within bounds
        if (planX < 0 || planX > planWidth || planY < 0 || planY > planHeight) {
            return null
        }
        
        // Convert to real world coordinates (meters)
        val realX = planX / planWidth * realWorldWidth
        val realY = planY / planHeight * realWorldHeight
        
        return Pair(realX.toDouble(), realY.toDouble())
    }
    
    /**
     * Represents a marker on the floor plan
     */
    data class Marker(
        var position: Position,
        val bitmap: Bitmap?,
        val title: String,
        val floor: Int
    )

    /**
     * Set the overlay bitmap to display on top of the floor plan
     * @param bitmap The overlay bitmap (should have same dimensions as floor plan)
     * @param type The type of overlay
     * @param alpha Transparency level (0-255), where 0 is fully transparent and 255 is opaque
     */
    fun setOverlay(bitmap: Bitmap?, type: OverlayType = OverlayType.CUSTOM, alpha: Int = 125) {
        overlayBitmap = bitmap
        overlayType = type
        overlayAlpha = alpha.coerceIn(0, 255)
        overlayPaint.alpha = overlayAlpha
        showOverlay = bitmap != null
        invalidate()
    }

    /**
     * Toggle overlay visibility
     * @return true if overlay is now visible, false otherwise
     */
    fun toggleOverlay(): Boolean {
        showOverlay = !showOverlay
        invalidate()
        return showOverlay
    }

    /**
     * Set overlay transparency
     * @param alpha value between 0 (transparent) and 255 (opaque)
     */
    fun setOverlayAlpha(alpha: Int) {
        overlayAlpha = alpha.coerceIn(0, 255)
        overlayPaint.alpha = overlayAlpha
        invalidate()
    }

    /**
     * Create a WiFi signal strength heatmap overlay
     * @param accessPoints List of WiFi access points with signal strength and position
     * @return Bitmap containing the heatmap
     */
    fun createWifiHeatmapOverlay(accessPoints: List<Pair<Position, Int>>): Bitmap {
        val width = floorPlanBitmap?.width ?: 1000
        val height = floorPlanBitmap?.height ?: 800

        val heatmapBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(heatmapBitmap)

        // Clear bitmap with transparent color
        canvas.drawColor(android.graphics.Color.TRANSPARENT)

        // Define colors for signal strength
        val colors = listOf(
            android.graphics.Color.rgb(255, 0, 0),    // Red (weakest)
            android.graphics.Color.rgb(255, 165, 0),  // Orange
            android.graphics.Color.rgb(255, 255, 0),  // Yellow
            android.graphics.Color.rgb(0, 255, 0)     // Green (strongest)
        )

        // Draw gradient circles for each access point
        for ((position, rssi) in accessPoints) {
            // Convert position to pixel coordinates
            val x = (position.x / realWorldWidth * width).toFloat()
            val y = (position.y / realWorldHeight * height).toFloat()

            // Determine circle radius based on signal strength (RSSI)
            // Typical RSSI ranges from -100 dBm (weak) to -30 dBm (strong)
            val signalStrength = ((rssi + 100) / 70f).coerceIn(0f, 1f)
            val radius = 150 * signalStrength

            // Determine color based on signal strength
            val colorIndex = (signalStrength * (colors.size - 1)).toInt().coerceIn(0, colors.size - 1)
            val nextColorIndex = (colorIndex + 1).coerceAtMost(colors.size - 1)

            // Create a radial gradient for smooth color transition
            val gradient = android.graphics.RadialGradient(
                x, y, radius,
                colors[nextColorIndex], colors[colorIndex],
                android.graphics.Shader.TileMode.CLAMP
            )

            heatmapPaint.shader = gradient
            heatmapPaint.alpha = (180 * signalStrength).toInt() + 75 // More signal = more opaque

            canvas.drawCircle(x, y, radius, heatmapPaint)
        }

        return heatmapBitmap
    }

    /**
     * Create an accessibility overlay showing wheelchair-accessible routes
     * @param accessibleRoutes List of positions representing accessible paths
     * @return Bitmap containing the accessibility overlay
     */
    fun createAccessibilityOverlay(accessibleRoutes: List<Position>): Bitmap {
        val width = floorPlanBitmap?.width ?: 1000
        val height = floorPlanBitmap?.height ?: 800

        val accessibilityBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(accessibilityBitmap)

        // Clear bitmap with transparent color
        canvas.drawColor(android.graphics.Color.TRANSPARENT)

        // Setup paint for accessible paths
        val pathPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.rgb(70, 130, 180) // Steel blue
            strokeWidth = 10f
            style = Paint.Style.STROKE
            alpha = 200
        }

        // Draw paths connecting accessible routes
        if (accessibleRoutes.size >= 2) {
            val path = android.graphics.Path()

            // Start at the first point
            val firstPoint = accessibleRoutes.first()
            val startX = (firstPoint.x / realWorldWidth * width).toFloat()
            val startY = (firstPoint.y / realWorldHeight * height).toFloat()
            path.moveTo(startX, startY)

            // Connect all subsequent points
            for (i in 1 until accessibleRoutes.size) {
                val point = accessibleRoutes[i]
                val x = (point.x / realWorldWidth * width).toFloat()
                val y = (point.y / realWorldHeight * height).toFloat()

                path.lineTo(x, y)
            }

            canvas.drawPath(path, pathPaint)

            // Draw accessibility markers at key points
            val markerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.rgb(30, 144, 255) // Dodger blue
                style = Paint.Style.FILL
            }

            // Draw a wheelchair icon or just a circle at each accessible point
            for (point in accessibleRoutes) {
                val x = (point.x / realWorldWidth * width).toFloat()
                val y = (point.y / realWorldHeight * height).toFloat()
                canvas.drawCircle(x, y, 8f, markerPaint)
            }
        }

        return accessibilityBitmap
    }

    /**
     * Draw the breadcrumb trail showing user's movement history
     */
    private fun drawBreadcrumbTrail(canvas: Canvas) {
        if (positionHistory.size < 2) return
        
        for (i in 0 until positionHistory.size - 1) { // Don't draw the last point (current position)
            val pos = positionHistory[i]
            
            // Skip points not on current floor
            if (pos.floor != currentFloor) continue
            
            // Convert position to pixel coordinates
            val pixelX = (pos.x / realWorldWidth * planWidth).toFloat()
            val pixelY = (pos.y / realWorldHeight * planHeight).toFloat()
            
            // Transform coordinates
            val pts = floatArrayOf(pixelX, pixelY)
            matrix.mapPoints(pts)
            
            // Draw breadcrumb point - size based on age (older points are smaller)
            val pointAge = i.toFloat() / positionHistory.size
            val pointSize = 5f * (0.5f + 0.5f * pointAge) * currentScale
            
            // Adjust transparency based on age (older points are more transparent)
            val alpha = (255 * (0.3f + 0.7f * pointAge)).toInt()
            breadcrumbPaint.alpha = alpha
            
            canvas.drawCircle(pts[0], pts[1], pointSize, breadcrumbPaint)
        }
    }
    
    /**
     * Draw a direction indicator showing which way the user is facing/moving
     */
    private fun drawDirectionIndicator(canvas: Canvas, position: Position, x: Float, y: Float) {
        if (positionHistory.size < 2) return
        
        // Calculate direction from previous position to current
        val prevPos = positionHistory[positionHistory.size - 2]
        val currentPos = position
        
        // Only draw if positions are on same floor
        if (prevPos.floor != currentPos.floor) return
        
        // Calculate angle
        val dx = currentPos.x - prevPos.x
        val dy = currentPos.y - prevPos.y
        
        // Only draw if there's meaningful movement
        if (Math.abs(dx) < 0.2 && Math.abs(dy) < 0.2) return
        
        val angle = Math.atan2(dy.toDouble(), dx.toDouble())
        
        // Draw direction triangle
        val directionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ContextCompat.getColor(context, R.color.directionIndicator)
            style = Paint.Style.FILL
        }
        
        val arrowSize = 15f * currentScale
        val trianglePath = Path()
        
        // Triangle pointing in direction of movement
        trianglePath.moveTo(
            x + arrowSize * Math.cos(angle).toFloat(),
            y + arrowSize * Math.sin(angle).toFloat()
        )
        trianglePath.lineTo(
            x + arrowSize * 0.5f * Math.cos(angle + Math.PI * 0.8).toFloat(),
            y + arrowSize * 0.5f * Math.sin(angle + Math.PI * 0.8).toFloat()
        )
        trianglePath.lineTo(
            x + arrowSize * 0.5f * Math.cos(angle - Math.PI * 0.8).toFloat(),
            y + arrowSize * 0.5f * Math.sin(angle - Math.PI * 0.8).toFloat()
        )
        trianglePath.close()
        
        canvas.drawPath(trianglePath, directionPaint)
    }
    
    /**
     * Clear position history/breadcrumb trail
     */
    fun clearPositionHistory() {
        positionHistory.clear()
        invalidate()
    }
    
    /**
     * Enable or disable recording of position history
     */
    fun setRecordPositionHistory(record: Boolean) {
        this.recordHistory = record
        if (!record) {
            positionHistory.clear()
            invalidate()
        }
    }
    
    /**
     * Get the current floor level being displayed
     */
    fun getCurrentFloorLevel(): Int {
        return currentFloor
    }
    
    /**
     * Set whether we're in edit mode (for adding/removing markers)
     */
    fun setEditMode(isEdit: Boolean) {
        isEditMode = isEdit
        
        // Reset drag state when exiting edit mode
        if (!isEdit) {
            isDragging = false
            draggedMarker = null
            dragOffset.set(0f, 0f)
            invalidate()
        }
    }
    
    /**
     * Set a listener for marker drag events
     */
    fun setMarkerDragListener(listener: (Marker, Position) -> Unit) {
        markerDragListener = listener
    }
    
    enum class OverlayType {
        NONE,
        WIFI_HEATMAP,
        BLUETOOTH_HEATMAP,
        ACCESSIBILITY,
        CUSTOM
    }
}
