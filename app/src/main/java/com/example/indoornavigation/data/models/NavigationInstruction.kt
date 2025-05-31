package com.example.indoornavigation.data.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Represents a navigation instruction to guide the user
 */
@Parcelize
data class NavigationInstruction(
    val type: InstructionType,
    val distanceMeters: Float,
    val direction: Direction,
    val text: String,
    val nodeId: String,
    val icon: Int? = null,
    // Backward compatibility fields
    val instruction: String = text,
    val distance: Double = distanceMeters.toDouble(),
    val turnAngle: Double = 0.0,
    val isFloorChange: Boolean = type == InstructionType.FLOOR_CHANGE
) : Parcelable {
    enum class InstructionType {
        START,
        CONTINUE,
        TURN,
        FLOOR_CHANGE,
        DESTINATION
    }
    
    enum class Direction {
        FORWARD,
        LEFT,
        RIGHT,
        SLIGHT_LEFT,
        SLIGHT_RIGHT,
        TURN_AROUND,
        UP,
        DOWN
    }
    
    // Backward compatibility for old code
    enum class Icon {
        STRAIGHT,
        LEFT,
        RIGHT,
        SLIGHT_LEFT,
        SLIGHT_RIGHT,
        SHARP_LEFT,
        SHARP_RIGHT,
        U_TURN,
        GO_UP,
        GO_DOWN,
        DESTINATION,
        START,
        CONTINUE,
        ERROR
    }
    
    // Helper method to convert from type+direction to icon for backward compatibility
    fun getIconFromTypeAndDirection(): Icon {
        return when (type) {
            InstructionType.START -> Icon.START
            InstructionType.DESTINATION -> Icon.DESTINATION
            InstructionType.CONTINUE -> Icon.CONTINUE
            InstructionType.FLOOR_CHANGE -> {
                when (direction) {
                    Direction.UP -> Icon.GO_UP
                    Direction.DOWN -> Icon.GO_DOWN
                    else -> Icon.STRAIGHT
                }
            }
            InstructionType.TURN -> {
                when (direction) {
                    Direction.LEFT -> Icon.LEFT
                    Direction.RIGHT -> Icon.RIGHT
                    Direction.SLIGHT_LEFT -> Icon.SLIGHT_LEFT
                    Direction.SLIGHT_RIGHT -> Icon.SLIGHT_RIGHT
                    Direction.TURN_AROUND -> Icon.U_TURN
                    else -> Icon.STRAIGHT
                }
            }
        }
    }
}