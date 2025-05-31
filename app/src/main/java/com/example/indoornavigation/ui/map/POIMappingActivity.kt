package com.example.indoornavigation.ui.map

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.indoornavigation.R
import com.example.indoornavigation.data.models.Position
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.firestore.FirebaseFirestore
import java.text.DecimalFormat
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Color

/**
 * POI Mapping Activity
 *
 * This activity allows users to map points of interest (POIs) on floor plans.
 * Users can tap on the floor plan to place POIs, name them, and export them to Firestore.
 */
class POIMappingActivity : AppCompatActivity() {

    private lateinit var floorPlanView: FloorPlanView
    private lateinit var coordinatesText: TextView
    private lateinit var floorLevelText: TextView
    private lateinit var addButton: FloatingActionButton
    
    // POI Mapping mode
    private var mappingModeActive = false
    
    // Store mapped POIs
    data class MappedPOI(
        val id: String,
        val name: String,
        val description: String,
        val category: String,
        val position: Position
    )
    
    private val mappedPOIs = mutableListOf<MappedPOI>()
    
    // POI categories
    private val poiCategories = listOf(
        "entrance", "elevator", "stairs", "restroom", 
        "room", "office", "food", "service", "exit",
        "emergency_exit", "lounge", "meeting"
    )
    
    // Current floor ID (used for Firestore export)
    private var currentFloorId = "ground_floor"
    
