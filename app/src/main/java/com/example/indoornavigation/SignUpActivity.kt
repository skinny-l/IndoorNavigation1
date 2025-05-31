package com.example.indoornavigation

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.indoornavigation.data.repository.FirebaseManager
import com.example.indoornavigation.databinding.ActivitySignUpBinding
import kotlinx.coroutines.launch

/**
 * Activity for user registration
 */
class SignUpActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySignUpBinding
    private val firebaseManager = FirebaseManager()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySignUpBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupClickListeners()
    }
    
    private fun setupClickListeners() {
        // Handle sign up button click
        binding.btnSignUp.setOnClickListener {
            val username = binding.etUsername.text.toString().trim()
            val email = binding.etEmail.text.toString().trim()
            val password = binding.etPassword.text.toString()
            val confirmPassword = binding.etConfirmPassword.text.toString()
            val phone = binding.etPhone.text.toString().trim()
            
            if (validateInputs(username, email, password, confirmPassword, phone)) {
                signUpUser(username, email, password, phone)
            }
        }
    }
    
    private fun validateInputs(username: String, email: String, password: String, 
                              confirmPassword: String, phone: String): Boolean {
        var isValid = true
        
        // Validate username
        if (username.isEmpty()) {
            binding.usernameInputLayout.error = "Username cannot be empty"
            isValid = false
        } else {
            binding.usernameInputLayout.error = null
        }
        
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
        
        // Validate confirm password
        if (confirmPassword.isEmpty()) {
            binding.confirmPasswordInputLayout.error = "Confirm password cannot be empty"
            isValid = false
        } else if (password != confirmPassword) {
            binding.confirmPasswordInputLayout.error = "Passwords do not match"
            isValid = false
        } else {
            binding.confirmPasswordInputLayout.error = null
        }
        
        // Validate phone
        if (phone.isEmpty()) {
            binding.phoneInputLayout.error = "Phone number cannot be empty"
            isValid = false
        } else if (!android.util.Patterns.PHONE.matcher(phone).matches()) {
            binding.phoneInputLayout.error = "Enter a valid phone number"
            isValid = false
        } else {
            binding.phoneInputLayout.error = null
        }
        
        return isValid
    }
    
    private fun signUpUser(username: String, email: String, password: String, phone: String) {
        binding.btnSignUp.isEnabled = false
        binding.btnSignUp.text = getString(R.string.signing_up)
        
        lifecycleScope.launch {
            try {
                firebaseManager.signUp(email, password).fold(
                    onSuccess = { user ->
                        val userInfo = hashMapOf(
                            "displayName" to username,
                            "email" to email,
                            "phoneNumber" to phone
                        )
                        firebaseManager.saveUserDetails(user, userInfo)
                        startActivity(Intent(this@SignUpActivity, MainActivity::class.java))
                        finish()
                    },
                    onFailure = { e ->
                        showError("Sign up failed")
                    }
                )
            } catch (e: Exception) {
                showError("Sign up error")
            }
        }
    }

    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        binding.btnSignUp.isEnabled = true
        binding.btnSignUp.text = getString(R.string.sign_up)
    }
}