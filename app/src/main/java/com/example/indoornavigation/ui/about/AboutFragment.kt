package com.example.indoornavigation.ui.about

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.indoornavigation.R
import com.example.indoornavigation.databinding.FragmentAboutBinding
import com.example.indoornavigation.ui.common.BaseFragment

class AboutFragment : BaseFragment() {
    private var _binding: FragmentAboutBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAboutBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // Setup content here
        binding.tvAppName.text = getString(R.string.app_name)
        binding.tvAppVersion.text = getString(R.string.app_version, "1.0.0")
        binding.tvAppDescription.text = getString(R.string.app_description)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}