    // Decimal formatter for coordinates
    private val decimalFormat = DecimalFormat("0.00")
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_poi_mapping)
        
        // Set up action bar
        supportActionBar?.setTitle("POI Mapping Tool")
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        
        // Initialize views
        floorPlanView = findViewById(R.id.floor_plan_view)
        coordinatesText = findViewById(R.id.coordinates_text)
        floorLevelText = findViewById(R.id.floor_level_text)
        addButton = findViewById(R.id.add_poi_button)
        
        // Set up the floor plan
        setupFloorPlan()
        
        // Set up touch listener for coordinates display
        floorPlanView.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN || event.action == MotionEvent.ACTION_MOVE) {
                // Just display raw coordinates for now
                // In a real implementation, you'd convert these to floor plan coordinates
                updateCoordinatesDisplay(event.x, event.y)
                
                // If in mapping mode and this is a tap (not a move)
                if (mappingModeActive && event.action == MotionEvent.ACTION_DOWN) {
                    // Convert screen tap to actual floor plan meter coordinates
                    val floorPlanMeterCoordinates = floorPlanView.screenToFloorPlanCoordinates(event.x, event.y)
                    if (floorPlanMeterCoordinates != null) {
                        showPOIDetailsDialog(floorPlanMeterCoordinates.first.toFloat(), floorPlanMeterCoordinates.second.toFloat())
                    } else {
                        Toast.makeText(this, "Tapped outside floor plan bounds.", Toast.LENGTH_SHORT).show()
                    }
                    return@setOnTouchListener true
                }
            }
            // Allow the view to handle the touch event normally
            false
        }
        
        // Set up add button
        addButton.setOnClickListener {
            // Toggle mapping mode
            mappingModeActive = !mappingModeActive
            updateMappingModeUI()
        }
        
        // Set up floor level text
        floorLevelText.text = "Floor: 0" // Default to ground floor
        
        // Update the mapping status text
        val mappingStatus = findViewById<TextView>(R.id.mapping_status)
        mappingStatus.text = "Mapping: OFF"
    }
    
    /**
     * Set up the floor plan view
     */
    private fun setupFloorPlan() {
        // Load floor plans (typically done with Firebase, but using local resources here)
        // For a real implementation, you'd use your FloorPlanProvider to load the floor plans
        
        // For now, let's load a default floor plan (e.g., ground floor)
        floorPlanView.setFloorPlan(0) // 0 for ground floor
        
        // Set building dimensions (CS1 building dimensions)
        floorPlanView.setBuildingDimensions(75.0, 75.0)
        
        // Center the floor plan initially
        floorPlanView.post { // Ensure the view is laid out before centering
            floorPlanView.resetTransformation()
        }
    }
    
    /**
     * Update the coordinates display
     */
    private fun updateCoordinatesDisplay(x: Float, y: Float) {
        coordinatesText.text = "X: ${decimalFormat.format(x)}, Y: ${decimalFormat.format(y)}"
    }
    
    /**
     * Update the UI based on mapping mode
     */
    private fun updateMappingModeUI() {
        val mappingStatus = findViewById<TextView>(R.id.mapping_status)
        
        if (mappingModeActive) {
            addButton.setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
            mappingStatus.text = "Mapping: ON"
            mappingStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark))
            Toast.makeText(this, "POI mapping mode activated. Tap on the floor plan to add POIs.", Toast.LENGTH_SHORT).show()
        } else {
            addButton.setImageResource(android.R.drawable.ic_menu_add)
            mappingStatus.text = "Mapping: OFF"
            mappingStatus.setTextColor(ContextCompat.getColor(this, android.R.color.darker_gray))
            Toast.makeText(this, "POI mapping mode deactivated.", Toast.LENGTH_SHORT).show()
        }
    }
    
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_poi_mapping, menu)
        return true
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_export_firebase -> {
                exportPOIsToFirestore()
                true
            }
            R.id.action_export_log -> {
                exportPOIsToLog()
                true
            }
            R.id.action_clear_pois -> {
                clearPOIs()
                true
            }
            android.R.id.home -> {
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
    
    /**
     * Handle floor plan touch events for POI placement
     */
    fun onFloorPlanTouched(x: Float, y: Float) {
        if (mappingModeActive) {
            showPOIDetailsDialog(x, y)
        }
    }
    
    /**
     * Show dialog to enter POI details
     */
    private fun showPOIDetailsDialog(x: Float, y: Float) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_poi, null)
        
        // Get references to the input fields
        val nameInput = dialogView.findViewById<EditText>(R.id.poi_name_input)
        val descriptionInput = dialogView.findViewById<EditText>(R.id.poi_description_input)
        val categorySpinner = dialogView.findViewById<Spinner>(R.id.poi_category_spinner)
        val coordinatesText = dialogView.findViewById<TextView>(R.id.poi_coordinates_text)
        
        // Update coordinates text
        coordinatesText.text = "Coordinates: (${decimalFormat.format(x)}, ${decimalFormat.format(y)})"
        
        // Set up the category spinner
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, poiCategories)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        categorySpinner.adapter = adapter
        
        // Show the dialog
        AlertDialog.Builder(this)
            .setTitle("Add Point of Interest")
            .setView(dialogView)
            .setPositiveButton("Add") { _, _ ->
                val name = nameInput.text.toString().trim()
                val description = descriptionInput.text.toString().trim()
                val category = categorySpinner.selectedItem.toString()
                
                if (name.isNotEmpty()) {
                    addPOI(name, description, category, x, y)
                } else {
                    Toast.makeText(this, "POI name cannot be empty", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    /**
     * Add a new POI to the list
     */
    private fun addPOI(name: String, description: String, category: String, x: Float, y: Float) {
        // Generate ID from name
        val id = name.lowercase().replace(" ", "_").replace(Regex("[^a-z0-9_]"), "")
        
        // Create a unique ID if this one already exists
        var uniqueId = id
        var counter = 1
        while (mappedPOIs.any { it.id == uniqueId }) {
            uniqueId = "${id}_$counter"
            counter++
        }
        
        // Get current floor - assuming ground floor (0) by default
        // In a real implementation, you'd get this from the FloorPlanView
        val currentFloor = 0
        
        // Create POI with current floor level
        val poi = MappedPOI(
            id = uniqueId,
            name = name,
            description = description,
            category = category,
            position = Position(x.toDouble(), y.toDouble(), currentFloor)
        )
        
        // Directly write to Firestore
        val db = FirebaseFirestore.getInstance()
        val floorRef = db.collection("floor_plans").document(currentFloorId) // Use currentFloorId member variable
        
        val poiData = hashMapOf(
            "name" to poi.name,
            "description" to poi.description,
            "category" to poi.category,
            "position" to hashMapOf(
                "x" to poi.position.x,
                "y" to poi.position.y
            )
        )
        
        floorRef.collection("pois").document(poi.id)
            .set(poiData)
            .addOnSuccessListener {
                Log.d("POIMappingActivity", "POI ${poi.name} successfully written to Firestore")
                Toast.makeText(this, "POI '${poi.name}' added to Firebase", Toast.LENGTH_SHORT).show()
                
                mappedPOIs.add(poi) // Keep it in the activity's list for export/logging

                // Create a FloorPlanView.Marker and add it to the FloorPlanView
                val defaultMarkerBitmap = createDefaultMarkerBitmap()

                if (defaultMarkerBitmap != null) {
                    val newMarker = FloorPlanView.Marker(
                        position = poi.position,
                        bitmap = defaultMarkerBitmap,
                        title = poi.name,
                        floor = poi.position.floor 
                    )
                    floorPlanView.addMarker(newMarker) // This will call invalidate() within FloorPlanView
                } else {
                    floorPlanView.invalidate() // Fallback to invalidate if bitmap creation fails
                }
            }
            .addOnFailureListener { e ->
                Log.w("POIMappingActivity", "Error writing POI ${poi.name} to Firestore", e)
                Toast.makeText(this, "Error adding POI to Firebase: ${e.message}", Toast.LENGTH_LONG).show()
            }
        
        // Log for debugging
        // Log.d("POIMappingActivity", "Added POI: $name at ($x, $y) on floor $currentFloor")
        
        // Show confirmation - moved to success listener
        // Toast.makeText(this, "Added POI: $name", Toast.LENGTH_SHORT).show()
        
        // Refresh the view to show the new POI
        // Note: We're not using floorPlanView.invalidate() since we don't have a draw method
    }
    
    /**
     * Export POIs to Firestore
     */
    private fun exportPOIsToFirestore() {
        val db = FirebaseFirestore.getInstance()
        
        // Get current floor - assuming ground floor (0) by default
        // In a real implementation, you'd get this from the FloorPlanView
        val currentFloor = 0
        
        // Get POIs for the current floor
        val poisToExport = mappedPOIs.filter { it.position.floor == currentFloor }
        
        if (poisToExport.isEmpty()) {
            Toast.makeText(this, "No POIs to export on this floor", Toast.LENGTH_SHORT).show()
            return
        }
        
        // Reference to the floor plan document
        val floorRef = db.collection("floor_plans").document(currentFloorId)
        
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
                    Log.d("POIMappingActivity", "POI ${poi.name} successfully written to Firestore")
                }
                .addOnFailureListener { e ->
                    Log.w("POIMappingActivity", "Error writing POI ${poi.name}", e)
                }
        }
        
        Toast.makeText(
            this, 
            "Exporting ${poisToExport.size} POIs to Firestore...", 
            Toast.LENGTH_SHORT
        ).show()
    }
    
    /**
     * Export POIs to log for manual copying
     */
    private fun exportPOIsToLog() {
        // Get current floor - assuming ground floor (0) by default
        // In a real implementation, you'd get this from the FloorPlanView
        val currentFloor = 0
        
        // Get POIs for the current floor
        val poisToExport = mappedPOIs.filter { it.position.floor == currentFloor }
        
        if (poisToExport.isEmpty()) {
            Toast.makeText(this, "No POIs to export on this floor", Toast.LENGTH_SHORT).show()
            return
        }
        
        Log.d("POIMappingActivity", "======= POIs for Floor $currentFloor =======")
        poisToExport.forEach { poi ->
            Log.d("POIMappingActivity", """
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
            this, 
            "Exported ${poisToExport.size} POIs to log (check Logcat)", 
            Toast.LENGTH_SHORT
        ).show()
    }
    
    /**
     * Clear all mapped POIs on the current floor
     */
    private fun clearPOIs() {
        // Get current floor - assuming ground floor (0) by default
        // In a real implementation, you'd get this from the FloorPlanView
        val currentFloor = 0
        
        val countBefore = mappedPOIs.size
        mappedPOIs.removeAll { it.position.floor == currentFloor }
        val removed = countBefore - mappedPOIs.size
        
        Toast.makeText(
            this, 
            "Cleared $removed POIs from floor $currentFloor", 
            Toast.LENGTH_SHORT
        ).show()
    }
}

private fun createDefaultMarkerBitmap(): Bitmap? {
    val size = 48 // px
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    paint.color = Color.MAGENTA // A distinct color for POIs added via mapping tool
    canvas.drawCircle(size / 2f, size / 2f, size / 2f - 4, paint)
    paint.color = Color.WHITE
    paint.style = Paint.Style.STROKE
    paint.strokeWidth = 2f
    canvas.drawCircle(size / 2f, size / 2f, size / 2f - 4, paint)
    return bitmap
}
