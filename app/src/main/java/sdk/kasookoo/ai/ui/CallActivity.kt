package sdk.kasookoo.ai.ui

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import sdk.kasookoo.ai.CallApplication
import sdk.kasookoo.ai.R
import sdk.kasookoo.ai.core.CallState
import sdk.kasookoo.ai.core.LiveKitManager
import sdk.kasookoo.ai.databinding.ActivityCallBinding
import kotlinx.coroutines.launch
import android.media.AudioManager
import android.widget.SeekBar
import android.content.Context
import sdk.kasookoo.ai.core.CallType
import android.media.AudioAttributes
import android.media.AudioFocusRequest

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
    private var audioFocusGranted = false
    private var audioFocusRequest: AudioFocusRequest? = null
    private val audioFocusChangeListener = AudioManager.OnAudioFocusChangeListener { change ->
        Log.d(TAG, "Audio focus changed: $change")
    }
    
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
        val currentCallType = sdk.kasookoo.ai.ui.MainActivity.getCurrentCallType()
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
            
            // Check DTMF support for support calls
            Log.d(TAG, "🎵 Checking DTMF support for support call...")
            checkDtmfSupport()
        }
        
        // Sync speaker state with AudioManager
        syncSpeakerState()
    }

    private fun requestAudioFocus() {
        try {
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
            val afr = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
                .setOnAudioFocusChangeListener(audioFocusChangeListener)
                .setAudioAttributes(attrs)
                .setWillPauseWhenDucked(false)
                .build()
            val result = audioManager.requestAudioFocus(afr)
            audioFocusRequest = afr
            audioFocusGranted = (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
            Log.d(TAG, "Audio focus granted: $audioFocusGranted")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to request audio focus: ${e.message}")
        }
    }

    private fun abandonAudioFocus() {
        try {
            audioFocusRequest?.let {
                audioManager.abandonAudioFocusRequest(it)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to abandon audio focus: ${e.message}")
        }
    }
    
    private fun setupAudioForCall() {
        Log.d(TAG, "=== SETUP AUDIO FOR CALL ===")
        
        try {
            // Get current call type for specific audio setup
            val currentCallType = sdk.kasookoo.ai.ui.MainActivity.getCurrentCallType()
            val isSupportCall = currentCallType == CallType.SUPPORT
            val isCustomerCall = currentCallType == CallType.CUSTOMER
            val isDriverCall = currentCallType == CallType.DRIVER
            
            Log.d(TAG, "📞 Setting up audio for call type: $currentCallType")
            
            // 1. Set audio mode to communication mode for voice calls
            Log.d(TAG, "Setting audio mode to MODE_IN_COMMUNICATION...")
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
            Log.d(TAG, "Audio mode set to: ${audioManager.mode}")
            
            // 2. Request audio focus for voice communication
            requestAudioFocus()

            // 3. Always start with speaker ON for all call types
            Log.d(TAG, "🔊 Starting call with speakerphone ON for all call types...")
            audioManager.isSpeakerphoneOn = true
            isSpeakerOn = true
            try {
                val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL)
                val initialVolume = (maxVolume * 0.8).toInt() // 80% default
                audioManager.setStreamVolume(AudioManager.STREAM_VOICE_CALL, initialVolume, 0)
                Log.d(TAG, "🔊 Initial speaker volume set to: $initialVolume/$maxVolume (~80%)")
                // Initialize the volume slider to current volume percent
                val current = audioManager.getStreamVolume(AudioManager.STREAM_VOICE_CALL)
                val percent = (current * 100) / maxVolume
                binding.sbVolume.progress = percent
            } catch (e: Exception) {
                Log.w(TAG, "⚠️ Could not set initial speaker volume: ${e.message}")
            }
            
            // 3. Ensure microphone is not muted
            Log.d(TAG, "Ensuring microphone is not muted...")
            audioManager.isMicrophoneMute = false
            Log.d(TAG, "Microphone muted: ${audioManager.isMicrophoneMute}")
            
            // 4. Check microphone permission
            val hasMicrophonePermission = checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED
            Log.d(TAG, "Microphone permission granted: $hasMicrophonePermission")
            
            // 5. Log final audio status
            Log.d(TAG, "=== FINAL AUDIO STATUS ===")
            Log.d(TAG, "Call type: $currentCallType")
            Log.d(TAG, "Audio mode: ${audioManager.mode}")
            Log.d(TAG, "Speakerphone on: ${audioManager.isSpeakerphoneOn}")
            Log.d(TAG, "Microphone muted: ${audioManager.isMicrophoneMute}")
            Log.d(TAG, "Microphone permission: $hasMicrophonePermission")
            Log.d(TAG, "Speaker state tracked: $isSpeakerOn")
            
            if (audioManager.mode == AudioManager.MODE_IN_COMMUNICATION && 
                !audioManager.isMicrophoneMute && 
                hasMicrophonePermission) {
                Log.d(TAG, "✅ AUDIO SETUP COMPLETE - Voice call should work properly!")
                Log.d(TAG, "   - Speaker state: ${if (isSpeakerOn) "ON" else "OFF"}")
            } else {
                Log.w(TAG, "⚠️ AUDIO SETUP ISSUE DETECTED:")
                Log.w(TAG, "   - Audio mode: ${audioManager.mode} (should be ${AudioManager.MODE_IN_COMMUNICATION})")
                Log.w(TAG, "   - Speakerphone: ${audioManager.isSpeakerphoneOn}")
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
        
        // Check speaker state and fix if needed
        if (audioManager.isSpeakerphoneOn != isSpeakerOn) {
            Log.w(TAG, "⚠️ Speaker state mismatch detected!")
            Log.w(TAG, "   - AudioManager speaker: ${audioManager.isSpeakerphoneOn}")
            Log.w(TAG, "   - Tracked speaker: $isSpeakerOn")
            Log.w(TAG, "🔄 Fixing speaker state...")
            forceSpeakerState(isSpeakerOn)
        }
        
        // Check volume levels
        try {
            val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_VOICE_CALL)
            val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL)
            val volumePercentage = (currentVolume * 100) / maxVolume
            
            Log.d(TAG, "📊 Volume status:")
            Log.d(TAG, "   - Current volume: $currentVolume / $maxVolume ($volumePercentage%)")
            
            // Ensure adequate volume for the current speaker state
            if (isSpeakerOn && volumePercentage < 50) {
                Log.w(TAG, "⚠️ Speaker volume too low, increasing...")
                val targetVolume = (maxVolume * 0.8).toInt()
                audioManager.setStreamVolume(AudioManager.STREAM_VOICE_CALL, targetVolume, 0)
                Log.d(TAG, "✅ Speaker volume increased to: $targetVolume")
            } else if (!isSpeakerOn && volumePercentage > 80) {
                Log.w(TAG, "⚠️ Earpiece volume too high, decreasing...")
                val targetVolume = (maxVolume * 0.6).toInt()
                audioManager.setStreamVolume(AudioManager.STREAM_VOICE_CALL, targetVolume, 0)
                Log.d(TAG, "✅ Earpiece volume decreased to: $targetVolume")
            }
        } catch (e: Exception) {
            Log.w(TAG, "⚠️ Could not check/adjust volume: ${e.message}")
        }
        
        Log.d(TAG, "=== END AUDIO STATUS CHECK ===")
    }
    
    private fun setupUI() {
        // Check if this is a support call by checking the stored call type
        val currentCallType = sdk.kasookoo.ai.ui.MainActivity.getCurrentCallType()
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
            // Toggle visibility of volume slider while ensuring speaker stays ON when visible
            val isVisible = binding.volumeSliderContainer.visibility == android.view.View.VISIBLE
            if (isVisible) {
                binding.volumeSliderContainer.visibility = android.view.View.GONE
            } else {
                // Ensure speaker ON when showing the slider
                if (!isSpeakerOn) {
                    forceSpeakerState(true)
                }
                binding.volumeSliderContainer.visibility = android.view.View.VISIBLE
            }
        }
        
        // Add long press on speaker button to test audio routing (for debugging)
        binding.btnSpeaker.setOnLongClickListener {
            Log.d(TAG, "🔍 Long press detected on speaker button - testing audio routing...")
            testAudioRouting()
            true // Consume the long press
        }

        // Volume SeekBar control
        binding.sbVolume.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    try {
                        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL)
                        val target = (maxVolume * progress) / 100
                        audioManager.setStreamVolume(AudioManager.STREAM_VOICE_CALL, target, 0)
                        Log.d(TAG, "🔊 Volume slider changed: $progress% → volume $target/$maxVolume")
                        if (progress <= 0) {
                            // Treat 0% as speaker OFF
                            isSpeakerOn = false
                            audioManager.isSpeakerphoneOn = false
                            updateSpeakerButton()
                        } else if (!isSpeakerOn) {
                            // Any non-zero volume should enable speaker
                            forceSpeakerState(true)
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "⚠️ Failed to set volume from slider: ${e.message}")
                    }
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        
        // Setup DTMF button listeners for support calls
        setupDtmfButtonListeners()
    }
    
    /**
     * Setup DTMF button listeners for support calls
     * This allows users to navigate IVR menus by sending DTMF tones
     */
    private fun setupDtmfButtonListeners() {
        val currentCallType = sdk.kasookoo.ai.ui.MainActivity.getCurrentCallType()
        val isSupportCall = currentCallType == CallType.SUPPORT
        
        if (!isSupportCall) {
            Log.d(TAG, "DTMF not needed for non-support calls")
            return
        }
        
        Log.d(TAG, "🎵 Setting up DTMF button listeners for support call...")
        
        // Show DTMF toggle button for support calls
        binding.btnDtmfToggle.visibility = android.view.View.VISIBLE
        
        // DTMF toggle button
        binding.btnDtmfToggle.setOnClickListener {
            toggleDtmfDialPad()
        }
        
        // DTMF digit buttons
        binding.btnDtmf1.setOnClickListener { sendDtmfDigit("1") }
        binding.btnDtmf2.setOnClickListener { sendDtmfDigit("2") }
        binding.btnDtmf3.setOnClickListener { sendDtmfDigit("3") }
        binding.btnDtmf4.setOnClickListener { sendDtmfDigit("4") }
        binding.btnDtmf5.setOnClickListener { sendDtmfDigit("5") }
        binding.btnDtmf6.setOnClickListener { sendDtmfDigit("6") }
        binding.btnDtmf7.setOnClickListener { sendDtmfDigit("7") }
        binding.btnDtmf8.setOnClickListener { sendDtmfDigit("8") }
        binding.btnDtmf9.setOnClickListener { sendDtmfDigit("9") }
        binding.btnDtmf0.setOnClickListener { sendDtmfDigit("0") }
        binding.btnDtmfStar.setOnClickListener { sendDtmfDigit("*") }
        binding.btnDtmfHash.setOnClickListener { sendDtmfDigit("#") }
        
        Log.d(TAG, "✅ DTMF button listeners setup completed")
    }
    
    /**
     * Toggle DTMF dial pad visibility
     */
    private fun toggleDtmfDialPad() {
        val isVisible = binding.dtmfDialPadSection.visibility == android.view.View.VISIBLE
        
        if (isVisible) {
            binding.dtmfDialPadSection.visibility = android.view.View.GONE
            binding.btnDtmfToggle.setImageResource(R.drawable.ic_call)
            Log.d(TAG, "🎵 DTMF dial pad hidden")
        } else {
            binding.dtmfDialPadSection.visibility = android.view.View.VISIBLE
            binding.btnDtmfToggle.setImageResource(R.drawable.ic_call_modern)
            Log.d(TAG, "🎵 DTMF dial pad shown")
        }
    }
    
    /**
     * Send a single DTMF digit
     * @param digit The DTMF digit to send (0-9, *, #)
     */
    private fun sendDtmfDigit(digit: String) {
        try {
            Log.d(TAG, "🎵 Sending DTMF digit: '$digit'")
            
            // Check if DTMF is supported
            if (!liveKitManager.isDtmfSupported()) {
                Log.w(TAG, "⚠️ DTMF not supported in current call")
                updateDtmfStatus("DTMF not supported")
                return
            }
            
            // Send the DTMF digit
            val success = liveKitManager.sendDtmfDigit(digit)
            
            if (success) {
                Log.d(TAG, "✅ DTMF digit '$digit' sent successfully")
                updateDtmfStatus("✅ Sent: $digit")

                // Provide haptic feedback
                val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as android.os.Vibrator
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    vibrator.vibrate(android.os.VibrationEffect.createOneShot(100, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(100)
                }

                // Update status after a delay
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    updateDtmfStatus("🎵 DTMF Ready")
                }, 1500)

            } else {
                Log.e(TAG, "❌ Failed to send DTMF digit '$digit'")
                updateDtmfStatus("❌ Failed: $digit")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error sending DTMF digit: ${e.message}")
            updateDtmfStatus("Error: ${e.message}")
        }
    }
    
    /**
     * Update DTMF status text
     * @param status The status message to display
     */
    private fun updateDtmfStatus(status: String) {
        try {
            binding.tvDtmfStatus.text = status
            Log.d(TAG, "🎵 DTMF status updated: $status")
        } catch (e: Exception) {
            Log.e(TAG, "Error updating DTMF status: ${e.message}")
        }
    }
    
    /**
     * Check DTMF support status and update UI accordingly
     */
    private fun checkDtmfSupport() {
        try {
            val dtmfStatus = liveKitManager.getDtmfStatus()
            Log.d(TAG, "🎵 DTMF Status: ${dtmfStatus.reason}")
            Log.d(TAG, "   - Supported: ${dtmfStatus.isSupported}")
            Log.d(TAG, "   - Has Audio Track: ${dtmfStatus.hasAudioTrack}")
            Log.d(TAG, "   - Has DTMF Sender: ${dtmfStatus.hasDtmfSender}")
            
            if (dtmfStatus.isSupported) {
                val method = if (dtmfStatus.hasDtmfSender) "WebRTC DTMF" else "Audio Generation"
                updateDtmfStatus("🎵 DTMF Ready ($method)")
            } else {
                updateDtmfStatus("❌ DTMF: ${dtmfStatus.reason}")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error checking DTMF support: ${e.message}")
            updateDtmfStatus("DTMF Error")
        }
    }
    
    private fun observeCallState() {
        // Check if this is a support call by checking the stored call type
        val currentCallType = sdk.kasookoo.ai.ui.MainActivity.getCurrentCallType()
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
                    CallState.IN_CALL -> {
                        // Call is active - start periodic audio checks
                        Log.d(TAG, "Call is active, starting periodic audio checks...")
                        startPeriodicAudioChecks()
                    }
                    else -> {
                        // Continue call
                        Log.d(TAG, "Call state: $state - continuing call")
                    }
                }
            }
        }
    }
    
    private fun startPeriodicAudioChecks() {
        lifecycleScope.launch {
            try {
                // Wait a bit for call to stabilize
                kotlinx.coroutines.delay(2000)
                
                // Check audio every 10 seconds during active call
                while (liveKitManager.callState.value == CallState.IN_CALL && !isFinishing) {
                    Log.d(TAG, "🔍 Performing periodic audio check...")
                    checkAudioStatus()
                    
                    // Wait 10 seconds before next check
                    kotlinx.coroutines.delay(10000)
                }
                
                Log.d(TAG, "🔄 Periodic audio checks stopped - call ended or activity finishing")
                
            } catch (e: Exception) {
                Log.e(TAG, "Error in periodic audio checks: ${e.message}")
            }
        }
    }
    
    private fun toggleMute() {
        try {
            isMuted = !isMuted
            
            if (isMuted) {
                // Mute audio through LiveKit
                liveKitManager.muteAudio()
                
                // Also mute through AudioManager for local feedback
                audioManager.isMicrophoneMute = true
                
                Log.d(TAG, "🔇 Audio muted through LiveKit and AudioManager")
            } else {
                // Unmute audio through LiveKit
                liveKitManager.unmuteAudio()
                
                // Also unmute through AudioManager
                audioManager.isMicrophoneMute = false
                
                Log.d(TAG, "🔊 Audio unmuted through LiveKit and AudioManager")
            }
            
            // Verify mute state
            val liveKitMuted = !liveKitManager.isLocalMicrophoneEnabled()
            val audioManagerMuted = audioManager.isMicrophoneMute
            
            Log.d(TAG, "🔄 Mute state verification:")
            Log.d(TAG, "   - Expected muted: $isMuted")
            Log.d(TAG, "   - LiveKit muted: $liveKitMuted")
            Log.d(TAG, "   - AudioManager muted: $audioManagerMuted")
            
            // Update UI
            updateMuteButton()
            
        } catch (e: Exception) {
            Log.e(TAG, "Error toggling mute: ${e.message}")
            showError("Failed to toggle mute")
        }
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
                // Enable speaker with proper audio routing
                audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
                audioManager.isSpeakerphoneOn = true
                
                // Force audio routing to speaker
                audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION)
                audioManager.isSpeakerphoneOn = true
                
                // Additional audio routing for better speaker output
                try {
                    // Set audio stream volume to ensure speaker is audible
                    val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL)
                    audioManager.setStreamVolume(AudioManager.STREAM_VOICE_CALL, maxVolume, 0)
                    Log.d(TAG, "🔊 Speakerphone enabled with max volume: $maxVolume")
                } catch (e: Exception) {
                    Log.w(TAG, "⚠️ Could not set max volume: ${e.message}")
                }
                
                Log.d(TAG, "🔊 Speakerphone enabled with enhanced routing")
            } else {
                // Disable speaker and route to earpiece
                audioManager.isSpeakerphoneOn = false
                audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
                
                // Ensure audio goes to earpiece
                try {
                    // Set moderate volume for earpiece
                    val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL)
                    val moderateVolume = maxVolume / 2
                    audioManager.setStreamVolume(AudioManager.STREAM_VOICE_CALL, moderateVolume, 0)
                    Log.d(TAG, "🔇 Speakerphone disabled, routing to earpiece with volume: $moderateVolume")
                } catch (e: Exception) {
                    Log.w(TAG, "⚠️ Could not set moderate volume: ${e.message}")
                }
                
                Log.d(TAG, "🔇 Speakerphone disabled, audio routed to earpiece")
            }
            
            // Verify the change took effect immediately
            val actualSpeakerState = audioManager.isSpeakerphoneOn
            val actualAudioMode = audioManager.mode
            
            Log.d(TAG, "🔄 Speaker toggle verification:")
            Log.d(TAG, "   - Expected speaker: $isSpeakerOn, Actual: $actualSpeakerState")
            Log.d(TAG, "   - Audio mode: $actualAudioMode")
            
            if (actualSpeakerState != isSpeakerOn) {
                Log.w(TAG, "⚠️ Speaker state mismatch - forcing state...")
                forceSpeakerState(isSpeakerOn)
            }
            
            // Update UI immediately
            updateSpeakerButton()
            
            // Additional verification after a short delay
            lifecycleScope.launch {
                kotlinx.coroutines.delay(200) // 200ms delay for better verification
                
                val finalSpeakerState = audioManager.isSpeakerphoneOn
                val finalAudioMode = audioManager.mode
                
                Log.d(TAG, "✅ Final speaker state verification:")
                Log.d(TAG, "   - Speaker: $finalSpeakerState (expected: $isSpeakerOn)")
                Log.d(TAG, "   - Audio mode: $finalAudioMode")
                
                if (finalSpeakerState != isSpeakerOn) {
                    Log.w(TAG, "⚠️ Final speaker state still incorrect - forcing again...")
                    forceSpeakerState(isSpeakerOn)
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error toggling speaker: ${e.message}")
            showError("Failed to toggle speaker")
        }
    }
    
    private fun updateSpeakerButton() {
        Log.d(TAG, "🔄 Updating speaker button - isSpeakerOn: $isSpeakerOn")
        // Keep icon to reflect current speaker state, but slider visibility is controlled separately
        if (isSpeakerOn) {
            binding.btnSpeaker.setImageResource(R.drawable.ic_speaker_on)
            binding.btnSpeaker.setBackgroundResource(R.drawable.button_ripple_control)
        } else {
            binding.btnSpeaker.setImageResource(R.drawable.ic_speaker_off)
            binding.btnSpeaker.setBackgroundResource(R.drawable.button_ripple_decline)
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
        val currentCallType = sdk.kasookoo.ai.ui.MainActivity.getCurrentCallType()
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
        sdk.kasookoo.ai.ui.MainActivity.setEndingCallProgrammatically(true)
        
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
        abandonAudioFocus()
    }

    private fun testAudioRouting() {
        Log.d(TAG, "🧪 === TESTING AUDIO ROUTING ===")
        
        val currentCallType = sdk.kasookoo.ai.ui.MainActivity.getCurrentCallType()
        Log.d(TAG, "Current call type: $currentCallType")
        
        // Launch in coroutine scope to use delay
        lifecycleScope.launch {
            try {
                // Test speaker toggle
                Log.d(TAG, "Testing speaker toggle...")
                val originalSpeakerState = isSpeakerOn
                
                // Toggle speaker on
                if (!isSpeakerOn) {
                    Log.d(TAG, "🔄 Testing: Enable speaker...")
                    toggleSpeaker()
                    kotlinx.coroutines.delay(500)
                    
                    val speakerEnabled = audioManager.isSpeakerphoneOn
                    Log.d(TAG, "✅ Speaker enabled test: $speakerEnabled")
                    
                    // Toggle back to original state
                    if (originalSpeakerState != isSpeakerOn) {
                        Log.d(TAG, "🔄 Testing: Disable speaker...")
                        toggleSpeaker()
                        kotlinx.coroutines.delay(500)
                        
                        val speakerDisabled = !audioManager.isSpeakerphoneOn
                        Log.d(TAG, "✅ Speaker disabled test: $speakerDisabled")
                    }
                } else {
                    // Speaker is on, test disabling
                    Log.d(TAG, "🔄 Testing: Disable speaker...")
                    toggleSpeaker()
                    kotlinx.coroutines.delay(500)
                    
                    val speakerDisabled = !audioManager.isSpeakerphoneOn
                    Log.d(TAG, "✅ Speaker disabled test: $speakerDisabled")
                    
                    // Toggle back to original state
                    if (originalSpeakerState != isSpeakerOn) {
                        Log.d(TAG, "🔄 Testing: Enable speaker...")
                        toggleSpeaker()
                        kotlinx.coroutines.delay(500)
                        
                        val speakerEnabled = audioManager.isSpeakerphoneOn
                        Log.d(TAG, "✅ Speaker enabled test: $speakerEnabled")
                    }
                }
                
                // Test mute functionality
                Log.d(TAG, "Testing mute functionality...")
                val originalMuteState = isMuted
                
                if (!isMuted) {
                    Log.d(TAG, "🔄 Testing: Enable mute...")
                    toggleMute()
                    kotlinx.coroutines.delay(500)
                    
                    val muteEnabled = isMuted
                    Log.d(TAG, "✅ Mute enabled test: $muteEnabled")
                    
                    // Toggle back to original state
                    if (originalMuteState != isMuted) {
                        Log.d(TAG, "🔄 Testing: Disable mute...")
                        toggleMute()
                        kotlinx.coroutines.delay(500)
                        
                        val muteDisabled = !isMuted
                        Log.d(TAG, "✅ Mute disabled test: $muteDisabled")
                    }
                } else {
                    // Mute is on, test disabling
                    Log.d(TAG, "🔄 Testing: Disable mute...")
                    toggleMute()
                    kotlinx.coroutines.delay(500)
                    
                    val muteDisabled = !isMuted
                    Log.d(TAG, "✅ Mute disabled test: $muteDisabled")
                    
                    // Toggle back to original state
                    if (originalMuteState != isMuted) {
                        Log.d(TAG, "🔄 Testing: Enable mute...")
                        toggleMute()
                        kotlinx.coroutines.delay(500)
                        
                        val muteEnabled = isMuted
                        Log.d(TAG, "✅ Mute enabled test: $muteEnabled")
                    }
                }
                
                // Final audio status check
                Log.d(TAG, "Final audio status after testing:")
                checkAudioStatus()
                
                Log.d(TAG, "🧪 === AUDIO ROUTING TEST COMPLETE ===")
                
            } catch (e: Exception) {
                Log.e(TAG, "Error during audio routing test: ${e.message}")
            }
        }
    }
} 