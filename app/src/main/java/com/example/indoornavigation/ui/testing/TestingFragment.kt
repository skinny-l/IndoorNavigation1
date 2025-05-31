package com.example.indoornavigation.ui.testing

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.indoornavigation.data.models.Position
import com.example.indoornavigation.databinding.FragmentTestingBinding
import com.example.indoornavigation.ui.testing.adapter.TestResultAdapter
import com.example.indoornavigation.viewmodel.PositioningViewModel
import com.example.indoornavigation.viewmodel.TestingViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class TestingFragment : Fragment() {

    private var _binding: FragmentTestingBinding? = null
    private val binding get() = _binding!!
    
    private val testingViewModel: TestingViewModel by activityViewModels()
    private val positioningViewModel: PositioningViewModel by activityViewModels()
    
    private lateinit var testResultAdapter: TestResultAdapter
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTestingBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // Setup test results recycler view
        setupTestResultsList()
        
        // Setup UI controls
        setupUIControls()
        
        // Observe position updates
        observePositionUpdates()
        
        // Observe test results
        observeTestResults()
        
        // Load test results
        loadTestResults()
    }
    
    private fun setupTestResultsList() {
        testResultAdapter = TestResultAdapter()
        binding.testResultsRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = testResultAdapter
        }
    }
    
    private fun setupUIControls() {
        // Start test button
        binding.startTestButton.setOnClickListener {
            startTest()
        }
        
        // End test button
        binding.endTestButton.setOnClickListener {
            endTest()
        }
        
        // Radio buttons for test type
        binding.radioAccuracy.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                testingViewModel.setTestType(TestingViewModel.TestType.ACCURACY)
                binding.positionInputLayout.visibility = View.VISIBLE
            }
        }
        
        binding.radioWalking.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                testingViewModel.setTestType(TestingViewModel.TestType.WALKING)
                binding.positionInputLayout.visibility = View.GONE
            }
        }
        
        binding.radioAb.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                testingViewModel.setTestType(TestingViewModel.TestType.A_B_TEST)
                binding.positionInputLayout.visibility = View.GONE
                
                // Show A/B test dialog
                showABTestDialog()
            }
        }
        
        // Update UI based on current test type
        when (testingViewModel.getTestType()) {
            TestingViewModel.TestType.ACCURACY -> binding.radioAccuracy.isChecked = true
            TestingViewModel.TestType.WALKING -> binding.radioWalking.isChecked = true
            TestingViewModel.TestType.A_B_TEST -> binding.radioAb.isChecked = true
        }
        
        // Use current position button
        binding.useCurrentPositionButton.setOnClickListener {
            val currentPosition = positioningViewModel.currentPosition.value
            if (currentPosition != null) {
                binding.positionXInput.setText(currentPosition.x.toInt().toString())
                binding.positionYInput.setText(currentPosition.y.toInt().toString())
                binding.positionFloorInput.setText(currentPosition.floor.toString())
            }
        }
    }
    
    private fun observePositionUpdates() {
        viewLifecycleOwner.lifecycleScope.launch {
            positioningViewModel.currentPosition.collectLatest { position ->
                position?.let {
                    binding.currentPositionText.text = 
                        "Current Position: (${it.x.toInt()}, ${it.y.toInt()}, Floor: ${it.floor})"
                    
                    // Record measurement if test is active
                    if (testingViewModel.isTestActive()) {
                        val error = testingViewModel.recordMeasurement(it)
                        binding.currentErrorText.text = "Current Error: ${String.format("%.2f", error)} meters"
                    } else {
                        binding.currentErrorText.text = "Current Error: 0.00 meters"
                    }
                } ?: run {
                    binding.currentPositionText.text = "Position unavailable"
                }
            }
        }
    }
    
    private fun observeTestResults() {
        viewLifecycleOwner.lifecycleScope.launch {
            testingViewModel.testResult.collectLatest { result ->
                result?.let {
                    // Show test result dialog
                    TestResultDialogFragment.newInstance(it)
                        .show(childFragmentManager, "TestResult")
                }
            }
        }
    }
    
    private fun startTest() {
        // Get test name
        val testName = binding.testNameInput.text.toString().trim()
        if (testName.isEmpty()) {
            binding.testNameInput.error = "Test name is required"
            return
        }
        
        when (testingViewModel.getTestType()) {
            TestingViewModel.TestType.ACCURACY -> {
                // Get ground truth position
                val x = binding.positionXInput.text.toString().toDoubleOrNull()
                val y = binding.positionYInput.text.toString().toDoubleOrNull()
                val floor = binding.positionFloorInput.text.toString().toIntOrNull()
                
                if (x == null || y == null || floor == null) {
                    binding.positionXInput.error = "Valid position required"
                    return
                }
                
                val groundTruthPosition = Position(x, y, floor)
                
                // Start test
                if (testingViewModel.startTest(testName, groundTruthPosition)) {
                    updateUIForActiveTest(true)
                }
            }
            TestingViewModel.TestType.WALKING -> {
                // For walking test, use current position as start
                val currentPosition = positioningViewModel.currentPosition.value
                if (currentPosition == null) {
                    binding.currentPositionText.error = "Current position not available"
                    return
                }
                
                // Start test
                if (testingViewModel.startWalkingTest(testName, currentPosition)) {
                    updateUIForActiveTest(true)
                }
            }
            TestingViewModel.TestType.A_B_TEST -> {
                // A/B test type is handled by the dialog
            }
        }
    }
    
    private fun endTest() {
        viewLifecycleOwner.lifecycleScope.launch {
            testingViewModel.endTest()
            updateUIForActiveTest(false)
            
            // Load updated test results
            loadTestResults()
        }
    }
    
    private fun updateUIForActiveTest(active: Boolean) {
        binding.startTestButton.isEnabled = !active
        binding.endTestButton.isEnabled = active
        binding.testTypeGroup.isEnabled = !active
        binding.testNameInput.isEnabled = !active
        binding.positionXInput.isEnabled = !active
        binding.positionYInput.isEnabled = !active
        binding.positionFloorInput.isEnabled = !active
        binding.useCurrentPositionButton.isEnabled = !active
        
        binding.currentErrorText.visibility = if (active) View.VISIBLE else View.GONE
    }
    
    private fun loadTestResults() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val results = testingViewModel.loadTestResults()
                testResultAdapter.submitList(results)
            } catch (e: Exception) {
                // Handle any errors, maybe log or show a message
                e.printStackTrace()
            }
        }
    }
    
    private fun showABTestDialog() {
        ABTestDialog().show(childFragmentManager, "ABTest")
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}