package com.example.indoornavigation.utils

import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import com.example.indoornavigation.data.models.Position
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Analyzes floor plan images to detect walls, corridors, and obstacles
 * for pixel-perfect navigation
 */
class FloorPlanAnalyzer {
    
    data class WallSegment(
        val startX: Double,
        val startY: Double,
        val endX: Double,
        val endY: Double
    )
    
    data class NavigableArea(
        val centerX: Double,
        val centerY: Double,
        val width: Double,
        val height: Double,
        val type: AreaType // corridor, room, intersection
    )
    
    enum class AreaType {
        CORRIDOR, ROOM, INTERSECTION, STAIRWAY, ELEVATOR_AREA
    }
    
    private val detectedWalls = mutableListOf<WallSegment>()
    private val navigableAreas = mutableListOf<NavigableArea>()
    
    /**
     * Analyze floor plan image to detect walls and navigable areas
     */
    fun analyzeFloorPlan(floorPlanBitmap: Bitmap): FloorPlanAnalysisResult {
        Log.d("FloorPlanAnalyzer", "Starting floor plan analysis...")
        
        detectedWalls.clear()
        navigableAreas.clear()
        
        // Step 1: Convert to grayscale and detect edges
        val edgeMap = detectEdges(floorPlanBitmap)
        
        // Step 2: Find wall segments using line detection
        detectWallSegments(edgeMap)
        
        // Step 3: Identify navigable corridors and rooms
        identifyNavigableAreas(floorPlanBitmap)
        
        // Step 4: Generate navigation mesh
        val navigationMesh = generateNavigationMesh()
        
        Log.d("FloorPlanAnalyzer", "Analysis complete: ${detectedWalls.size} walls, ${navigableAreas.size} areas")
        
        return FloorPlanAnalysisResult(
            walls = detectedWalls.toList(),
            navigableAreas = navigableAreas.toList(),
            navigationMesh = navigationMesh
        )
    }
    
