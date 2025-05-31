package com.example.indoornavigation.ui.map

import android.content.Context
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.painterResource
import androidx.core.content.ContextCompat
import com.example.indoornavigation.R
import com.example.indoornavigation.data.models.Position

/**
 * Jetpack Compose implementation of a floor plan with a red dot marker
 */
@Composable
fun FloorPlanWithDot(
    userPosition: Offset = Offset(400f, 600f),
    floorPlanResId: Int = R.drawable.ground_floor
) {
    // Keep track of zoom and pan
    val scale = remember { mutableStateOf(1f) }
    val offset = remember { mutableStateOf(Offset.Zero) }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clipToBounds()
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    // Update offset for pan
                    offset.value = offset.value.plus(pan)
                    // Update scale for zoom
                    scale.value *= zoom
                    // Limit zoom
                    scale.value = scale.value.coerceIn(0.5f, 5f)
                }
            }
    ) {
        // Floor plan with transformations
        Image(
            painter = painterResource(id = floorPlanResId),
            contentDescription = "Floor Plan",
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = scale.value,
                    scaleY = scale.value,
                    translationX = offset.value.x,
                    translationY = offset.value.y
                )
        )
        
        // User position marker (red dot)
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = scale.value,
                    scaleY = scale.value,
                    translationX = offset.value.x,
                    translationY = offset.value.y
                )
        ) {
            // Draw red dot at user position
            drawCircle(
                color = Color.Red,
                radius = 20f,
                center = userPosition
            )
        }
    }
}

/**
 * Create a Compose view containing the floor plan with a red dot
 * that can be used in a traditional view hierarchy
 */
class ComposeFloorPlanView(context: Context) : LinearLayout(context) {
    
    private val composeView = ComposeView(context).apply {
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
    }
    
    // User position as mutable state to enable reactivity
    private val userPositionX = mutableStateOf(300f)
    private val userPositionY = mutableStateOf(600f)
    
    init {
        // Set up layout parameters
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        
        // Add the compose view to this layout
        addView(composeView)
        
        // Set up the compose content with dynamic position
        composeView.setContent {
            val xPos = remember { userPositionX }
            val yPos = remember { userPositionY }
            
            FloorPlanWithMovingDot(xPos.value, yPos.value)
        }
    }
    
    /**
     * Set the user's position
     */
    fun setUserPosition(position: Position) {
        // Convert real-world position to pixel position and update state
        val pixelX = (position.x * 10).toFloat() // Assume 1 meter = 10 pixels
        val pixelY = (position.y * 10).toFloat()
        
        userPositionX.value = pixelX
        userPositionY.value = pixelY
    }
    
    /**
     * Update the compose content with the current user position
     */
    private fun updateContent() {
        // Not needed as the Composable automatically reacts to state changes
    }
}

/**
 * Composable for a floor plan with a moving dot
 * Takes explicit x and y coordinates that can be updated dynamically
 */
@Composable
fun FloorPlanWithMovingDot(x: Float, y: Float) {
    Box(modifier = Modifier.fillMaxSize()) {
        Image(
            painter = painterResource(id = R.drawable.ground_floor),
            contentDescription = "Floor Plan",
            modifier = Modifier.fillMaxSize()
        )
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                color = Color.Red,
                radius = 20f,
                center = Offset(x = x, y = y)
            )
        }
    }
}
