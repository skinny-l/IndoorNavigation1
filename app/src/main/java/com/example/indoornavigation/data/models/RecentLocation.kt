package com.example.indoornavigation.data.models

data class RecentLocation(
    val id: String,
    val name: String,
    val x: Int,
    val y: Int,
    val floor: Int,
    val timestamp: Long = System.currentTimeMillis()
)