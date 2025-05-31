package com.example.indoornavigation.ui.map

import android.app.AlertDialog
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.util.AttributeSet
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.widget.EditText
import android.widget.Spinner
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.example.indoornavigation.R
import com.example.indoornavigation.data.models.POICategory
import com.example.indoornavigation.data.models.Position
import com.google.firebase.firestore.FirebaseFirestore

/**
 * POI Mapping Tool
 * 
 * A specialized view for mapping POIs on floor plans.
 * This tool allows users to tap on a floor plan to place POIs,
 * assign names, descriptions, and categories to them,
 * and export them to Firestore or as JSON.
 */
class POIMappingTool(context: Context, attrs: AttributeSet? = null) : FloorPlanView(context, attrs) {
    
    // POI Mapping mode
    private var isMappingModeActive = false
    
    // Store mapped POIs
    data class MappedPOI(
        val id: String,
        val name: String,
        val description: String,
        val category: String,
        val position: Position
    )
    
    private val mappedPOIs = mutableListOf<MappedPOI>()
    
    // Paint for POI markers
    private val poiMarkerPaint = Paint().apply {
        color = Color.RED
        style = Paint.Style.FILL
        alpha = 180
    }
    
    // Paint for POI text
    private val poiTextPaint = Paint().apply {
        color = Color.BLACK
        textSize = 40f
        textAlign = Paint.Align.CENTER
    }
    
    // POI categories
    private val poiCategories = listOf(
        "entrance", "elevator", "stairs", "restroom", 
        "room", "office", "food", "service", "exit",
        "emergency_exit", "lounge", "meeting"
    )
    
    /**
     * Enable or disable POI mapping mode
     */
    fun toggleMappingMode(enabled: Boolean) {
        isMappingModeActive = enabled
        val message = if (enabled) 
            "POI mapping mode enabled. Tap to add POIs." 
        else 
            "POI mapping mode disabled."
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        invalidate()
    }
    
