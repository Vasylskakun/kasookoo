package com.yuave.kasookoo.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class FirebaseTokenManager(private val context: Context) {
    private val sharedPreferences: SharedPreferences = context.getSharedPreferences(
        "firebase_tokens", Context.MODE_PRIVATE
    )
    
    companion object {
        private const val TAG = "FirebaseTokenManager"
        private const val KEY_FCM_TOKEN = "fcm_token"
        private const val KEY_DEVICE_ID = "device_id"
        private const val MAX_RETRY_ATTEMPTS = 5
        private const val INITIAL_RETRY_DELAY = 2000L // 2 seconds
    }
    
    // Get or generate FCM token with retry logic
    fun getFCMToken(callback: (String) -> Unit) {
        val storedToken = getStoredFCMToken()
        if (isValidToken(storedToken)) {
            Log.d(TAG, "✅ Using stored valid FCM token: ${storedToken?.take(20)}...")
            callback(storedToken!!)
        } else {
            Log.d(TAG, "🔄 Generating new FCM token with retry logic...")
            generateNewFCMTokenWithRetry(callback)
        }
    }
    
    // Generate new FCM token with retry mechanism
    private fun generateNewFCMTokenWithRetry(callback: (String) -> Unit, attempt: Int = 1) {
        Log.d(TAG, "🔄 FCM token generation attempt $attempt/$MAX_RETRY_ATTEMPTS")
        
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val token = task.result
                if (isValidToken(token)) {
                    Log.d(TAG, "✅ FCM token generated successfully on attempt $attempt: ${token?.take(20)}...")
                    saveFCMToken(token!!)
                    callback(token)
                } else {
                    Log.e(TAG, "❌ Generated token is invalid: $token")
                    handleTokenFailure(callback, attempt, "Invalid token generated")
                }
            } else {
                val error = task.exception
                Log.e(TAG, "❌ FCM token generation failed on attempt $attempt", error)
                
                when {
                    error?.message?.contains("SERVICE_NOT_AVAILABLE") == true -> {
                        Log.w(TAG, "⚠️ Google Play Services not available, will retry...")
                        handleTokenFailure(callback, attempt, "SERVICE_NOT_AVAILABLE")
                    }
                    error?.message?.contains("API key") == true -> {
                        Log.e(TAG, "❌ Firebase API key issue - check google-services.json")
                        callback("") // Don't retry for API key issues
                    }
                    error?.message?.contains("TIMEOUT") == true -> {
                        Log.w(TAG, "⚠️ Network timeout, will retry...")
                        handleTokenFailure(callback, attempt, "TIMEOUT")
                    }
                    else -> {
                        Log.e(TAG, "❌ Unknown error: ${error?.message}")
                        handleTokenFailure(callback, attempt, error?.message ?: "Unknown error")
                    }
                }
            }
        }
    }
    
    // Handle token generation failure with retry logic
    private fun handleTokenFailure(callback: (String) -> Unit, attempt: Int, reason: String) {
        if (attempt < MAX_RETRY_ATTEMPTS) {
            val delayMs = INITIAL_RETRY_DELAY * attempt // Exponential backoff
            Log.d(TAG, "🔄 Retrying FCM token generation in ${delayMs}ms (Reason: $reason)")
            
            CoroutineScope(Dispatchers.Main).launch {
                delay(delayMs)
                generateNewFCMTokenWithRetry(callback, attempt + 1)
            }
        } else {
            Log.e(TAG, "❌ FCM token generation failed after $MAX_RETRY_ATTEMPTS attempts")
            Log.e(TAG, "❌ Final failure reason: $reason")
            
            // Try to use stored token as fallback
            val storedToken = getStoredFCMToken()
            if (isValidToken(storedToken)) {
                Log.w(TAG, "⚠️ Using stored token as fallback: ${storedToken?.take(20)}...")
                callback(storedToken!!)
            } else {
                Log.e(TAG, "❌ No valid fallback token available")
                callback("") // Return empty string to indicate failure
            }
        }
    }
    
    // Validate FCM token
    private fun isValidToken(token: String?): Boolean {
        return !token.isNullOrBlank() && 
               token.length > 50 && 
               token != "no_fcm_token" &&
               !token.startsWith("error_")
    }
    
    // Save FCM token locally
    fun saveFCMToken(token: String) {
        if (isValidToken(token)) {
            sharedPreferences.edit().putString(KEY_FCM_TOKEN, token).apply()
            Log.d(TAG, "✅ Valid FCM token saved locally: ${token.take(20)}...")
        } else {
            Log.e(TAG, "❌ Attempted to save invalid FCM token: $token")
        }
    }
    
    // Get stored FCM token
    fun getStoredFCMToken(): String? {
        return sharedPreferences.getString(KEY_FCM_TOKEN, null)
    }
    
    // Get device ID (Android ID)
    fun getDeviceId(): String {
        return android.provider.Settings.Secure.getString(
            context.contentResolver,
            android.provider.Settings.Secure.ANDROID_ID
        ) ?: "unknown_device"
    }
    
    // Save device ID
    fun saveDeviceId(deviceId: String) {
        sharedPreferences.edit().putString(KEY_DEVICE_ID, deviceId).apply()
        Log.d(TAG, "Device ID saved: $deviceId")
    }
    
    // Get stored device ID
    fun getStoredDeviceId(): String? {
        return sharedPreferences.getString(KEY_DEVICE_ID, null)
    }
    
    // Force refresh FCM token
    fun forceRefreshToken(callback: (String) -> Unit) {
        Log.d(TAG, "🔄 Force refreshing FCM token...")
        clearStoredToken()
        generateNewFCMTokenWithRetry(callback)
    }
    
    // Clear stored token
    private fun clearStoredToken() {
        sharedPreferences.edit().remove(KEY_FCM_TOKEN).apply()
        Log.d(TAG, "🧹 Stored FCM token cleared")
    }
    
    // Clear all tokens
    fun clearTokens() {
        sharedPreferences.edit().clear().apply()
        Log.d(TAG, "🧹 All tokens cleared")
    }
}