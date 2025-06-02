package com.example.indoornavigation.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.indoornavigation.data.models.RecentLocation

class RecentLocationsViewModel : ViewModel() {
    
    private val _recentLocations = MutableLiveData<List<RecentLocation>>()
    val recentLocations: LiveData<List<RecentLocation>> = _recentLocations
    
    init {
        // Load sample data for demonstration
        loadRecentLocations()
    }
    
    private fun loadRecentLocations() {
        // In a real app, this would come from a repository or database
        val sampleLocations = listOf(
            RecentLocation("1", "Laman Najib", 100, 200, 1),
            RecentLocation("2", "TH1", 150, 250, 1),
            RecentLocation("3", "Pejabat Pengurusan Akademik", 200, 300, 2),
            RecentLocation("4", "Big Data Lab", 250, 350, 2)
        )
        
        _recentLocations.value = sampleLocations
    }
    
    fun addRecentLocation(location: RecentLocation) {
        val currentList = _recentLocations.value?.toMutableList() ?: mutableListOf()
        
        // Remove if already exists (to avoid duplicates)
        currentList.removeAll { it.id == location.id }
        
        // Add to the beginning of the list
        currentList.add(0, location)
        
        // Limit to the most recent 10 locations
        val limitedList = currentList.take(10)
        
        _recentLocations.value = limitedList
    }
}