    /**
     * Handle touch events for POI mapping
     */
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (isMappingModeActive && event.action == MotionEvent.ACTION_DOWN) {
            // Convert screen coordinates to floor plan coordinates
            val touchPoint = PointF(event.x, event.y)
            val floorCoordinates = screenToFloorPlanCoordinates(touchPoint)
            
            // Show dialog to enter POI details
            showPOIDetailsDialog(floorCoordinates)
            return true
        }
        // Allow normal touch handling if not in mapping mode
        return super.onTouchEvent(event)
    }
    
    /**
     * Convert screen coordinates to floor plan coordinates
     */
    private fun screenToFloorPlanCoordinates(screenPoint: PointF): PointF {
        // Calculate the floor plan coordinates based on the current view transformation
        // This is a simplified version - adjust based on your FloorPlanView implementation
        val floorX = (screenPoint.x - translateX) / currentScale
        val floorY = (screenPoint.y - translateY) / currentScale
        return PointF(floorX, floorY)
    }
    
    /**
     * Show dialog to enter POI details
     */
    private fun showPOIDetailsDialog(point: PointF) {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_add_poi, null)
        
        // Get references to the input fields
        val nameInput = dialogView.findViewById<EditText>(R.id.poi_name_input)
        val descriptionInput = dialogView.findViewById<EditText>(R.id.poi_description_input)
        val categorySpinner = dialogView.findViewById<Spinner>(R.id.poi_category_spinner)
        
        // Set up the category spinner
        val adapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, poiCategories)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        categorySpinner.adapter = adapter
        
        // Show the dialog
        AlertDialog.Builder(context)
            .setTitle("Add Point of Interest")
            .setView(dialogView)
            .setPositiveButton("Add") { _, _ ->
                val name = nameInput.text.toString().trim()
                val description = descriptionInput.text.toString().trim()
                val category = categorySpinner.selectedItem.toString()
                
                if (name.isNotEmpty()) {
                    addPOI(name, description, category, point)
                } else {
                    Toast.makeText(context, "POI name cannot be empty", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    /**
     * Add a new POI to the list
     */
    private fun addPOI(name: String, description: String, category: String, point: PointF) {
        // Generate ID from name
        val id = name.lowercase().replace(" ", "_").replace(Regex("[^a-z0-9_]"), "")
        
        // Create a unique ID if this one already exists
        var uniqueId = id
        var counter = 1
        while (mappedPOIs.any { it.id == uniqueId }) {
            uniqueId = "${id}_$counter"
            counter++
        }
        
        // Create POI with current floor level
        val poi = MappedPOI(
            id = uniqueId,
            name = name,
            description = description,
            category = category,
            position = Position(point.x.toDouble(), point.y.toDouble(), resolveCurrentFloorForPOI())
        )
        
        // Add to the list
        mappedPOIs.add(poi)
        
        // Log for debugging
        Log.d("POIMappingTool", "Added POI: $name at (${point.x}, ${point.y}) on floor ${resolveCurrentFloorForPOI()}")
        
        // Refresh the view
        invalidate()
    }
    
    /**
     * Get the current floor level
     */
    private fun resolveCurrentFloorForPOI(): Int {
        // This should return the current floor level being viewed
        // Adjust according to your floor plan view implementation
        return super.getCurrentFloorLevel()
    }
    
    /**
     * Draw the mapped POIs on the canvas
     */
    override fun onDraw(canvas: Canvas) {
        // Draw the floor plan first
        super.onDraw(canvas)
        
        // Only draw mapped POIs if mapping mode is active
        if (isMappingModeActive) {
            // Draw each POI marker
            mappedPOIs.filter { it.position.floor == resolveCurrentFloorForPOI() }.forEach { poi ->
                // Convert floor plan coordinates to screen coordinates
                val screenX = poi.position.x.toFloat() * currentScale + translateX
                val screenY = poi.position.y.toFloat() * currentScale + translateY
                
                // Draw marker
                canvas.drawCircle(screenX, screenY, 20f, poiMarkerPaint)
                
                // Draw POI name
                canvas.drawText(poi.name, screenX, screenY - 30f, poiTextPaint)
            }
        }
    }
    
    /**
     * Export POIs to Firestore
     */
    fun exportPOIsToFirestore(floorId: String) {
        val db = FirebaseFirestore.getInstance()
        
        // Get POIs for the current floor
        val poisToExport = mappedPOIs.filter { it.position.floor == resolveCurrentFloorForPOI() }
        
        if (poisToExport.isEmpty()) {
            Toast.makeText(context, "No POIs to export on this floor", Toast.LENGTH_SHORT).show()
            return
        }
        
        // Reference to the floor plan document
        val floorRef = db.collection("floor_plans").document(floorId)
        
        // Export each POI
        poisToExport.forEach { poi ->
            val poiData = hashMapOf(
                "name" to poi.name,
                "description" to poi.description,
                "category" to poi.category,
                "position" to hashMapOf(
                    "x" to poi.position.x,
                    "y" to poi.position.y
                )
            )
            
            // Add to Firestore
            floorRef.collection("pois").document(poi.id)
                .set(poiData)
                .addOnSuccessListener {
                    Log.d("POIMappingTool", "POI ${poi.name} successfully written to Firestore")
                }
                .addOnFailureListener { e ->
                    Log.w("POIMappingTool", "Error writing POI ${poi.name}", e)
                }
        }
        
        Toast.makeText(
            context, 
            "Exporting ${poisToExport.size} POIs to Firestore...", 
            Toast.LENGTH_SHORT
        ).show()
    }
    
    /**
     * Export POIs to log for manual copying
     */
    fun exportPOIsToLog() {
        val poisToExport = mappedPOIs.filter { it.position.floor == resolveCurrentFloorForPOI() }
        
        if (poisToExport.isEmpty()) {
            Toast.makeText(context, "No POIs to export on this floor", Toast.LENGTH_SHORT).show()
            return
        }
        
        Log.d("POIMappingTool", "======= POIs for Floor ${resolveCurrentFloorForPOI()} =======")
        poisToExport.forEach { poi ->
            Log.d("POIMappingTool", """
                Document ID: "${poi.id}"
                Fields:
                - name: "${poi.name}"
                - description: "${poi.description}"
                - category: "${poi.category}"
                - position: Map
                  - x: ${poi.position.x}
                  - y: ${poi.position.y}
                ------------------------------
            """.trimIndent())
        }
        
        Toast.makeText(
            context, 
            "Exported ${poisToExport.size} POIs to log (check Logcat)", 
            Toast.LENGTH_SHORT
        ).show()
    }
    
    /**
     * Clear all mapped POIs on the current floor
     */
    fun clearPOIs() {
        val countBefore = mappedPOIs.size
        mappedPOIs.removeAll { it.position.floor == resolveCurrentFloorForPOI() }
        val removed = countBefore - mappedPOIs.size
        
        Toast.makeText(
            context, 
            "Cleared $removed POIs from floor ${resolveCurrentFloorForPOI()}", 
            Toast.LENGTH_SHORT
        ).show()
        
        invalidate()
    }
}
