package com.yuave.kasookoo.data

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.provider.Settings
import android.util.Log
import com.google.gson.Gson

class UserDataManager(private val context: Context) {
    private val sharedPreferences: SharedPreferences = context.getSharedPreferences(
        "user_data", Context.MODE_PRIVATE
    )
    private val gson = Gson()
    private val firebaseTokenManager = FirebaseTokenManager(context)
    
    companion object {
        private const val KEY_USER_ID = "user_id"
        private const val KEY_USER_TYPE = "user_type"
        private const val KEY_FULL_NAME = "full_name"
        private const val KEY_EMAIL = "email"
        private const val KEY_PHONE_NUMBER = "phone_number"
        private const val KEY_DEVICE_TOKEN = "device_token"
        private const val KEY_DEVICE_INFO = "device_info"
        private const val KEY_IS_LOGGED_IN = "is_logged_in"
    }
    
    // Get FCM token as device token
    fun getDeviceToken(): String {
        val storedToken = firebaseTokenManager.getStoredFCMToken()
        return if (isValidFCMToken(storedToken)) storedToken!! else "no_fcm_token"
    }
    
    // Get FCM token asynchronously with validation
    fun getFCMToken(callback: (String) -> Unit) {
        firebaseTokenManager.getFCMToken { token ->
            if (isValidFCMToken(token)) {
                callback(token)
            } else {
                Log.w("UserDataManager", "⚠️ Invalid FCM token received, using fallback")
                callback("no_fcm_token")
            }
        }
    }
    
    // Validate FCM token
    private fun isValidFCMToken(token: String?): Boolean {
        return !token.isNullOrBlank() && 
               token.length > 50 && 
               token != "no_fcm_token" &&
               !token.startsWith("error_")
    }
    
    // Get device ID (Android ID) for device info
    fun getDeviceId(): String {
        return firebaseTokenManager.getDeviceId()
    }
    
    // Get device info as Map for API requests
    fun getDeviceInfoMap(): Map<String, Any> {
        return mapOf(
            "platform" to "Android",
            "version" to Build.VERSION.RELEASE,
            "api_level" to Build.VERSION.SDK_INT,
            "manufacturer" to Build.MANUFACTURER,
            "model" to Build.MODEL,
            "device_id" to getDeviceId()
        )
    }
    
    // Get device info as JSON object
    fun getDeviceInfo(): String {
        return gson.toJson(getDeviceInfoMap())
    }
    
    // Save user data
    fun saveUserData(
        userId: String,
        userType: String,
        fullName: String,
        email: String,
        phoneNumber: String
    ) {
        sharedPreferences.edit().apply {
            putString(KEY_USER_ID, userId)
            putString(KEY_USER_TYPE, userType)
            putString(KEY_FULL_NAME, fullName)
            putString(KEY_EMAIL, email)
            putString(KEY_PHONE_NUMBER, phoneNumber)
            putString(KEY_DEVICE_TOKEN, getDeviceToken())
            putString(KEY_DEVICE_INFO, getDeviceInfo())
            putBoolean(KEY_IS_LOGGED_IN, true)
        }.apply()
    }
    
    // Get user ID
    fun getUserId(): String? {
        return sharedPreferences.getString(KEY_USER_ID, null)
    }
    
    // Get user type
    fun getUserType(): String? {
        return sharedPreferences.getString(KEY_USER_TYPE, null)
    }
    
    // Get full name
    fun getFullName(): String? {
        return sharedPreferences.getString(KEY_FULL_NAME, null)
    }
    
    // Get email
    fun getEmail(): String? {
        return sharedPreferences.getString(KEY_EMAIL, null)
    }
    
    // Get phone number
    fun getPhoneNumber(): String? {
        return sharedPreferences.getString(KEY_PHONE_NUMBER, null)
    }
    
    // Get stored device token (FCM token)
    fun getStoredDeviceToken(): String? {
        return sharedPreferences.getString(KEY_DEVICE_TOKEN, null)
    }
    
    // Get stored device info
    fun getStoredDeviceInfo(): String? {
        return sharedPreferences.getString(KEY_DEVICE_INFO, null)
    }
    
    // Check if user is logged in
    fun isLoggedIn(): Boolean {
        return sharedPreferences.getBoolean(KEY_IS_LOGGED_IN, false)
    }
    
    // Update device token (FCM token)
    fun updateDeviceToken(newToken: String) {
        sharedPreferences.edit().putString(KEY_DEVICE_TOKEN, newToken).apply()
        firebaseTokenManager.saveFCMToken(newToken)
    }
    
    // Clear user data
    fun clearUserData() {
        sharedPreferences.edit().clear().apply()
        firebaseTokenManager.clearTokens()
    }
    
    // Clear only login status
    fun clearLoginStatus() {
        sharedPreferences.edit().putBoolean(KEY_IS_LOGGED_IN, false).apply()
    }
    
    // Check if user data exists
    fun hasUserData(): Boolean {
        return getUserId() != null && getUserType() != null
    }
}
