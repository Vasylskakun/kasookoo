package com.yuave.kasookoo.ui

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.yuave.kasookoo.CallApplication
import com.yuave.kasookoo.R
import com.yuave.kasookoo.core.CallState
import com.yuave.kasookoo.core.LiveKitManager
import com.yuave.kasookoo.databinding.ActivityCallBinding
import kotlinx.coroutines.launch
import android.media.AudioManager
import android.content.Context
import com.yuave.kasookoo.core.CallType

class CallActivity : AppCompatActivity() {
    
    companion object {
        private const val TAG = "CallActivity"
    }
    
    private lateinit var binding: ActivityCallBinding
    private lateinit var liveKitManager: LiveKitManager
    private lateinit var audioManager: AudioManager
    private var isCustomer: Boolean = true
    private var isMuted: Boolean = false
    private var isSpeakerOn: Boolean = false  // Add speaker state tracking
    private var isEndingCall = false
    private var isActivityFinishing = false
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCallBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Get user type from intent
        isCustomer = intent.getBooleanExtra("isCustomer", true)
        
        // Initialize LiveKit manager and audio manager
        liveKitManager = (application as CallApplication).liveKitManager
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        
        // Check if this is a support call
        val currentCallType = com.yuave.kasookoo.ui.MainActivity.getCurrentCallType()
        val isSupportCall = currentCallType == CallType.SUPPORT
        
        Log.d(TAG, "CallActivity created - isSupportCall: $isSupportCall")
        
        setupUI()
        setupClickListeners()
        observeCallState()
        
        // Start call timer
        startCallTimer()
        
        // Setup audio for voice call
        setupAudioForCall()
        
        // Force enable audio to ensure it's working
        liveKitManager.forceEnableAudio()
        
        // Additional audio setup for support calls
        if (isSupportCall) {
            Log.d(TAG, "🆘 Additional audio setup for support call...")
            setupSupportCallAudio()
        }
        
