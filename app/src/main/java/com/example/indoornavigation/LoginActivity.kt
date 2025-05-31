package com.example.indoornavigation

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.indoornavigation.data.repository.FirebaseManager
import com.example.indoornavigation.databinding.ActivityLoginBinding
import kotlinx.coroutines.launch

/**
 * Login screen for the app
 * Handles authentication with Firebase
 */
class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private val firebaseManager = FirebaseManager()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupClickListeners()
    }
    
    private fun setupClickListeners() {
        // Handle login button click
        binding.btnLogin.setOnClickListener {
            val email = binding.etEmail.text.toString().trim()
            val password = binding.etPassword.text.toString()
            
            if (validateInputs(email, password)) {
                loginUser(email, password)
            }
        }
        
        // Handle forgot password click
        binding.tvForgotPassword.setOnClickListener {
            // Navigate to reset password screen
            startActivity(Intent(this, ResetPasswordActivity::class.java))
        }
        
        // Handle sign up text click
        binding.tvSignUp.setOnClickListener {
            // Navigate to sign up screen
            startActivity(Intent(this, SignUpActivity::class.java))
        }
    }
    
    private fun validateInputs(email: String, password: String): Boolean {
        var isValid = true
        
        // Validate email
        if (email.isEmpty()) {
            binding.emailInputLayout.error = "Email cannot be empty"
            isValid = false
        } else if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.emailInputLayout.error = "Enter a valid email address"
            isValid = false
        } else {
            binding.emailInputLayout.error = null
        }
        
        // Validate password
        if (password.isEmpty()) {
            binding.passwordInputLayout.error = "Password cannot be empty"
            isValid = false
        } else if (password.length < 6) {
            binding.passwordInputLayout.error = "Password must be at least 6 characters"
            isValid = false
        } else {
            binding.passwordInputLayout.error = null
        }
        
        return isValid
    }
    
    private fun loginUser(email: String, password: String) {
        // Show loading state (could add a progress bar in the future)
        binding.btnLogin.isEnabled = false
        
        lifecycleScope.launch {
            try {
                val result = firebaseManager.signIn(email, password)
                
                result.fold(
                    onSuccess = {
                        // Login successful, navigate to main activity
                        Toast.makeText(this@LoginActivity, "Login successful", Toast.LENGTH_SHORT).show()
                        startActivity(Intent(this@LoginActivity, MainActivity::class.java))
                        finish()
                    },
                    onFailure = { exception ->
                        // Login failed, show error message
                        if (exception.message?.contains("API key") == true) {
                            Toast.makeText(this@LoginActivity, "Login failed: Invalid API key", Toast.LENGTH_LONG).show()
                        } else {
                            Toast.makeText(this@LoginActivity, "Login failed: ${exception.message}", Toast.LENGTH_LONG).show()
                        }
                        binding.btnLogin.isEnabled = true
                    }
                )
            } catch (e: Exception) {
                // Handle general exceptions
                if (e.message?.contains("API key") == true) {
                    Toast.makeText(this@LoginActivity, "Error: Invalid API key", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this@LoginActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
                binding.btnLogin.isEnabled = true
            }
        }
    }
}