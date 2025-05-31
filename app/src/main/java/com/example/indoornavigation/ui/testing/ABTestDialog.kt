package com.example.indoornavigation.ui.testing

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import com.example.indoornavigation.R
import com.example.indoornavigation.databinding.DialogAbTestBinding
import com.example.indoornavigation.viewmodel.TestingViewModel

class ABTestDialog : DialogFragment() {

    private var _binding: DialogAbTestBinding? = null
    private val binding get() = _binding!!
    
    private val testingViewModel: TestingViewModel by activityViewModels()
    
    private val algorithms = arrayOf(
        "BLE Trilateration",
        "BLE Weighted Average", 
        "WiFi Fingerprinting",
        "Fusion (BLE + WiFi)", 
        "Fusion with Dead Reckoning"
    )
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogAbTestBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // Set dialog properties
        dialog?.window?.setBackgroundDrawableResource(R.drawable.bg_rounded_dialog)
        
        // Setup algorithm spinners
        setupAlgorithmSpinners()
        
        // Setup buttons
        binding.startButton.setOnClickListener {
            startABTest()
        }
        
        binding.cancelButton.setOnClickListener {
            dismiss()
        }
    }
    
    private fun setupAlgorithmSpinners() {
        // Create adapter
        val adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_dropdown_item,
            algorithms
        )
        
        // Set adapters
        binding.algorithmASpinner.adapter = adapter
        binding.algorithmBSpinner.adapter = adapter
        
        // Set default selections
        binding.algorithmASpinner.setSelection(0) // BLE Trilateration
        binding.algorithmBSpinner.setSelection(3) // Fusion
    }
    
    private fun startABTest() {
        // Get test name
        val testName = binding.testNameInput.text.toString().trim()
        if (testName.isEmpty()) {
            binding.testNameInput.error = "Test name is required"
            return
        }
        
        // Get sample count
        val sampleCountText = binding.sampleCountInput.text.toString()
        val sampleCount = sampleCountText.toIntOrNull() ?: 0
        if (sampleCount <= 0) {
            binding.sampleCountInput.error = "Valid sample count required"
            return
        }
        
        // Get selected algorithms
        val algorithmA = binding.algorithmASpinner.selectedItemPosition
        val algorithmB = binding.algorithmBSpinner.selectedItemPosition
        
        if (algorithmA == algorithmB) {
            // Show error
            binding.errorText.visibility = View.VISIBLE
            binding.errorText.text = "Please select different algorithms for A and B"
            return
        }
        
        // Hide error
        binding.errorText.visibility = View.GONE
        
        // Start A/B test
        testingViewModel.startABTest(
            testName = testName,
            algorithmA = mapAlgorithmIndex(algorithmA),
            algorithmB = mapAlgorithmIndex(algorithmB),
            sampleCount = sampleCount
        )
        
        // Close dialog
        dismiss()
    }
    
    private fun mapAlgorithmIndex(index: Int): TestingViewModel.Algorithm {
        return when (index) {
            0 -> TestingViewModel.Algorithm.BLE_TRILATERATION
            1 -> TestingViewModel.Algorithm.BLE_WEIGHTED_AVERAGE
            2 -> TestingViewModel.Algorithm.WIFI_FINGERPRINTING
            3 -> TestingViewModel.Algorithm.FUSION
            4 -> TestingViewModel.Algorithm.FUSION_WITH_DR
            else -> TestingViewModel.Algorithm.FUSION
        }
    }
    
    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}