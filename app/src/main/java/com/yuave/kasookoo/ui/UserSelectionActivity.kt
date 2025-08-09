package com.yuave.kasookoo.ui

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.yuave.kasookoo.data.UserDataManager
import com.yuave.kasookoo.databinding.ActivityUserSelectionBinding

class UserSelectionActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityUserSelectionBinding
    private lateinit var userDataManager: UserDataManager
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityUserSelectionBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        userDataManager = UserDataManager(this)
        
        // Check if user is already logged in
        if (userDataManager.isLoggedIn()) {
            val userType = userDataManager.getUserType()
            val isCustomer = userType == "customer"
            
            val intent = Intent(this, MainActivity::class.java).apply {
                putExtra("isCustomer", isCustomer)
            }
            startActivity(intent)
            finish()
            return
        } else if (userDataManager.hasUserData()) {
            // User has data but not logged in, go to login screen
            val intent = Intent(this, LoginActivity::class.java)
            startActivity(intent)
            finish()
            return
        } else {
            // No user data, go directly to registration
            val intent = Intent(this, RegistrationActivity::class.java)
            startActivity(intent)
            finish()
            return
        }
    }
} 