    /**
     * Detect edges in the floor plan using simple edge detection
     */
    private fun detectEdges(bitmap: Bitmap): Array<IntArray> {
        val width = bitmap.width
        val height = bitmap.height
        val edgeMap = Array(height) { IntArray(width) }
        
        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                // Get surrounding pixels
                val center = getGrayValue(bitmap.getPixel(x, y))
                val left = getGrayValue(bitmap.getPixel(x - 1, y))
                val right = getGrayValue(bitmap.getPixel(x + 1, y))
                val top = getGrayValue(bitmap.getPixel(x, y - 1))
                val bottom = getGrayValue(bitmap.getPixel(x, y + 1))
                
                // Simple edge detection (Sobel-like)
                val gradientX = abs(right - left)
                val gradientY = abs(bottom - top)
                val gradient = sqrt((gradientX * gradientX + gradientY * gradientY).toDouble()).toInt()
                
                edgeMap[y][x] = if (gradient > 50) 255 else 0 // Threshold for edge
            }
        }
        
        return edgeMap
    }
    
    /**
     * Convert color pixel to grayscale value
     */
    private fun getGrayValue(pixel: Int): Int {
        val r = Color.red(pixel)
        val g = Color.green(pixel)
        val b = Color.blue(pixel)
        return (0.299 * r + 0.587 * g + 0.114 * b).toInt()
    }
    
    /**
     * Detect wall segments from edge map
     */
    private fun detectWallSegments(edgeMap: Array<IntArray>) {
        val height = edgeMap.size
        val width = edgeMap[0].size
        
        // Simplified wall detection - look for continuous edge lines
        for (y in 0 until height step 5) { // Sample every 5 pixels for performance
            var lineStart = -1
            for (x in 0 until width) {
                if (edgeMap[y][x] > 0) {
                    if (lineStart == -1) lineStart = x
                } else {
                    if (lineStart != -1 && x - lineStart > 20) { // Minimum wall length
                        detectedWalls.add(WallSegment(
                            startX = lineStart.toDouble(),
                            startY = y.toDouble(),
                            endX = x.toDouble(),
                            endY = y.toDouble()
                        ))
                    }
                    lineStart = -1
                }
            }
        }
        
        // Vertical walls
        for (x in 0 until width step 5) {
            var lineStart = -1
            for (y in 0 until height) {
                if (edgeMap[y][x] > 0) {
                    if (lineStart == -1) lineStart = y
                } else {
                    if (lineStart != -1 && y - lineStart > 20) {
                        detectedWalls.add(WallSegment(
                            startX = x.toDouble(),
                            startY = lineStart.toDouble(),
                            endX = x.toDouble(),
                            endY = y.toDouble()
                        ))
                    }
                    lineStart = -1
                }
            }
        }
    }
    
    /**
     * Identify navigable areas like corridors and rooms
     */
    private fun identifyNavigableAreas(bitmap: Bitmap) {
        val width = bitmap.width
        val height = bitmap.height
        
        // Look for white/light areas (navigable spaces)
        for (y in 0 until height step 20) {
            for (x in 0 until width step 20) {
                val pixel = bitmap.getPixel(x, y)
                val brightness = getGrayValue(pixel)
                
                if (brightness > 200) { // Light area = navigable
                    // Check area size
                    val areaSize = measureNavigableArea(bitmap, x, y)
                    if (areaSize.first > 10 && areaSize.second > 10) {
                        val areaType = classifyArea(areaSize.first, areaSize.second)
                        navigableAreas.add(NavigableArea(
                            centerX = x.toDouble(),
                            centerY = y.toDouble(),
                            width = areaSize.first.toDouble(),
                            height = areaSize.second.toDouble(),
                            type = areaType
                        ))
                    }
                }
            }
        }
    }
    
    /**
     * Measure the size of a navigable area
     */
    private fun measureNavigableArea(bitmap: Bitmap, startX: Int, startY: Int): Pair<Int, Int> {
        val width = bitmap.width
        val height = bitmap.height
        
        var maxWidth = 0
        var maxHeight = 0
        
        // Measure width
        for (x in startX until width) {
            val pixel = bitmap.getPixel(x, startY)
            if (getGrayValue(pixel) > 200) {
                maxWidth = x - startX
            } else break
        }
        
        // Measure height
        for (y in startY until height) {
            val pixel = bitmap.getPixel(startX, y)
            if (getGrayValue(pixel) > 200) {
                maxHeight = y - startY
            } else break
        }
        
        return Pair(maxWidth, maxHeight)
    }
    
    /**
     * Classify area type based on dimensions
     */
    private fun classifyArea(width: Int, height: Int): AreaType {
        val aspectRatio = maxOf(width, height).toDouble() / minOf(width, height).toDouble()
        
        return when {
            aspectRatio > 3.0 -> AreaType.CORRIDOR // Long and narrow
            width > 100 && height > 100 -> AreaType.ROOM // Large area
            aspectRatio < 1.5 -> AreaType.INTERSECTION // Square-ish
            else -> AreaType.CORRIDOR
        }
    }
    
    /**
     * Generate navigation mesh from detected areas
     */
    private fun generateNavigationMesh(): List<Position> {
        val meshPoints = mutableListOf<Position>()
        
        // Create navigation points in each navigable area
        navigableAreas.forEach { area ->
            when (area.type) {
                AreaType.CORRIDOR -> {
                    // Add points along corridor centerline
                    val points = if (area.width > area.height) {
                        // Horizontal corridor
                        val step = area.width / 5.0
                        (0..4).map { i ->
                            Position(
                                x = area.centerX - area.width/2 + i * step,
                                y = area.centerY,
                                floor = 0
                            )
                        }
                    } else {
                        // Vertical corridor
                        val step = area.height / 5.0
                        (0..4).map { i ->
                            Position(
                                x = area.centerX,
                                y = area.centerY - area.height/2 + i * step,
                                floor = 0
                            )
                        }
                    }
                    meshPoints.addAll(points)
                }
                AreaType.ROOM -> {
                    // Add center point for rooms
                    meshPoints.add(Position(area.centerX, area.centerY, 0))
                }
                AreaType.INTERSECTION -> {
                    // Add center point with connections
                    meshPoints.add(Position(area.centerX, area.centerY, 0))
                }
                else -> {
                    meshPoints.add(Position(area.centerX, area.centerY, 0))
                }
            }
        }
        
        return meshPoints
    }
    
    /**
     * Check if a line path intersects with any wall
     */
    fun checkPathClearance(start: Position, end: Position): Boolean {
        for (wall in detectedWalls) {
            if (lineIntersectsWall(start, end, wall)) {
                return false // Path blocked by wall
            }
        }
        return true // Path is clear
    }
    
    /**
     * Check if a line intersects with a wall segment
     */
    private fun lineIntersectsWall(start: Position, end: Position, wall: WallSegment): Boolean {
        // Simple line intersection algorithm
        val x1 = start.x
        val y1 = start.y
        val x2 = end.x
        val y2 = end.y
        val x3 = wall.startX
        val y3 = wall.startY
        val x4 = wall.endX
        val y4 = wall.endY
        
        val denom = (x1 - x2) * (y3 - y4) - (y1 - y2) * (x3 - x4)
        if (abs(denom) < 1e-10) return false // Lines are parallel
        
        val t = ((x1 - x3) * (y3 - y4) - (y1 - y3) * (x3 - x4)) / denom
        val u = -((x1 - x2) * (y1 - y3) - (y1 - y2) * (x1 - x3)) / denom
        
        return t >= 0 && t <= 1 && u >= 0 && u <= 1
    }
    
    data class FloorPlanAnalysisResult(
        val walls: List<WallSegment>,
        val navigableAreas: List<NavigableArea>,
        val navigationMesh: List<Position>
    )
}