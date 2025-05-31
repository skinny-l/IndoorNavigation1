package com.example.indoornavigation.ui.testing

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import com.example.indoornavigation.R
import com.example.indoornavigation.data.models.TestResult
import com.example.indoornavigation.databinding.DialogTestResultBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class TestResultDialogFragment : DialogFragment() {

    private var _binding: DialogTestResultBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var testResult: TestResult
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    
    companion object {
        private const val ARG_TEST_RESULT = "test_result"
        
        fun newInstance(testResult: TestResult): TestResultDialogFragment {
            val fragment = TestResultDialogFragment()
            val args = Bundle()
            args.putString(ARG_TEST_RESULT + "_id", testResult.id)
            args.putString(ARG_TEST_RESULT + "_name", testResult.name)
            args.putLong(ARG_TEST_RESULT + "_startTime", testResult.startTime)
            args.putLong(ARG_TEST_RESULT + "_duration", testResult.duration)
            args.putInt(ARG_TEST_RESULT + "_measurementCount", testResult.measurementCount)
            args.putDouble(ARG_TEST_RESULT + "_averageError", testResult.averageError)
            args.putDouble(ARG_TEST_RESULT + "_maxError", testResult.maxError)
            args.putDouble(ARG_TEST_RESULT + "_standardDeviation", testResult.standardDeviation)
            args.putInt(ARG_TEST_RESULT + "_floor", testResult.floor)
            
            fragment.arguments = args
            return fragment
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Extract test result from arguments
        val args = requireArguments()
        testResult = TestResult(
            id = args.getString(ARG_TEST_RESULT + "_id", ""),
            name = args.getString(ARG_TEST_RESULT + "_name", ""),
            startTime = args.getLong(ARG_TEST_RESULT + "_startTime", 0L),
            duration = args.getLong(ARG_TEST_RESULT + "_duration", 0L),
            measurementCount = args.getInt(ARG_TEST_RESULT + "_measurementCount", 0),
            averageError = args.getDouble(ARG_TEST_RESULT + "_averageError", 0.0),
            maxError = args.getDouble(ARG_TEST_RESULT + "_maxError", 0.0),
            standardDeviation = args.getDouble(ARG_TEST_RESULT + "_standardDeviation", 0.0),
            floor = args.getInt(ARG_TEST_RESULT + "_floor", 0)
        )
    }
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogTestResultBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // Set dialog properties
        dialog?.window?.setBackgroundDrawableResource(R.drawable.bg_rounded_dialog)
        
        // Display test result
        displayTestResult()
        
        // Setup close button
        binding.closeButton.setOnClickListener {
            dismiss()
        }
    }
    
    private fun displayTestResult() {
        with(binding) {
            // Test basic info
            testNameText.text = testResult.name
            dateTimeText.text = dateFormat.format(Date(testResult.startTime))
            durationText.text = formatDuration(testResult.duration)
            floorText.text = "Floor: ${testResult.floor}"
            
            // Error metrics
            averageErrorText.text = formatMeters(testResult.averageError)
            maxErrorText.text = formatMeters(testResult.maxError)
            stdDeviationText.text = "±${formatMeters(testResult.standardDeviation)}"
            measurementCountText.text = "${testResult.measurementCount} samples"
            
            // Rating and colors
            val (rating, color) = getRatingAndColor(testResult.averageError)
            accuracyRatingText.text = rating
            averageErrorText.setTextColor(color)
            accuracyRatingText.setTextColor(color)
        }
    }

    private fun formatDuration(millis: Long): String {
        val minutes = TimeUnit.MILLISECONDS.toMinutes(millis)
        val seconds = TimeUnit.MILLISECONDS.toSeconds(millis) % 60
        return "${minutes}m ${seconds}s"
    }

    private fun formatMeters(value: Double): String = 
        "${String.format("%.2f", value)} meters"

    private fun getRatingAndColor(averageError: Double): Pair<String, Int> {
        return when {
            averageError < 1.5 -> "Excellent" to android.graphics.Color.parseColor("#4CAF50")
            averageError < 3.0 -> "Good" to android.graphics.Color.parseColor("#8BC34A")
            averageError < 5.0 -> "Fair" to android.graphics.Color.parseColor("#FF9800")
            else -> "Poor" to android.graphics.Color.parseColor("#F44336")
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