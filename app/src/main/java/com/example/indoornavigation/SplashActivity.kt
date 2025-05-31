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
    private val SPLASH_DELAY = 2500L // 2.5 seconds
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Start animation for the new logo container
        binding.logoContainer.alpha = 0f
        binding.logoContainer.scaleX = 0.8f
        binding.logoContainer.scaleY = 0.8f
        
        binding.logoContainer.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(1000)
        
        // Animate app name
        binding.appName.alpha = 0f
        binding.appName.translationY = 50f
        binding.appName.animate()
            .alpha(1f)
            .translationY(0f)
            .setStartDelay(500)
            .setDuration(800)
        
        // Navigate to main activity after delay
        Handler(Looper.getMainLooper()).postDelayed({
            navigateToNextScreen()
        }, SPLASH_DELAY)
    }
    
    private fun navigateToNextScreen() {
        // Navigate to welcome screen for authentication
        val intent = Intent(this, WelcomeActivity::class.java)
        startActivity(intent)
        finish()
    }
}
