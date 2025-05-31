package com.example.indoornavigation.ui.pathfinding

import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Bundle
import androidx.preference.PreferenceManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.navArgs
import com.example.indoornavigation.R
import com.example.indoornavigation.data.models.Path
import com.example.indoornavigation.data.models.Position
import com.example.indoornavigation.databinding.FragmentPathfindingBinding
import com.example.indoornavigation.utils.PathfindingEngine
import com.example.indoornavigation.viewmodel.PositioningViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline

class PathfindingFragment : Fragment() {

    private var _binding: FragmentPathfindingBinding? = null
    private val binding get() = _binding!!

    private val positioningViewModel: PositioningViewModel by activityViewModels()
    private val args: PathfindingFragmentArgs by navArgs()

    private lateinit var startPosition: Position
    private lateinit var endPosition: Position
    private var pathPolyline: Polyline? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPathfindingBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize OSMdroid configuration
        Configuration.getInstance().load(
            requireContext(),
            PreferenceManager.getDefaultSharedPreferences(requireContext())
        )

        // Get positions from arguments
        startPosition = args.startPosition
        endPosition = args.endPosition

        // Initialize map
        setupMap()

        // Calculate and display path
        calculatePath()

        // Observe position updates
        observePositionUpdates()

        // Setup back button
        binding.backToMapFab.setOnClickListener {
            requireActivity().onBackPressed()
        }
    }

    private fun setupMap() {
        // Basic map configuration
        binding.mapView.apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            controller.setZoom(18.0)
        }

        // Set initial camera position to see both start and end
        val midpointLat = (convertPositionToGeoPoint(startPosition).latitude +
                convertPositionToGeoPoint(endPosition).latitude) / 2
        val midpointLon = (convertPositionToGeoPoint(startPosition).longitude +
                convertPositionToGeoPoint(endPosition).longitude) / 2

        val midpoint = GeoPoint(midpointLat, midpointLon)
        binding.mapView.controller.setCenter(midpoint)
    }

    private fun calculatePath() {
        // Get navigation nodes from ViewModel
        val navigationNodes = positioningViewModel.getNavigationNodes()

        // Create pathfinding engine
        val pathfindingEngine = PathfindingEngine(navigationNodes)

        // Calculate path
        val path = pathfindingEngine.findPath(startPosition, endPosition)

        path?.let {
            displayPath(it)
            displayPathInfo(it)
        } ?: run {
            // Show error if path not found
            binding.pathInfoText.text = "No path found between locations"
        }
    }

    private fun displayPath(path: Path) {
        // Convert waypoints to geo points
        val geoPoints = path.waypoints.map { convertPositionToGeoPoint(it) }

        // Create polyline for the path
        pathPolyline = Polyline().apply {
            setPoints(geoPoints)
            outlinePaint.color = Color.RED
            outlinePaint.strokeWidth = 10f
            binding.mapView.overlays.add(this)
        }

        // Add markers for start and end points
        val startMarker = Marker(binding.mapView).apply {
            position = convertPositionToGeoPoint(path.start)
            icon = resources.getDrawable(R.drawable.ic_start_marker, null)
            title = "Start"
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            binding.mapView.overlays.add(this)
        }

        val endMarker = Marker(binding.mapView).apply {
            position = convertPositionToGeoPoint(path.end)
            icon = resources.getDrawable(R.drawable.ic_end_marker, null)
            title = "Destination"
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            binding.mapView.overlays.add(this)
        }

        // Refresh map
        binding.mapView.invalidate()
    }

    private fun displayPathInfo(path: Path) {
        // Calculate total distance
        var totalDistance = 0.0
        for (i in 0 until path.waypoints.size - 1) {
            val p1 = path.waypoints[i]
            val p2 = path.waypoints[i + 1]

            val dx = p2.x - p1.x
            val dy = p2.y - p1.y

            totalDistance += Math.sqrt(dx * dx + dy * dy)
        }

        // Display path information
        binding.pathInfoText.text = "Distance: ${String.format("%.1f", totalDistance)} meters"

        // Floor changes
        val floorChanges = path.waypoints.zipWithNext { a, b -> a.floor != b.floor }.count { it }
        binding.floorChangesText.text = "Floor changes: $floorChanges"

        // Setup start navigation button
        binding.startNavigationButton.setOnClickListener {
            // In a real app, this would start turn-by-turn navigation
            // For now, just show a message
            binding.pathInfoText.text = "Navigation started"
        }
    }

    private fun observePositionUpdates() {
        viewLifecycleOwner.lifecycleScope.launch {
            positioningViewModel.currentPosition.collectLatest { position ->
                position?.let {
                    updateUserLocationOnMap(it)
                }
            }
        }
    }

    private fun updateUserLocationOnMap(position: Position) {
        // Convert to geo point
        val geoPoint = convertPositionToGeoPoint(position)

        // Create or update user location marker
        val userMarker = Marker(binding.mapView).apply {
            this.position = geoPoint
            icon = resources.getDrawable(R.drawable.ic_location_marker, null)
            title = "Your Location"
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        }

        // Remove previous marker if exists and add new one
        binding.mapView.overlays.removeAll { it is Marker && it.title == "Your Location" }
        binding.mapView.overlays.add(userMarker)

        // Refresh map
        binding.mapView.invalidate()
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

    override fun onResume() {
        super.onResume()
        binding.mapView.onResume()
    }

    override fun onPause() {
        super.onPause()
        binding.mapView.onPause()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}