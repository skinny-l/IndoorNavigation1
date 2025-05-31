package com.example.indoornavigation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.indoornavigation.data.models.RecentLocation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class RecentLocationsViewModel : ViewModel() {

    private val _recentLocations = MutableStateFlow<List<RecentLocation>>(emptyList())
    val recentLocations: StateFlow<List<RecentLocation>> = _recentLocations.asStateFlow()
    
    init {
        // Load sample data for demonstration
        loadRecentLocations()
    }
    
    private fun loadRecentLocations() {
        viewModelScope.launch {
            // In a real app, this would come from a repository or database
            val sampleLocations = listOf(
                RecentLocation("1", "Laman Najib", 100, 200, 1),
                RecentLocation("2", "TH1", 150, 250, 1),
                RecentLocation("3", "Pejabat Pengurusan Akademik", 200, 300, 2),
                RecentLocation("4", "Big Data Lab", 250, 350, 2)
            )

            _recentLocations.value = sampleLocations
        }
    }
    
    fun addRecentLocation(location: RecentLocation) {
        viewModelScope.launch {
            val currentList = _recentLocations.value.toMutableList()

            // Remove if already exists (to avoid duplicates)
            currentList.removeAll { it.id == location.id }

            // Add to the beginning of the list
            currentList.add(0, location)

            // Limit to the most recent 10 locations
            val limitedList = currentList.take(10)

            _recentLocations.value = limitedList
        }
    }
}
