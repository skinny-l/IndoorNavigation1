package com.example.indoornavigation.ui.testing.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.indoornavigation.data.models.TestResult
import com.example.indoornavigation.databinding.ItemTestResultBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TestResultAdapter : ListAdapter<TestResult, TestResultAdapter.TestResultViewHolder>(TestResultDiffCallback()) {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TestResultViewHolder {
        val binding = ItemTestResultBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return TestResultViewHolder(binding)
    }
    
    override fun onBindViewHolder(holder: TestResultViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
    
    inner class TestResultViewHolder(
        private val binding: ItemTestResultBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        
        fun bind(testResult: TestResult) {
            // Format date
            val date = Date(testResult.startTime)
            val formattedDate = dateFormat.format(date)
            
            // Set values
            binding.testNameText.text = testResult.name
            binding.testDateText.text = formattedDate
            binding.floorText.text = "Floor: ${testResult.floor}"
            binding.averageErrorText.text = "Avg Error: ${String.format("%.2f", testResult.averageError)} m"
            binding.maxErrorText.text = "Max: ${String.format("%.2f", testResult.maxError)} m"
            binding.samplesText.text = "${testResult.measurementCount} samples"
            
            // Set error color based on value
            val errorColor = when {
                testResult.averageError < 2.0 -> android.graphics.Color.parseColor("#4CAF50") // Green
                testResult.averageError < 5.0 -> android.graphics.Color.parseColor("#FF9800") // Orange
                else -> android.graphics.Color.parseColor("#F44336") // Red
            }
            binding.averageErrorText.setTextColor(errorColor)
        }
    }
    
    class TestResultDiffCallback : DiffUtil.ItemCallback<TestResult>() {
        override fun areItemsTheSame(oldItem: TestResult, newItem: TestResult): Boolean {
            return oldItem.id == newItem.id
        }
        
        override fun areContentsTheSame(oldItem: TestResult, newItem: TestResult): Boolean {
            return oldItem == newItem
        }
    }
}