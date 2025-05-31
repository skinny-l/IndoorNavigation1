package com.example.indoornavigation.ui.testing

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.example.indoornavigation.ui.theme.IndoorNavigationTheme
import com.example.indoornavigation.viewmodel.PositioningTestViewModel

class PositioningTestFragment : Fragment() {
    
    private lateinit var viewModel: PositioningTestViewModel
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        viewModel = ViewModelProvider(this)[PositioningTestViewModel::class.java]
        
        return ComposeView(requireContext()).apply {
            setContent {
                IndoorNavigationTheme {
                    PositioningTestScreen(viewModel)
                }
            }
        }
    }
    
    override fun onPause() {
        super.onPause()
        viewModel.stopPositioning()
    }
    
    override fun onResume() {
        super.onResume()
        viewModel.startPositioning()
    }
}