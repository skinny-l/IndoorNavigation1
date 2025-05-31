package com.example.indoornavigation.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PaintingStyle
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import com.example.indoornavigation.R
import com.example.indoornavigation.data.models.ManagedBeacon
import com.example.indoornavigation.data.models.Position

/**
 * A composable that displays a floor plan with the user's position and beacons
 */
@Composable
fun FloorPlanVisualization(
    floorPlan: Int = R.drawable.sample_floor_plan, 
    userPosition: Position,
    beacons: List<ManagedBeacon>,
    accuracy: Double
) {
    // State for zoom and pan
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    
    // State for the floor plan dimensions in meters
    val floorPlanWidthMeters = remember { 30.0 } // Approximate width in meters
    val floorPlanHeightMeters = remember { 20.0 } // Approximate height in meters
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clipToBounds()
    ) {
        // Zoomable and pannable container
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offset.x,
                    translationY = offset.y
                )
                .pointerInput(Unit) {
                    detectTransformGestures { centroid, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(0.5f, 3f)
                        
                        // Adjust offset based on pan and zoom
                        val newOffset = offset + pan
                        offset = newOffset
                    }
                }
        ) {
            // Floor plan image
            Image(
                painter = painterResource(id = floorPlan),
                contentDescription = "Floor Plan",
                modifier = Modifier.fillMaxSize()
            )
            
            // Draw beacons and user position
            Canvas(modifier = Modifier.fillMaxSize()) {
                val pixelPerMeter = size.minDimension / maxOf(floorPlanWidthMeters, floorPlanHeightMeters).toFloat()
                
                // Draw beacons
                beacons.forEach { beacon ->
                    if (beacon.floor == userPosition.floor) {
                        drawBeacon(
                            x = (beacon.x * pixelPerMeter).toFloat(),
                            y = (beacon.y * pixelPerMeter).toFloat(),
                            name = beacon.name
                        )
                    }
                }
                
                // Draw user position with accuracy circle
                val userX = (userPosition.x * pixelPerMeter).toFloat()
                val userY = (userPosition.y * pixelPerMeter).toFloat()
                val accuracyRadius = (accuracy * pixelPerMeter).toFloat()
                
                // Accuracy circle
                drawCircle(
                    color = Color.Blue.copy(alpha = 0.2f),
                    radius = accuracyRadius,
                    center = Offset(userX, userY)
                )
                
                // User marker
                drawCircle(
                    color = Color.Blue,
                    radius = 15f,
                    center = Offset(userX, userY)
                )
                
                drawCircle(
                    color = Color.White,
                    radius = 7f,
                    center = Offset(userX, userY)
                )
            }
        }
    }
}

/**
 * Draw a beacon marker on the canvas
 */
private fun DrawScope.drawBeacon(x: Float, y: Float, name: String) {
    // Draw beacon marker (red circle)
    drawCircle(
        color = Color.Red,
        radius = 12f,
        center = Offset(x, y),
        style = Stroke(width = 2f)
    )
    
    // Draw beacon center
    drawCircle(
        color = Color.Red,
        radius = 4f,
        center = Offset(x, y)
    )
    
    // In a more complete implementation, you'd also draw the beacon name
    // This would require using a Text composable in an overlay
}