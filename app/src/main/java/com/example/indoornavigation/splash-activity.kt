package com.example.indoornavigation

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import com.example.indoornavigation.databinding.ActivitySplashBinding
import com.google.firebase.auth.FirebaseAuth

class SplashActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySplashBinding
    private val SPLASH_DELAY = 2000L // 2 seconds
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Start animation
        binding.logoImage.alpha = 0f
        binding.logoImage.animate().alpha(1f).duration = 1000
        
        // Navigate to main activity after delay
        Handler(Looper.getMainLooper()).postDelayed({
            navigateToNextScreen()
        }, SPLASH_DELAY)
    }
    
    private fun navigateToNextScreen() {
        // Check if user is logged in
        val currentUser = FirebaseAuth.getInstance().currentUser
        
        val intent = if (currentUser != null) {
            // User is logged in, go to main activity
            Intent(this, MainActivity::class.java)
        } else {
            // User is not logged in, go to login activity
            // For simplicity, we'll go to main activity
            Intent(this, MainActivity::class.java)
        }
        
        startActivity(intent)
        finish()
    }
}