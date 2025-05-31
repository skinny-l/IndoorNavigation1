package com.example.indoornavigation.ui.map

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import com.example.indoornavigation.R
import com.example.indoornavigation.data.models.NavigationInstruction
import com.example.indoornavigation.data.models.PointOfInterest
import java.text.DecimalFormat

/**
 * A Waze-like navigation bottom sheet that provides turn-by-turn directions
 * with animated visual cues and expandable details
 */
class NavigationBottomSheet @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : CardView(context, attrs, defStyleAttr) {

    private val directionIcon: ImageView
    private val directionText: TextView
    private val distanceText: TextView
    private val navigationProgress: ProgressBar
    private val etaText: TextView
    private val expandedDetails: View
    private val dragHandle: View
    private val totalDistanceText: TextView
    private val turnsRemainingText: TextView
    private val floorChangesText: TextView
    
    private var expanded = false
    private val arrowAnimator: ValueAnimator
    private val distanceFormat = DecimalFormat("#0")
    
    // Nearby POIs to use in context-aware instructions
    private var nearbyPois: List<PointOfInterest> = emptyList()
    
    init {
        // Inflate the layout (already done in XML)
        View.inflate(context, R.layout.layout_navigation_bottom_sheet, this)
        
        // Get references to the views
        directionIcon = findViewById(R.id.direction_icon)
        directionText = findViewById(R.id.direction_text)
        distanceText = findViewById(R.id.distance_text)
        navigationProgress = findViewById(R.id.navigation_progress)
        etaText = findViewById(R.id.eta_text)
        expandedDetails = findViewById(R.id.expanded_details)
        dragHandle = findViewById(R.id.drag_handle)
        totalDistanceText = findViewById(R.id.total_distance_text)
        turnsRemainingText = findViewById(R.id.turns_remaining_text)
        floorChangesText = findViewById(R.id.floor_changes_text)
        
        // Setup arrow animation for visual cues
        arrowAnimator = ValueAnimator.ofFloat(1f, 1.2f, 1f).apply {
            duration = 1500
            repeatCount = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { animation ->
                val scale = animation.animatedValue as Float
                directionIcon.scaleX = scale
                directionIcon.scaleY = scale
            }
        }
        
        // Setup click listener for expanding/collapsing details
        setOnClickListener {
            toggleExpansion()
        }
    }

    /**
     * Update the instruction displayed in the bottom sheet
     */
    fun updateInstruction(instruction: NavigationInstruction) {
        // Set direction text with context-aware information
        val contextAwareInstruction = enrichInstructionWithContext(instruction)
        directionText.text = contextAwareInstruction
        
        // Set distance text
        val formattedDistance = distanceFormat.format(instruction.distanceMeters)
        distanceText.text = "In $formattedDistance meters"
        
        // Get the icon for backward compatibility
        val icon = instruction.getIconFromTypeAndDirection()
        
        // Set icon based on instruction type
        when (icon) {
            NavigationInstruction.Icon.STRAIGHT -> {
                directionIcon.setImageResource(android.R.drawable.ic_media_play)
                directionIcon.rotation = 90f
                updateBackgroundColor(R.color.instructionBackground)
                stopArrowAnimation()
            }
            NavigationInstruction.Icon.RIGHT -> {
                directionIcon.setImageResource(android.R.drawable.ic_media_play)
                directionIcon.rotation = 0f
                updateBackgroundColor(R.color.instructionBackground)
                startArrowAnimation()
            }
            NavigationInstruction.Icon.LEFT -> {
                directionIcon.setImageResource(android.R.drawable.ic_media_play)
                directionIcon.rotation = 180f
                updateBackgroundColor(R.color.instructionBackground)
                startArrowAnimation()
            }
            NavigationInstruction.Icon.SHARP_RIGHT -> {
                directionIcon.setImageResource(android.R.drawable.ic_media_play)
                directionIcon.rotation = -45f
                updateBackgroundColor(R.color.instructionBackground)
                startArrowAnimation()
            }
            NavigationInstruction.Icon.SHARP_LEFT -> {
                directionIcon.setImageResource(android.R.drawable.ic_media_play)
                directionIcon.rotation = 225f
                updateBackgroundColor(R.color.instructionBackground)
                startArrowAnimation()
            }
            NavigationInstruction.Icon.SLIGHT_RIGHT -> {
                directionIcon.setImageResource(android.R.drawable.ic_media_play)
                directionIcon.rotation = 45f
                updateBackgroundColor(R.color.instructionBackground)
                startArrowAnimation()
            }
            NavigationInstruction.Icon.SLIGHT_LEFT -> {
                directionIcon.setImageResource(android.R.drawable.ic_media_play)
                directionIcon.rotation = 135f
                updateBackgroundColor(R.color.instructionBackground)
                startArrowAnimation()
            }
            NavigationInstruction.Icon.U_TURN -> {
                directionIcon.setImageResource(android.R.drawable.ic_menu_rotate)
                updateBackgroundColor(R.color.instructionBackground)
                startArrowAnimation()
            }
            NavigationInstruction.Icon.GO_UP -> {
                directionIcon.setImageResource(R.drawable.ic_arrow_up)
                updateBackgroundColor(R.color.floorChangeBackground)
                startArrowAnimation()
            }
            NavigationInstruction.Icon.GO_DOWN -> {
                directionIcon.setImageResource(R.drawable.ic_arrow_down)
                updateBackgroundColor(R.color.floorChangeBackground)
                startArrowAnimation()
            }
            NavigationInstruction.Icon.DESTINATION -> {
                directionIcon.setImageResource(R.drawable.ic_end_marker)
                updateBackgroundColor(R.color.destinationBackground)
                stopArrowAnimation()
            }
            NavigationInstruction.Icon.START -> {
                directionIcon.setImageResource(R.drawable.ic_start_marker)
                updateBackgroundColor(R.color.instructionBackground)
                stopArrowAnimation()
            }
            NavigationInstruction.Icon.CONTINUE -> {
                directionIcon.setImageResource(android.R.drawable.ic_media_play)
                directionIcon.rotation = 90f
                updateBackgroundColor(R.color.instructionBackground)
                stopArrowAnimation()
            }
            NavigationInstruction.Icon.ERROR -> {
                directionIcon.setImageResource(android.R.drawable.ic_dialog_alert)
                updateBackgroundColor(R.color.colorAccent)
                stopArrowAnimation()
            }
        }
        
        // Calculate ETA based on average walking speed (~ 4 km/h)
        val walkingSpeedMPS = 1.1 // meters per second
        val etaSeconds = (instruction.distanceMeters / walkingSpeedMPS).toInt()
        val etaMinutes = etaSeconds / 60
        val etaRemainderSeconds = etaSeconds % 60
        
        if (etaMinutes > 0) {
            etaText.text = "ETA: ${etaMinutes}m ${etaRemainderSeconds}s"
        } else {
            etaText.text = "ETA: ${etaRemainderSeconds}s"
        }
    }
    
