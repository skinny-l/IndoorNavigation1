package com.example.indoornavigation.ui.map

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.view.animation.AccelerateDecelerateInterpolator
import com.example.indoornavigation.data.models.Floor
import com.example.indoornavigation.utils.EventBus

/**
 * Manages animated transitions between floors in the map view
 */
class FloorTransitionManager(
    private val mapView: MapView,
    private val floorIndicator: FloorIndicatorView
) {
    private val transitionAnimator = ValueAnimator()
    private var currentFloor: Floor? = null
    
    /**
     * Switch to a target floor with optional animation
     */
    fun switchFloor(targetFloor: Floor, animate: Boolean = true) {
        val currentFloorLocal = currentFloor
        
        if (!animate || currentFloorLocal == null) {
            // Instant switch
            mapView.setFloor(targetFloor)
            floorIndicator.setCurrentFloor(targetFloor.level)
            currentFloor = targetFloor
            EventBus.post(FloorChangedEvent(targetFloor))
            return
        }
        
        // Start animated transition
        val startElevation = currentFloorLocal.level
        val endElevation = targetFloor.level
        val movingUp = endElevation > startElevation
        
        // Setup animator
        transitionAnimator.cancel()
        transitionAnimator.setFloatValues(0f, 1f)
        transitionAnimator.duration = 800
        transitionAnimator.interpolator = AccelerateDecelerateInterpolator()
        
        // Apply crossfade effect
        val startOpacity = 1.0f
        val midOpacity = 0.0f
        
        // Setup the animation listener
        transitionAnimator.addUpdateListener { animator ->
            val fraction = animator.animatedValue as Float
            
            // First half: fade out current floor
            if (fraction < 0.5f) {
                val opacity = startOpacity * (1 - fraction * 2)
                mapView.setFloorOpacity(currentFloorLocal, opacity)
                
                // Show elevation change animation
                val currentY = if (movingUp) {
                    lerp(0f, -20f, fraction * 2)
                } else {
                    lerp(0f, 20f, fraction * 2)
                }
                mapView.setFloorElevation(currentFloorLocal, currentY)
                
                // Update floor indicator at halfway point
                if (fraction >= 0.4f && fraction <= 0.5f) {
                    floorIndicator.setCurrentFloor(targetFloor.level)
                }
            } 
            // Second half: fade in target floor
            else {
                // Switch to target floor at halfway point
                if (fraction <= 0.51f) {
                    mapView.setFloor(targetFloor)
                    mapView.setFloorOpacity(targetFloor, midOpacity)
                    
                    // Set initial elevation
                    val initialY = if (movingUp) {
                        20f
                    } else {
                        -20f
                    }
                    mapView.setFloorElevation(targetFloor, initialY)
                }
                
                val opacity = midOpacity + (startOpacity - midOpacity) * (fraction - 0.5f) * 2
                mapView.setFloorOpacity(targetFloor, opacity)
                
                // Animate to final position
                val currentY = if (movingUp) {
                    lerp(20f, 0f, (fraction - 0.5f) * 2)
                } else {
                    lerp(-20f, 0f, (fraction - 0.5f) * 2)
                }
                mapView.setFloorElevation(targetFloor, currentY)
            }
        }
        
        transitionAnimator.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                // Ensure final state is correct
                mapView.setFloor(targetFloor)
                mapView.setFloorOpacity(targetFloor, 1.0f)
                mapView.setFloorElevation(targetFloor, 0f)
                currentFloor = targetFloor
                EventBus.post(FloorChangedEvent(targetFloor))
            }
        })
        
        transitionAnimator.start()
    }
    
    /**
     * Linear interpolation between two values
     */
    private fun lerp(start: Float, end: Float, fraction: Float): Float {
        return start + (end - start) * fraction
    }
    
    /**
     * Event class for floor change events
     */
    data class FloorChangedEvent(val floor: Floor)
}