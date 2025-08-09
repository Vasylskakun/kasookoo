package com.yuave.kasookoo.ui

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.yuave.kasookoo.data.CallRepository
import com.yuave.kasookoo.data.UserDataManager
import com.yuave.kasookoo.databinding.ActivityLoginBinding
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityLoginBinding
    private lateinit var userDataManager: UserDataManager
    private lateinit var callRepository: CallRepository
    
    companion object {
        private const val TAG = "LoginActivity"
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        userDataManager = UserDataManager(this)
        callRepository = CallRepository()
        
        setupUI()
        setupClickListeners()
    }
    
    private fun setupUI() {
        // Pre-fill email if available
        userDataManager.getEmail()?.let { email ->
            binding.etEmail.setText(email)
        }
        
        // Show registration button if no user data exists
        if (!userDataManager.hasUserData()) {
            binding.btnRegister.visibility = View.VISIBLE
        }
    }
    
    private fun setupClickListeners() {
        binding.btnLogin.setOnClickListener {
            handleLogin()
        }
        
        binding.btnRegister.setOnClickListener {
            navigateToRegistration()
        }
    }
    
    private fun handleLogin() {
        val email = binding.etEmail.text.toString().trim()
        val password = binding.etPassword.text.toString().trim()
        
        // Validate inputs
        if (email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
            return
        }
        
        // Check if user data exists locally
        if (!userDataManager.hasUserData()) {
            Toast.makeText(this, "No user data found. Please register first.", Toast.LENGTH_SHORT).show()
            return
        }
        
        // Show loading
        binding.progressBar.visibility = View.VISIBLE
        binding.btnLogin.isEnabled = false
        
        lifecycleScope.launch {
            try {
                // Get stored user data
                val userId = userDataManager.getUserId()
                val userType = userDataManager.getUserType()
                
                if (userId == null || userType == null) {
                    Toast.makeText(this@LoginActivity, "User data not found", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                
                // Get FCM token for login
                userDataManager.getFCMToken { newFcmToken ->
                    lifecycleScope.launch {
                        try {
                            if (newFcmToken.isEmpty() || newFcmToken == "no_fcm_token") {
                                Log.e(TAG, "❌ Failed to get valid FCM token for login")
                                Toast.makeText(this@LoginActivity, 
                                    "Failed to initialize Firebase. Please check your internet connection and try again.", 
                                    Toast.LENGTH_LONG).show()
                                binding.progressBar.visibility = View.GONE
                                binding.btnLogin.isEnabled = true
                                return@launch
                            }
                            // Device token logic for login:
                            // - deviceToken: The FCM token that was sent during registration (stored locally)
                            // - newDeviceToken: Current FCM token (might be different if app was reinstalled)
                            val deviceToken = userDataManager.getStoredDeviceToken() ?: "no_stored_token"
                            val deviceInfo = userDataManager.getDeviceInfoMap()
                            
                            Log.d(TAG, "📱 Login token details:")
                            Log.d(TAG, "   - Stored token: ${deviceToken.take(20)}...")
                            Log.d(TAG, "   - New token: ${newFcmToken.take(20)}...")
                            
                            // Update Firebase token
                            val updateResult = callRepository.updateCallerOrCalledForFirebaseToken(
                                userType, userId, deviceToken, newFcmToken, deviceInfo
                            )
                            
                            if (updateResult.isSuccess) {
                                // Update stored token
                                userDataManager.updateDeviceToken(newFcmToken)
                                
                                Log.d(TAG, "✅ Login successful for $userType: $userId")
                                
                                // Navigate to main activity
                                val intent = Intent(this@LoginActivity, MainActivity::class.java).apply {
                                    putExtra("isCustomer", userType == "customer")
                                }
                                startActivity(intent)
                                finish()
                            } else {
                                Log.e(TAG, "❌ Login failed: ${updateResult.exceptionOrNull()?.message}")
                                Toast.makeText(this@LoginActivity, "Login failed", Toast.LENGTH_SHORT).show()
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "❌ Login error: ${e.message}")
                            Toast.makeText(this@LoginActivity, "Login error: ${e.message}", Toast.LENGTH_SHORT).show()
                        } finally {
                            binding.progressBar.visibility = View.GONE
                            binding.btnLogin.isEnabled = true
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Login error: ${e.message}")
                Toast.makeText(this@LoginActivity, "Login error: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                binding.progressBar.visibility = View.GONE
                binding.btnLogin.isEnabled = true
            }
        }
    }
    
    private fun navigateToRegistration() {
        val intent = Intent(this, RegistrationActivity::class.java)
        startActivity(intent)
    }
}
