package com.example.indoornavigation.data.navigation

import com.example.indoornavigation.data.models.FloorTransitionType
import com.example.indoornavigation.data.models.NavNodeEnhanced
import com.example.indoornavigation.data.models.NavigationInstruction
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * Generates turn-by-turn navigation instructions from a path
 */
class NavigationInstructionGenerator(private val navigationService: EnhancedNavigationService) {

    /**
     * Generate navigation instructions from a path of nodes
     * @param path List of navigation nodes representing the path
     * @return List of instructions for navigation
     */
    fun generateInstructions(path: List<NavNodeEnhanced>): List<NavigationInstruction> {
        val instructions = mutableListOf<NavigationInstruction>()
        
        if (path.isEmpty()) {
            return instructions
        }
        
        // Add starting instruction
        instructions.add(
            NavigationInstruction(
                type = NavigationInstruction.InstructionType.START,
                direction = NavigationInstruction.Direction.FORWARD,
                distanceMeters = 0f,
                text = "Start navigation",
                nodeId = path[0].id
            )
        )
        
        // Analyze path segments
        for (i in 0 until path.size - 1) {
            val current = path[i]
            val next = path[i + 1]
            
            // Check if this is a floor transition
            if (current.floorId != next.floorId) {
                generateFloorTransitionInstruction(current, next, instructions)
                continue
            }
            
            // Check for significant direction changes if we have at least 3 nodes
            if (i < path.size - 2) {
                val nextNext = path[i + 2]
                
                // Only generate turn instructions if all nodes are on the same floor
                if (current.floorId == next.floorId && next.floorId == nextNext.floorId) {
                    generateDirectionInstruction(current, next, nextNext, instructions)
                }
            }
            
            // Calculate distance between nodes
            val connection = current.connections.find { it.targetNodeId == next.id }
            val distance = connection?.distance ?: calculateDistance(current, next)
            
            // Add distance instruction (only for longer segments)
            if (distance > 5.0f && i > 0) {
                instructions.add(
                    NavigationInstruction(
                        type = NavigationInstruction.InstructionType.CONTINUE,
                        direction = NavigationInstruction.Direction.FORWARD,
                        distanceMeters = distance,
                        text = "Continue for ${formatDistance(distance)}",
                        nodeId = next.id
                    )
                )
            }
        }
        
        // Add destination instruction
        instructions.add(
            NavigationInstruction(
                type = NavigationInstruction.InstructionType.DESTINATION,
                direction = NavigationInstruction.Direction.FORWARD,
                distanceMeters = 0f,
                text = "You have reached your destination",
                nodeId = path.last().id
            )
        )
        
        return instructions
    }
    
    /**
     * Create an instruction for a floor transition
     */
    private fun generateFloorTransitionInstruction(
        current: NavNodeEnhanced, 
        next: NavNodeEnhanced,
        instructions: MutableList<NavigationInstruction>
    ) {
        val connection = current.connections.find { it.targetNodeId == next.id }
        val transitionType = connection?.transitionType ?: FloorTransitionType.STAIRS
        val floorDifference = next.floorId - current.floorId
        val direction = if (floorDifference > 0) {
            NavigationInstruction.Direction.UP
        } else {
            NavigationInstruction.Direction.DOWN
        }
        
        val directionText = if (direction == NavigationInstruction.Direction.UP) "up" else "down"
        
        val instructionText = when (transitionType) {
            FloorTransitionType.ELEVATOR -> "Take the elevator $directionText to floor ${next.floorId}"
            FloorTransitionType.ESCALATOR -> "Take the escalator $directionText to floor ${next.floorId}"
            FloorTransitionType.STAIRS -> "Take the stairs $directionText to floor ${next.floorId}"
        }
        
        instructions.add(
            NavigationInstruction(
                type = NavigationInstruction.InstructionType.FLOOR_CHANGE,
                direction = direction,
                distanceMeters = connection?.distance ?: 5f,
                text = instructionText,
                nodeId = current.id
            )
        )
    }
    
    /**
     * Create an instruction for a change in direction
     */
    private fun generateDirectionInstruction(
        current: NavNodeEnhanced,
        middle: NavNodeEnhanced,
        next: NavNodeEnhanced,
        instructions: MutableList<NavigationInstruction>
    ) {
        // Calculate turn angle
        val angle = calculateTurnAngle(
            current.position.x, current.position.y,
            middle.position.x, middle.position.y,
            next.position.x, next.position.y
        )
        
        // Determine turn direction based on angle
        val turnDirection = when {
            angle > 150 -> NavigationInstruction.Direction.TURN_AROUND
            angle > 45 -> NavigationInstruction.Direction.RIGHT
            angle < -45 -> NavigationInstruction.Direction.LEFT
            angle > 20 -> NavigationInstruction.Direction.SLIGHT_RIGHT
            angle < -20 -> NavigationInstruction.Direction.SLIGHT_LEFT
            else -> NavigationInstruction.Direction.FORWARD
        }
        
        // Only add turn instructions for significant turns
        if (turnDirection != NavigationInstruction.Direction.FORWARD) {
            instructions.add(
                NavigationInstruction(
                    type = NavigationInstruction.InstructionType.TURN,
                    direction = turnDirection,
                    distanceMeters = 0f,
                    text = getDirectionText(turnDirection),
                    nodeId = middle.id
                )
            )
        }
    }
    
    /**
     * Get human-readable text for a direction
     */
    private fun getDirectionText(direction: NavigationInstruction.Direction): String {
        return when (direction) {
            NavigationInstruction.Direction.FORWARD -> "Continue straight"
            NavigationInstruction.Direction.LEFT -> "Turn left"
            NavigationInstruction.Direction.RIGHT -> "Turn right"
            NavigationInstruction.Direction.SLIGHT_LEFT -> "Bear slightly left"
            NavigationInstruction.Direction.SLIGHT_RIGHT -> "Bear slightly right"
            NavigationInstruction.Direction.TURN_AROUND -> "Make a U-turn"
            NavigationInstruction.Direction.UP -> "Go up"
            NavigationInstruction.Direction.DOWN -> "Go down"
        }
    }
    
    /**
     * Calculate the turn angle between three points
     * Returns angle in degrees, positive for right turns, negative for left turns
     */
    private fun calculateTurnAngle(x1: Double, y1: Double, x2: Double, y2: Double, x3: Double, y3: Double): Double {
        // Calculate vectors
        val vector1x = x2 - x1
        val vector1y = y2 - y1
        val vector2x = x3 - x2
        val vector2y = y3 - y2
        
        // Calculate angle between vectors
        val dotProduct = vector1x * vector2x + vector1y * vector2y
        val crossProduct = vector1x * vector2y - vector1y * vector2x
        
        val angle = Math.toDegrees(atan2(crossProduct, dotProduct))
        
        return angle
    }
    
    /**
     * Calculate distance between two nodes
     */
    private fun calculateDistance(node1: NavNodeEnhanced, node2: NavNodeEnhanced): Float {
        val dx = node2.position.x - node1.position.x
        val dy = node2.position.y - node1.position.y
        return sqrt(dx * dx + dy * dy).toFloat()
    }
    
    /**
     * Format distance for human-readable output
     */
    private fun formatDistance(meters: Float): String {
        return when {
            meters < 10 -> "${meters.toInt()} meters"
            meters < 100 -> "${(meters / 5).toInt() * 5} meters"
            else -> "${(meters / 10).toInt() * 10} meters"
        }
    }
}