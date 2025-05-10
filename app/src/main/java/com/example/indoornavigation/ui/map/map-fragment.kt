package com.example.indoornavigation.ui.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Bundle
import androidx.preference.PreferenceManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.example.indoornavigation.R
import com.example.indoornavigation.data.models.Position
import com.example.indoornavigation.databinding.FragmentMapBinding
import com.example.indoornavigation.viewmodel.PositioningViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Overlay
import java.io.File
import kotlin.math.max

class MapFragment : Fragment() {

    private var _binding: FragmentMapBinding? = null
    private val binding get() = _binding!!

    private val positioningViewModel: PositioningViewModel by activityViewModels()

    private lateinit var mapView: MapView
    private var userLocationMarker: Marker? = null
    private var floorPlanOverlay: FloorPlanOverlay? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMapBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize OSMdroid configuration
        Configuration.getInstance().load(
            requireContext(),
            PreferenceManager.getDefaultSharedPreferences(requireContext())
        )

        // Initialize map
        mapView = binding.mapView

        // Setup map style and initial position
        setupMap()

        // Observe user position updates
        observePositionUpdates()

        // Setup floor controls
        setupFloorControls()
    }

    private fun setupMap() {
        // Basic map configuration
        mapView.setTileSource(TileSourceFactory.MAPNIK)
        mapView.setMultiTouchControls(true)
        mapView.controller.setZoom(18.0)

        // Set initial center position (use a default location or building entrance)
        // For indoor navigation, we'll use relative coordinates and convert them
        val initialGeoPoint = GeoPoint(37.7749, -122.4194) // Example: San Francisco
        mapView.controller.setCenter(initialGeoPoint)

        // Add floor plan overlay
        addFloorPlanOverlay(positioningViewModel.currentFloor.value)
    }

    private fun addFloorPlanOverlay(floor: Int) {
        // Remove existing floor plan overlay
        if (floorPlanOverlay != null) {
            mapView.overlays.remove(floorPlanOverlay)
        }

        // Create new floor plan overlay based on floor
        floorPlanOverlay = FloorPlanOverlay(requireContext(), floor)
        mapView.overlays.add(floorPlanOverlay)

        // Refresh map
        mapView.invalidate()
    }

    private fun observePositionUpdates() {
        viewLifecycleOwner.lifecycleScope.launch {
            positioningViewModel.currentPosition.collectLatest { position ->
                position?.let {
                    updateUserLocationOnMap(it)
                }
            }
        }

        // Observe floor changes
        viewLifecycleOwner.lifecycleScope.launch {
            positioningViewModel.currentFloor.collectLatest { floor ->
                // Update floor indicator
                binding.floorText.text = getString(R.string.floor_indicator, floor)

                // Update map layer to show correct floor
                addFloorPlanOverlay(floor)
            }
        }
    }

    private fun updateUserLocationOnMap(position: Position) {
        // Convert app coordinates to map coordinates
        val geoPoint = convertPositionToGeoPoint(position)

        // Create or update user location marker
        if (userLocationMarker == null) {
            userLocationMarker = Marker(mapView).apply {
                icon = resources.getDrawable(R.drawable.ic_location_marker, null)
                title = "Your Location"
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                mapView.overlays.add(this)
            }
        }

        // Update marker position
        userLocationMarker?.position = geoPoint

        // Center map on user if tracking is enabled
        if (binding.trackUserSwitch.isChecked) {
            mapView.controller.animateTo(geoPoint)
        }

        // Refresh map
        mapView.invalidate()
    }

    private fun convertPositionToGeoPoint(position: Position): GeoPoint {
        // For indoor positioning, we need to convert local coordinates to geo coordinates
        // This is a simplified example - in a real app, you would use proper coordinate transformation

        // Center of your building/map in geo coordinates
        val centerLat = 37.7749
        val centerLng = -122.4194

        // Scale factors (meters to degrees approximately)
        val latScale = 0.000009
        val lngScale = 0.000011

        // Convert local coordinates to geo coordinates relative to center
        val lat = centerLat + position.y * latScale
        val lng = centerLng + position.x * lngScale

        return GeoPoint(lat, lng)
    }

    private fun setupFloorControls() {
        // Floor up button
        binding.floorUpButton.setOnClickListener {
            val currentFloor = positioningViewModel.currentFloor.value
            positioningViewModel.setCurrentFloor(currentFloor + 1)
        }

        // Floor down button
        binding.floorDownButton.setOnClickListener {
            val currentFloor = positioningViewModel.currentFloor.value
            if (currentFloor > 1) {
                positioningViewModel.setCurrentFloor(currentFloor - 1)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()
    }

    override fun onPause() {
        super.onPause()
        mapView.onPause()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    /**
     * Custom overlay for floor plans
     */
    inner class FloorPlanOverlay(private val context: Context, private val floor: Int) : Overlay() {
        private val paint = Paint()
        private var floorPlanBitmap: Bitmap? = null

        init {
            // Load floor plan bitmap based on floor number
            val resourceId = when (floor) {
                1 -> R.drawable.floor_plan_ground
                2 -> R.drawable.floor_plan_1st
                else -> R.drawable.floor_plan_upper
            }

            try {
                floorPlanBitmap = BitmapFactory.decodeResource(context.resources, resourceId)
            } catch (e: Exception) {
                // Handle missing floor plan
                e.printStackTrace()
            }
        }

        override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
            if (shadow) return

            floorPlanBitmap?.let { bitmap ->
                // Get the map's bounds in pixels
                val screenRect = mapView.projection.screenRect

                // Calculate positioning to center the floor plan
                val left = screenRect.left.toFloat()
                val top = screenRect.top.toFloat()

                // Draw the floor plan
                canvas.drawBitmap(bitmap, left, top, paint)
            }
        }
    }
}