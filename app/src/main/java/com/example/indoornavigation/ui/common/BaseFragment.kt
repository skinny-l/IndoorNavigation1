package com.example.indoornavigation.ui.common

import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import com.example.indoornavigation.R

/**
 * Base fragment with common functionality, including drawer menu setup
 */
abstract class BaseFragment : Fragment() {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupHamburgerMenu()
    }

    /**
     * Setup hamburger menu button if it exists in the fragment layout
     */
    private fun setupHamburgerMenu() {
        view?.findViewById<ImageButton>(R.id.btnMenu)?.setOnClickListener {
            val drawerLayout = requireActivity().findViewById<DrawerLayout>(R.id.drawer_layout)
            drawerLayout?.openDrawer(GravityCompat.START)
        }
    }
}