    /**
     * Update the progress bar showing navigation progress
     */
    fun updateProgress(progress: Int) {
        navigationProgress.progress = progress
    }
    
    /**
     * Update route overview information
     */
    fun updateRouteOverview(totalDistance: Double, turnsRemaining: Int, floorChanges: Int) {
        totalDistanceText.text = "Total distance: ${distanceFormat.format(totalDistance)} meters"
        turnsRemainingText.text = "Turns remaining: $turnsRemaining"
        floorChangesText.text = "Floor changes: $floorChanges"
    }
    
    /**
     * Set nearby POIs for context-aware instructions
     */
    fun setNearbyPois(pois: List<PointOfInterest>) {
        nearbyPois = pois
    }
    
    /**
     * Toggle expanded/collapsed state
     */
    private fun toggleExpansion() {
        expanded = !expanded
        expandedDetails.visibility = if (expanded) View.VISIBLE else View.GONE
    }
    
    /**
     * Start arrow animation to draw attention to turn instructions
     */
    private fun startArrowAnimation() {
        if (!arrowAnimator.isRunning) {
            arrowAnimator.start()
        }
    }
    
    /**
     * Stop arrow animation
     */
    private fun stopArrowAnimation() {
        if (arrowAnimator.isRunning) {
            arrowAnimator.cancel()
            directionIcon.scaleX = 1f
            directionIcon.scaleY = 1f
        }
    }
    
    /**
     * Update background color based on instruction type
     */
    private fun updateBackgroundColor(colorResId: Int) {
        setCardBackgroundColor(ContextCompat.getColor(context, colorResId))
        
        // Also update text colors for better visibility
        val textColor = ContextCompat.getColor(context, R.color.colorOnInstruction)
        directionText.setTextColor(textColor)
        distanceText.setTextColor(textColor)
        etaText.setTextColor(textColor)
    }
    
    /**
     * Enhance instruction with context about nearby landmarks
     */
    private fun enrichInstructionWithContext(instruction: NavigationInstruction): String {
        // Use the instruction text or fallback to the original instruction if text is empty
        var enhancedInstruction = instruction.text
        
        // Check if we should enrich this instruction with context
        val isDestination = instruction.type == NavigationInstruction.InstructionType.DESTINATION
        val isFloorChange = instruction.type == NavigationInstruction.InstructionType.FLOOR_CHANGE
        
        if (nearbyPois.isNotEmpty() && !isFloorChange && !isDestination) {
            // Find nearest POI within 10 meters
            val nearestPoi = nearbyPois.minByOrNull { poi ->
                // Calculate straight-line distance to POI for simplicity
                val dx = poi.position.x - 0 // Would need user's position here
                val dy = poi.position.y - 0
                dx * dx + dy * dy
            }
            
            // Add context if a nearby POI exists
            nearestPoi?.let {
                if (!enhancedInstruction.contains(it.name)) {
                    // Get icon for backward compatibility
                    val icon = instruction.getIconFromTypeAndDirection()
                    
                    enhancedInstruction = when (icon) {
                        NavigationInstruction.Icon.RIGHT, 
                        NavigationInstruction.Icon.SLIGHT_RIGHT,
                        NavigationInstruction.Icon.SHARP_RIGHT -> {
                            "Turn right toward ${it.name}"
                        }
                        NavigationInstruction.Icon.LEFT,
                        NavigationInstruction.Icon.SLIGHT_LEFT,
                        NavigationInstruction.Icon.SHARP_LEFT -> {
                            "Turn left toward ${it.name}"
                        }
                        NavigationInstruction.Icon.STRAIGHT,
                        NavigationInstruction.Icon.CONTINUE -> {
                            "Continue toward ${it.name}"
                        }
                        else -> enhancedInstruction
                    }
                }
            }
        }
        
        // Special handling for floor changes
        if (isFloorChange) {
            enhancedInstruction = if (instruction.direction == NavigationInstruction.Direction.UP) {
                "Take stairs/elevator up"
            } else {
                "Take stairs/elevator down"
            }
        }
        
        return enhancedInstruction
    }
    
    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopArrowAnimation()
    }
}