package com.example.indoornavigation

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.indoornavigation.data.repository.FirebaseManager
import com.example.indoornavigation.databinding.ActivityResetPasswordBinding
import kotlinx.coroutines.launch

/**
 * Activity for resetting password
 */
class ResetPasswordActivity : AppCompatActivity() {

    private lateinit var binding: ActivityResetPasswordBinding
    private val firebaseManager = FirebaseManager()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityResetPasswordBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupClickListeners()
    }
    
    private fun setupClickListeners() {
        // Handle reset password button click
        binding.btnResetPassword.setOnClickListener {
            val email = binding.etEmail.text.toString().trim()
            val newPassword = binding.etNewPassword.text.toString()
            val confirmPassword = binding.etConfirmPassword.text.toString()
            
            if (validateInputs(email, newPassword, confirmPassword)) {
                resetPassword(email, newPassword)
            }
        }
    }
    
    private fun validateInputs(email: String, newPassword: String, confirmPassword: String): Boolean {
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
        if (newPassword.isEmpty()) {
            binding.newPasswordInputLayout.error = "Password cannot be empty"
            isValid = false
        } else if (newPassword.length < 6) {
            binding.newPasswordInputLayout.error = "Password must be at least 6 characters"
            isValid = false
        } else {
            binding.newPasswordInputLayout.error = null
        }
        
        // Validate confirm password
        if (confirmPassword.isEmpty()) {
            binding.confirmPasswordInputLayout.error = "Confirm password cannot be empty"
            isValid = false
        } else if (newPassword != confirmPassword) {
            binding.confirmPasswordInputLayout.error = "Passwords do not match"
            isValid = false
        } else {
            binding.confirmPasswordInputLayout.error = null
        }
        
        return isValid
    }
    
    private fun resetPassword(email: String, newPassword: String) {
        // Show loading state
        binding.btnResetPassword.isEnabled = false
        
        lifecycleScope.launch {
            try {
                firebaseManager.sendPasswordResetEmail(email)
                Toast.makeText(
                    this@ResetPasswordActivity,
                    "Password reset email sent. Check your inbox.",
                    Toast.LENGTH_LONG
                ).show()
                finish()
            } catch (e: Exception) {
                Toast.makeText(
                    this@ResetPasswordActivity,
                    "Failed to send reset email: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
                binding.btnResetPassword.isEnabled = true
            }
        }
    }
}