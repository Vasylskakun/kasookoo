package com.yuave.kasookoo.ui

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.yuave.kasookoo.data.CallRepository
import com.yuave.kasookoo.data.UserDataManager
import com.yuave.kasookoo.databinding.ActivityRegistrationBinding
import kotlinx.coroutines.launch

class RegistrationActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityRegistrationBinding
    private lateinit var userDataManager: UserDataManager
    private lateinit var callRepository: CallRepository
    
    companion object {
        private const val TAG = "RegistrationActivity"
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegistrationBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        userDataManager = UserDataManager(this)
        callRepository = CallRepository()
        
        setupUI()
        setupClickListeners()
    }
    
    private fun setupUI() {
        // Setup role spinner
        val roles = arrayOf("Customer", "Driver")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, roles)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerRole.adapter = adapter
        
        // Default to Customer (first option)
        binding.spinnerRole.setSelection(0)
        
        // Show login button if user data exists
        if (userDataManager.hasUserData()) {
            binding.btnLogin.visibility = View.VISIBLE
        }
    }
    
    private fun setupClickListeners() {
        binding.btnRegister.setOnClickListener {
            handleRegistration()
        }
        
        binding.btnLogin.setOnClickListener {
            navigateToLogin()
        }
    }
    
    private fun handleRegistration() {
        val email = binding.etEmail.text.toString().trim()
        val password = binding.etPassword.text.toString().trim()
        val selectedRole = binding.spinnerRole.selectedItem.toString()
        
        // Validate inputs
        if (email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
            return
        }
        
        // Show loading
        binding.progressBar.visibility = View.VISIBLE
        binding.btnRegister.isEnabled = false
        
        lifecycleScope.launch {
            try {
                val userType = if (selectedRole == "Driver") "driver" else "customer"
                
                // Step 1: Get user info based on role
                val userInfo = if (userType == "driver") {
                    callRepository.getRandomUserForCallerIdentity()
                } else {
                    callRepository.getRandomCustomerLeadForCallerIdentity()
                }
                
                if (userInfo.isSuccess) {
                    val userData = userInfo.getOrNull()
                    if (userData != null) {
                        // Step 2: Get FCM token and register
                        userDataManager.getFCMToken { fcmToken ->
                            lifecycleScope.launch {
                                try {
                                    if (fcmToken.isEmpty() || fcmToken == "no_fcm_token") {
                                        Log.e(TAG, "❌ Failed to get valid FCM token")
                                        Toast.makeText(this@RegistrationActivity, 
                                            "Failed to initialize Firebase. Please check your internet connection and try again.", 
                                            Toast.LENGTH_LONG).show()
                                        binding.progressBar.visibility = View.GONE
                                        binding.btnRegister.isEnabled = true
                                        return@launch
                                    }
                                    val deviceInfo = userDataManager.getDeviceInfoMap()
                                    
                                    val userId = when (userData) {
                                        is com.yuave.kasookoo.data.RandomUserResponse -> userData.id
                                        is com.yuave.kasookoo.data.RandomCustomerLeadResponse -> userData.id
                                        else -> throw Exception("Invalid user data type")
                                    }
                                    
                                    val fullNameToSave = when (userData) {
                                        is com.yuave.kasookoo.data.RandomUserResponse -> "${userData.first_name} ${userData.last_name}"
                                        is com.yuave.kasookoo.data.RandomCustomerLeadResponse -> userData.full_name
                                        else -> "User"
                                    }
                                    
                                    val emailToSave = when (userData) {
                                        is com.yuave.kasookoo.data.RandomUserResponse -> userData.email
                                        is com.yuave.kasookoo.data.RandomCustomerLeadResponse -> userData.email
                                        else -> email
                                    }
                                    
                                    val phoneToSave = when (userData) {
                                        is com.yuave.kasookoo.data.RandomUserResponse -> userData.phone_number
                                        is com.yuave.kasookoo.data.RandomCustomerLeadResponse -> userData.phone_number
                                        else -> ""
                                    }
                                    
                                    val registerResult = callRepository.registerCallerOrCalledForFirebaseToken(
                                        userType, userId, fcmToken, deviceInfo
                                    )
                                    
                                    if (registerResult.isSuccess) {
                                        // Save user data locally
                                        userDataManager.saveUserData(
                                            userId, userType, fullNameToSave, emailToSave, phoneToSave
                                        )
                                        
                                        Log.d(TAG, "✅ Registration successful for $userType: $userId")
                                        Log.d(TAG, "📱 FCM Token: ${fcmToken.take(20)}...")
                                        
                                        // Navigate to main activity
                                        val intent = Intent(this@RegistrationActivity, MainActivity::class.java).apply {
                                            putExtra("isCustomer", userType == "customer")
                                        }
                                        startActivity(intent)
                                        finish()
                                    } else {
                                        Log.e(TAG, "❌ Registration failed: ${registerResult.exceptionOrNull()?.message}")
                                        Toast.makeText(this@RegistrationActivity, "Registration failed", Toast.LENGTH_SHORT).show()
                                    }
                                } catch (e: Exception) {
                                    Log.e(TAG, "❌ Registration error: ${e.message}")
                                    Toast.makeText(this@RegistrationActivity, "Registration error: ${e.message}", Toast.LENGTH_SHORT).show()
                                } finally {
                                    binding.progressBar.visibility = View.GONE
                                    binding.btnRegister.isEnabled = true
                                }
                            }
                        }
                    } else {
                        Log.e(TAG, "❌ User data is null")
                        Toast.makeText(this@RegistrationActivity, "Failed to get user data", Toast.LENGTH_SHORT).show()
                        binding.progressBar.visibility = View.GONE
                        binding.btnRegister.isEnabled = true
                    }
                } else {
                    Log.e(TAG, "❌ Failed to get user info: ${userInfo.exceptionOrNull()?.message}")
                    Toast.makeText(this@RegistrationActivity, "Failed to get user info", Toast.LENGTH_SHORT).show()
                    binding.progressBar.visibility = View.GONE
                    binding.btnRegister.isEnabled = true
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Registration error: ${e.message}")
                Toast.makeText(this@RegistrationActivity, "Registration error: ${e.message}", Toast.LENGTH_SHORT).show()
                binding.progressBar.visibility = View.GONE
                binding.btnRegister.isEnabled = true
            }
        }
    }
    
    private fun navigateToLogin() {
        val intent = Intent(this, LoginActivity::class.java)
        startActivity(intent)
    }
}
