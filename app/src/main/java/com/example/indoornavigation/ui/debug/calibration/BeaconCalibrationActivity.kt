package com.example.indoornavigation.ui.debug.calibration

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupActionBarWithNavController
import com.example.indoornavigation.R
import com.example.indoornavigation.databinding.ActivityBeaconCalibrationBinding

class BeaconCalibrationActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBeaconCalibrationBinding
    private lateinit var navController: NavController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBeaconCalibrationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Set up the NavController
        val navHostFragment = supportFragmentManager.findFragmentById(R.id.calibration_nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController

        // Setup ActionBar with NavController
        setSupportActionBar(binding.toolbar)
        setupActionBarWithNavController(navController)
    }

    override fun onSupportNavigateUp(): Boolean {
        return navController.navigateUp() || super.onSupportNavigateUp()
    }
}