        // Sync speaker state with AudioManager
        syncSpeakerState()
    }
    
    private fun setupAudioForCall() {
        Log.d(TAG, "=== SETUP AUDIO FOR CALL ===")
        
        try {
            // 1. Set audio mode to communication mode for voice calls
            Log.d(TAG, "Setting audio mode to MODE_IN_COMMUNICATION...")
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
            Log.d(TAG, "Audio mode set to: ${audioManager.mode}")
            
            // 2. Enable speakerphone for better audio quality
            Log.d(TAG, "Enabling speakerphone...")
            audioManager.isSpeakerphoneOn = true
            isSpeakerOn = true  // Track speaker state
            Log.d(TAG, "Speakerphone enabled: ${audioManager.isSpeakerphoneOn}")
            
            // 3. Ensure microphone is not muted
            Log.d(TAG, "Ensuring microphone is not muted...")
            audioManager.isMicrophoneMute = false
            Log.d(TAG, "Microphone muted: ${audioManager.isMicrophoneMute}")
            
            // 4. Check microphone permission
            val hasMicrophonePermission = checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED
            Log.d(TAG, "Microphone permission granted: $hasMicrophonePermission")
            
            // 5. Log final audio status
            Log.d(TAG, "=== FINAL AUDIO STATUS ===")
            Log.d(TAG, "Audio mode: ${audioManager.mode}")
            Log.d(TAG, "Speakerphone on: ${audioManager.isSpeakerphoneOn}")
            Log.d(TAG, "Microphone muted: ${audioManager.isMicrophoneMute}")
            Log.d(TAG, "Microphone permission: $hasMicrophonePermission")
            
            if (audioManager.mode == AudioManager.MODE_IN_COMMUNICATION && 
                audioManager.isSpeakerphoneOn && 
                !audioManager.isMicrophoneMute && 
                hasMicrophonePermission) {
                Log.d(TAG, "✅ AUDIO SETUP COMPLETE - Voice call should work properly!")
            } else {
                Log.w(TAG, "⚠️ AUDIO SETUP ISSUE DETECTED:")
                Log.w(TAG, "   - Audio mode: ${audioManager.mode} (should be ${AudioManager.MODE_IN_COMMUNICATION})")
                Log.w(TAG, "   - Speakerphone: ${audioManager.isSpeakerphoneOn} (should be true)")
                Log.w(TAG, "   - Microphone muted: ${audioManager.isMicrophoneMute} (should be false)")
                Log.w(TAG, "   - Permission: $hasMicrophonePermission (should be true)")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up audio for call: ${e.message}")
            Log.e(TAG, "Audio setup error:", e)
        }
        
        Log.d(TAG, "=== END AUDIO SETUP ===")
    }
    
    private fun setupSupportCallAudio() {
        Log.d(TAG, "🆘 Setting up additional audio for support call...")
        
        try {
            // Force enable audio again for support calls
            liveKitManager.forceEnableAudio()
            
            // Add a small delay to ensure audio tracks are properly established
            lifecycleScope.launch {
                kotlinx.coroutines.delay(1000) // 1 second delay
                
                // Force enable audio one more time
                liveKitManager.forceEnableAudio()
                
                Log.d(TAG, "✅ Support call audio setup completed")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error in support call audio setup: ${e.message}")
        }
    }
    
    private fun checkAudioStatus() {
        Log.d(TAG, "=== AUDIO STATUS CHECK ===")
        
        // Check microphone permission
        val hasMicrophonePermission = checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED
        Log.d(TAG, "Microphone permission: $hasMicrophonePermission")
        
        // Check audio manager status
        Log.d(TAG, "Audio mode: ${audioManager.mode}")
        Log.d(TAG, "Speakerphone on: ${audioManager.isSpeakerphoneOn}")
        Log.d(TAG, "Microphone muted: ${audioManager.isMicrophoneMute}")
        
        // Check if we're in communication mode
        if (audioManager.mode != AudioManager.MODE_IN_COMMUNICATION) {
            Log.w(TAG, "⚠️ Audio not in communication mode! Current mode: ${audioManager.mode}")
            Log.w(TAG, "Setting audio mode to MODE_IN_COMMUNICATION...")
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        } else {
            Log.d(TAG, "✅ Audio in communication mode")
        }
        
        // Enable speakerphone for better audio
        if (!audioManager.isSpeakerphoneOn) {
            Log.d(TAG, "Enabling speakerphone for better audio...")
            audioManager.isSpeakerphoneOn = true
        }
        
        Log.d(TAG, "=== END AUDIO STATUS CHECK ===")
    }
    
    private fun setupUI() {
        // Check if this is a support call by checking the stored call type
        val currentCallType = com.yuave.kasookoo.ui.MainActivity.getCurrentCallType()
        val isSupportCall = currentCallType == CallType.SUPPORT
        
        if (isSupportCall) {
            // Support call UI
            binding.tvContactName.text = "Customer Support"
            binding.tvCallStatus.text = "Connected to Support"
            // Set support icon (you might want to add a support icon)
            binding.ivContactAvatar.setImageResource(R.drawable.ic_person)
        } else if (isCustomer) {
            // Driver call UI
            binding.tvContactName.text = "Driver"
            binding.tvCallStatus.text = "Connected to Driver"
            // Set driver icon
            binding.ivContactAvatar.setImageResource(R.drawable.ic_driver_modern)
        } else {
            // Driver receiving call UI
            binding.tvContactName.text = "Customer"
            binding.tvCallStatus.text = "Connected to Customer"
            // Set customer icon
            binding.ivContactAvatar.setImageResource(R.drawable.ic_person)
        }
        
        binding.tvCallDuration.text = "00:00"
        updateMuteButton()
        updateSpeakerButton()  // Initialize speaker button state
        
        // Test speaker icons accessibility
        try {
            val speakerOnIcon = R.drawable.ic_speaker_on
            val speakerOffIcon = R.drawable.ic_speaker_off
            Log.d(TAG, "✅ Speaker icons accessible - ON: $speakerOnIcon, OFF: $speakerOffIcon")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Speaker icons not accessible: ${e.message}")
        }
    }
    
    private fun setupClickListeners() {
        binding.btnEndCall.setOnClickListener {
            endCall()
        }
        
        binding.btnMute.setOnClickListener {
            toggleMute()
        }
        
        binding.btnSpeaker.setOnClickListener {
            toggleSpeaker()
        }
    }
    
    private fun observeCallState() {
        // Check if this is a support call by checking the stored call type
        val currentCallType = com.yuave.kasookoo.ui.MainActivity.getCurrentCallType()
        val isSupportCall = currentCallType == CallType.SUPPORT
        
        // For both support calls and regular calls, observe LiveKit states
        lifecycleScope.launch {
            liveKitManager.callState.collect { state ->
                Log.d(TAG, "Call state changed: $state (Support call: $isSupportCall)")
                
                when (state) {
                    CallState.IDLE -> {
                        // Call ended - finish activity
                        Log.d(TAG, "Call ended, finishing activity")
                        finish()
                    }
                    CallState.ERROR -> {
                        // Connection error
                        Log.d(TAG, "Call connection error")
                        showError("Call connection lost")
                        finish()
                    }
                    else -> {
                        // Continue call
                        Log.d(TAG, "Call state: $state - continuing call")
                    }
                }
            }
        }
    }
    
    private fun toggleMute() {
        isMuted = !isMuted
        
        if (isMuted) {
            liveKitManager.muteAudio()
            Log.d(TAG, "Audio muted")
        } else {
            liveKitManager.unmuteAudio()
            Log.d(TAG, "Audio unmuted")
        }
        
        updateMuteButton()
    }
    
    private fun updateMuteButton() {
        if (isMuted) {
            binding.btnMute.setImageResource(R.drawable.ic_mic_off)
            binding.btnMute.setBackgroundResource(R.drawable.button_ripple_decline)
        } else {
            binding.btnMute.setImageResource(R.drawable.ic_microphone)
            binding.btnMute.setBackgroundResource(R.drawable.button_ripple_control)
        }
    }
    
    private fun toggleSpeaker() {
        try {
            isSpeakerOn = !isSpeakerOn
            
            if (isSpeakerOn) {
                // Enable speaker with multiple approaches for better reliability
                audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
                audioManager.isSpeakerphoneOn = true
                Log.d(TAG, "🔊 Speakerphone enabled")
            } else {
                // Disable speaker with multiple approaches for better reliability
                audioManager.isSpeakerphoneOn = false
                // Keep communication mode for call quality
                audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
                Log.d(TAG, "🔇 Speakerphone disabled")
            }
            
            // Add a small delay to allow AudioManager to update
            lifecycleScope.launch {
                kotlinx.coroutines.delay(100) // 100ms delay
                
                // Verify the change took effect
                val actualSpeakerState = audioManager.isSpeakerphoneOn
                if (actualSpeakerState == isSpeakerOn) {
                    Log.d(TAG, "✅ Speaker state changed successfully")
                } else {
                    Log.w(TAG, "⚠️ Speaker state mismatch - expected: $isSpeakerOn, actual: $actualSpeakerState")
                    // Force the state to match our intention
                    forceSpeakerState(isSpeakerOn)
                    Log.d(TAG, "🔄 Forced speaker state to: $isSpeakerOn")
                }
                
                // Always update the UI based on our intended state
                updateSpeakerButton()
            }
            
            // Update UI immediately for better responsiveness
            updateSpeakerButton()
            
        } catch (e: Exception) {
            Log.e(TAG, "Error toggling speaker: ${e.message}")
            showError("Failed to toggle speaker")
        }
    }
    
    private fun updateSpeakerButton() {
        Log.d(TAG, "🔄 Updating speaker button - isSpeakerOn: $isSpeakerOn")
        
        if (isSpeakerOn) {
            binding.btnSpeaker.setImageResource(R.drawable.ic_speaker_on)
            binding.btnSpeaker.setBackgroundResource(R.drawable.button_ripple_control)
            Log.d(TAG, "✅ Set speaker button to ON state (green icon)")
        } else {
            binding.btnSpeaker.setImageResource(R.drawable.ic_speaker_off)
            binding.btnSpeaker.setBackgroundResource(R.drawable.button_ripple_decline)
            Log.d(TAG, "✅ Set speaker button to OFF state (red icon)")
        }
    }
    
    private fun syncSpeakerState() {
        // Sync our tracked state with AudioManager's actual state
        val actualSpeakerState = audioManager.isSpeakerphoneOn
        
        // If there's a mismatch, prefer our tracked state for UI consistency
        if (actualSpeakerState != isSpeakerOn) {
            Log.d(TAG, "🔄 Speaker state sync - AudioManager: $actualSpeakerState, Tracked: $isSpeakerOn")
            // Keep our tracked state for UI consistency
            Log.d(TAG, "📱 Using tracked state for UI: $isSpeakerOn")
        } else {
            Log.d(TAG, "✅ Speaker state synced: $isSpeakerOn")
        }
        
        updateSpeakerButton()
    }
    
    private fun forceSpeakerState(enable: Boolean) {
        try {
            if (enable) {
                audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
                audioManager.isSpeakerphoneOn = true
                Log.d(TAG, "🔧 Forced speaker ON")
            } else {
                audioManager.isSpeakerphoneOn = false
                audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
                Log.d(TAG, "🔧 Forced speaker OFF")
            }
            
            // Update our tracked state
            isSpeakerOn = enable
            updateSpeakerButton()
            
        } catch (e: Exception) {
            Log.e(TAG, "Error forcing speaker state: ${e.message}")
        }
    }
    
    private fun endCall() {
        if (isEndingCall || isActivityFinishing) {
            Log.d(TAG, "⚠️ End call already in progress or activity finishing, ignoring duplicate click")
            return
        }
        
        // Check if we're already in the process of ending the call
        val currentCallType = com.yuave.kasookoo.ui.MainActivity.getCurrentCallType()
        if (currentCallType == null) {
            Log.d(TAG, "⚠️ Call already ended, finishing activity")
            finish()
            return
        }
        
        isEndingCall = true
        isActivityFinishing = true
        
        // Disable the end call button immediately to prevent multiple clicks
        binding.btnEndCall.isEnabled = false
        binding.btnEndCall.alpha = 0.5f
        
        Log.d(TAG, "📞 Call ended by user")
        Log.d(TAG, "🔍 Activity instance: ${this.hashCode()}")
        
        // Check if this is a support call
        val isSupportCall = currentCallType == CallType.SUPPORT
        
        if (isSupportCall) {
            Log.d(TAG, "🆘 Ending support call...")
        } else {
            Log.d(TAG, "🚗 Ending driver call...")
        }
        
        // Set flag to prevent navigation when state changes to IDLE
        com.yuave.kasookoo.ui.MainActivity.setEndingCallProgrammatically(true)
        
        // Restore audio settings
        restoreAudioSettings()
        
        Log.d(TAG, "🔄 Calling liveKitManager.disconnect()...")
        liveKitManager.disconnect()
        Log.d(TAG, "✅ liveKitManager.disconnect() completed")
        
        // For support calls, finish immediately to prevent double-click issues
        if (isSupportCall) {
            Log.d(TAG, "🏁 Finishing support call activity immediately...")
            finish()
        } else {
            // Add a small delay for driver calls to prevent rapid double-clicks
            lifecycleScope.launch {
                kotlinx.coroutines.delay(100) // 100ms delay
                Log.d(TAG, "🏁 Finishing activity...")
                finish()
            }
        }
    }
    
    private fun restoreAudioSettings() {
        Log.d(TAG, "=== RESTORING AUDIO SETTINGS ===")
        
        try {
            // Restore normal audio mode
            audioManager.mode = AudioManager.MODE_NORMAL
            Log.d(TAG, "Audio mode restored to MODE_NORMAL")
            
            // Turn off speakerphone
            audioManager.isSpeakerphoneOn = false
            isSpeakerOn = false  // Reset speaker state
            Log.d(TAG, "Speakerphone turned off")
            
            Log.d(TAG, "✅ Audio settings restored successfully")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error restoring audio settings: ${e.message}")
        }
        
        Log.d(TAG, "=== END AUDIO RESTORE ===")
    }
    
    private fun startCallTimer() {
        lifecycleScope.launch {
            while (true) {
                kotlinx.coroutines.delay(1000)
                updateCallDuration()
            }
        }
    }
    
    private fun updateCallDuration() {
        val durationSeconds = liveKitManager.getCurrentCallDuration()
        val minutes = durationSeconds / 60
        val seconds = durationSeconds % 60
        binding.tvCallDuration.text = String.format("%02d:%02d", minutes, seconds)
    }
    
    private fun showError(message: String) {
        android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_SHORT).show()
    }
    
    override fun onBackPressed() {
        // Prevent accidental back press during call
        // User must press end call button
        super.onBackPressed()
    }

    override fun onDestroy() {
        super.onDestroy()
        
        // Ensure audio settings are restored when activity is destroyed
        restoreAudioSettings()
    }
} 