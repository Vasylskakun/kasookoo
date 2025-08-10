package com.yuave.kasookoo.ui

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import com.yuave.kasookoo.CallApplication
import com.yuave.kasookoo.R
import com.yuave.kasookoo.core.CallState
import com.yuave.kasookoo.core.LiveKitManager
import com.yuave.kasookoo.databinding.ActivityRingingBinding
import kotlinx.coroutines.launch
import com.yuave.kasookoo.core.CallType

class RingingActivity : AppCompatActivity() {
    
    companion object {
        private const val TAG = "RingingActivity"
    }
    
    private lateinit var binding: ActivityRingingBinding
    private lateinit var liveKitManager: LiveKitManager
    private var isCustomer: Boolean = true
    private var isIncomingCall: Boolean = false
    private var suppressFinishUntilConnected: Boolean = false
    private val tokenRequestScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    @Volatile private var isFetchingToken: Boolean = false
    private var autoAcceptFromNotification: Boolean = false
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRingingBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Get user type from intent
        isCustomer = intent.getBooleanExtra("isCustomer", true)
        
        // Initialize LiveKit manager
        liveKitManager = (application as CallApplication).liveKitManager
        
        setupUI()
        setupClickListeners()
        observeCallState()
        
        // Resolve local role from stored data
        runCatching {
            val userType = com.yuave.kasookoo.data.UserDataManager(this).getUserType()
            if (!userType.isNullOrBlank()) {
                isCustomer = userType.equals("customer", ignoreCase = true)
            }
        }

        // Check if this activity was launched for an incoming call
        isIncomingCall = intent.getBooleanExtra("is_incoming_call", false)
        suppressFinishUntilConnected = isIncomingCall

        // Check if we should auto-accept the call (from notification action)
        autoAcceptFromNotification = intent.getBooleanExtra("auto_accept", false)
        if (autoAcceptFromNotification) {
            Log.d(TAG, "🔄 Auto-accepting call from notification")
            answerCall()
        }
    }
    
    private fun setupUI() {
        // Check if this is a support call by checking the stored call type
        val currentCallType = com.yuave.kasookoo.ui.MainActivity.getCurrentCallType()
        val isSupportCall = currentCallType == CallType.SUPPORT
        
        if (isSupportCall) {
            // Support call UI
            binding.tvCallingStatus.text = "Calling Support"
            binding.tvContactName.text = "Customer Support"
            binding.tvCallInfo.text = "Connecting to support team..."
            // Set support icon (you might want to add a support icon)
            binding.ivContactAvatar.setImageResource(R.drawable.ic_person)
        } else if (isCustomer) {
            // Customer making outgoing call UI
            binding.tvCallingStatus.text = "Connecting..."
            binding.tvContactName.text = "Driver"
            binding.tvCallInfo.text = "Establishing connection to room"
            // Set driver icon
            binding.ivContactAvatar.setImageResource(R.drawable.ic_driver_modern)
        } else {
            // Driver UI - check if driver is calling customer or receiving call
            val isIncomingCall = intent.getBooleanExtra("is_incoming_call", false)
            
            if (isIncomingCall) {
                // Driver receiving incoming call
                binding.tvContactName.text = "Customer"
                binding.ivContactAvatar.setImageResource(R.drawable.ic_person)

                if (autoAcceptFromNotification) {
                    // If user accepted from notification action, don't show accept screen
                    binding.tvCallingStatus.text = "Connecting..."
                    binding.tvCallInfo.text = "Establishing connection to room"
                    binding.answerCard.visibility = android.view.View.GONE
                } else {
                    binding.tvCallingStatus.text = "Incoming Call"
                    binding.tvCallInfo.text = "Touch to answer the call"
                    binding.answerCard.visibility = android.view.View.VISIBLE
                }
            } else {
                // Driver making outgoing call to customer
                binding.tvCallingStatus.text = "Calling Customer"
                binding.tvContactName.text = "Customer"
                binding.tvCallInfo.text = "Establishing connection to room"
                binding.ivContactAvatar.setImageResource(R.drawable.ic_person)
                
                // Hide answer button for driver making call
                binding.answerCard.visibility = android.view.View.GONE
            }
        }
    }
    
    private fun setupClickListeners() {
        binding.btnEndCall.setOnClickListener {
            endCall()
        }
        
        binding.btnAnswer.setOnClickListener {
            answerCall()
        }
    }
    
    private fun observeCallState() {
        // Check if this is a support call by checking the stored call type
        val currentCallType = com.yuave.kasookoo.ui.MainActivity.getCurrentCallType()
        val isSupportCall = currentCallType == CallType.SUPPORT
        
        if (isSupportCall) {
            // For support calls, observe LiveKit states like regular calls
            lifecycleScope.launch {
                liveKitManager.callState.collect { state ->
                    Log.d(TAG, "Support call state changed: $state")
                    
                    when (state) {
                        CallState.CONNECTING -> {
                            // Still connecting to room
                            Log.d(TAG, "Support call connecting to room...")
                            updateUIForConnecting()
                        }
                        CallState.CONNECTED -> {
                            // Connected to room, waiting for support to join
                            Log.d(TAG, "Support call connected to room, waiting for support...")
                            updateUIForWaiting()
                        }
                        CallState.WAITING_FOR_ACCEPTANCE -> {
                            // Waiting for support to accept the call
                            Log.d(TAG, "Support call waiting for acceptance...")
                            updateUIForWaitingForAcceptance()
                            
                            // Additional safety check: if we have remote participants, transition to IN_CALL
                            val hasRemoteParticipants = liveKitManager.hasRemoteParticipants()
                            if (hasRemoteParticipants) {
                                Log.d(TAG, "🔄 Support call: Remote participants detected while waiting, forcing IN_CALL transition")
                                liveKitManager.forceInCallState()
                            }
                        }
                        CallState.WAITING_FOR_DRIVER_ACCEPTANCE -> {
                            // Waiting for driver to accept the call after customer joined
                            Log.d(TAG, "Support call waiting for driver acceptance...")
                            updateUIForWaitingForDriverAcceptance()
                        }
                        CallState.IN_CALL -> {
                            // Support call connected - navigate to call screen immediately
                            Log.d(TAG, "🎉 Support call IN_CALL state reached, navigating to call screen")
                            Log.d(TAG, "👤 Is customer: $isCustomer")
                            Log.d(TAG, "📞 Current call type: ${com.yuave.kasookoo.ui.MainActivity.getCurrentCallType()}")
                            navigateToCallActivity()
                        }
                        CallState.IDLE -> {
                            // Don't finish immediately for incoming-call flow; we're about to fetch token
                            if (!suppressFinishUntilConnected) {
                                finish()
                            } else {
                                Log.d(TAG, "⏸️ Suppressing finish on IDLE (incoming call flow)")
                            }
                        }
                        CallState.ERROR -> {
                            // Connection error
                            showError("Support call failed")
                            finish()
                        }
                        else -> {
                            // Stay on ringing screen
                        }
                    }
                }
            }
            
            // Additional safety mechanism: Periodic check for stuck support calls
            lifecycleScope.launch {
                delay(5000) // Wait 5 seconds after support call starts
                while (isActive && !isFinishing) {
                    delay(2000) // Check every 2 seconds
                    
                    // If we're still waiting for acceptance but have remote participants, force transition
                    if (liveKitManager.callState.value == CallState.WAITING_FOR_ACCEPTANCE && 
                        liveKitManager.hasRemoteParticipants()) {
                        Log.d(TAG, "🔄 Support call safety check: Remote participants present but still waiting, forcing IN_CALL")
                        liveKitManager.forceInCallState()
                        break
                    }
                }
            }
        } else {
            // For regular LiveKit calls, observe call state
            lifecycleScope.launch {
                liveKitManager.callState.collect { state ->
                    Log.d(TAG, "Call state changed: $state")
                    
                    when (state) {
                        CallState.CONNECTING -> {
                            // Still connecting to room
                            Log.d(TAG, "Calling to support...")
                            updateUIForConnecting()
                        }
                        CallState.CONNECTED -> {
                            // Connected to room, waiting for other participant
                            Log.d(TAG, "Connected to support")
                            updateUIForWaiting()
                        }
                        CallState.WAITING_FOR_ACCEPTANCE -> {
                            // Waiting for driver/support to accept the call
                            Log.d(TAG, "Waiting for acceptance...")
                            updateUIForWaitingForAcceptance()
                        }
                        CallState.WAITING_FOR_DRIVER_ACCEPTANCE -> {
                            // Waiting for driver to accept the call after customer joined
                            Log.d(TAG, "Waiting for driver acceptance...")
                            if (autoAcceptFromNotification) {
                                // Skip any waiting UI if call came from notification accept
                                Log.d(TAG, "🚗 Auto-accepted from notification → skipping waiting UI")
                                binding.answerCard.visibility = android.view.View.GONE
                                binding.tvCallingStatus.text = "Connecting..."
                                binding.tvCallInfo.text = "Establishing connection to room"
                            } else {
                                updateUIForWaitingForDriverAcceptance()
                            }
                            // If this device is driver (callee), auto-accept to move to IN_CALL
                            if (!isCustomer) {
                                Log.d(TAG, "🚗 Driver reached WAITING_FOR_DRIVER_ACCEPTANCE → auto-accepting")
                                liveKitManager.acceptCall()
                            }
                        }
                        CallState.IN_CALL -> {
                            // Call connected - navigate to call screen immediately
                            Log.d(TAG, "🎉 IN_CALL state reached, navigating to call screen")
                            Log.d(TAG, "👤 Is customer: $isCustomer")
                            Log.d(TAG, "📞 Current call type: ${com.yuave.kasookoo.ui.MainActivity.getCurrentCallType()}")
                            navigateToCallActivity()
                        }
                        CallState.IDLE -> {
                            // Don't finish immediately for incoming-call flow; we're about to fetch token
                            if (!suppressFinishUntilConnected) {
                                finish()
                            } else {
                                Log.d(TAG, "⏸️ Suppressing finish on IDLE (incoming call flow)")
                            }
                        }
                        CallState.ERROR -> {
                            // Connection error
                            showError("Call failed")
                            finish()
                        }
                        else -> {
                            // Stay on ringing screen
                        }
                    }
                }
            }
        }
    }
    
    private fun updateUIForConnecting() {
        binding.tvCallingStatus.text = "Connecting..."
        binding.tvCallInfo.text = "Establishing connection..."
    }
    
    private fun updateUIForWaiting() {
        binding.tvCallingStatus.text = "Connected"
        binding.tvCallInfo.text = "Waiting for other participant"
    }
    
    private fun updateUIForWaitingForAcceptance() {
        val currentCallType = com.yuave.kasookoo.ui.MainActivity.getCurrentCallType()
        
        if (currentCallType == CallType.DRIVER) {
            // Customer is waiting for driver to accept
            binding.tvCallingStatus.text = "Calling Driver"
            binding.tvCallInfo.text = "Waiting for driver to accept the call..."
        } else if (currentCallType == CallType.SUPPORT) {
            // Customer is waiting for support to accept
            binding.tvCallingStatus.text = "Calling Support"
            binding.tvCallInfo.text = "Connected to support team, waiting for agent to join..."
        } else {
            // Generic waiting message
            binding.tvCallingStatus.text = "Calling Support"
            binding.tvCallInfo.text = "Waiting for acceptance..."
        }
    }
    
    private fun updateUIForWaitingForDriverAcceptance() {
        val currentCallType = com.yuave.kasookoo.ui.MainActivity.getCurrentCallType()
        
        if (currentCallType == CallType.DRIVER) {
            // Driver is waiting - customer has joined but driver hasn't accepted yet
            binding.tvCallingStatus.text = "Incoming Call"
            binding.tvCallInfo.text = "Customer has joined. Touch to answer the call"
            // Show answer button for driver
            binding.answerCard.visibility = android.view.View.VISIBLE
        } else if (currentCallType == CallType.SUPPORT) {
            // Customer is waiting for support to accept
            binding.tvCallingStatus.text = "Calling Support"
            binding.tvCallInfo.text = "Connected to support team, waiting for agent to join..."
        } else {
            // Customer is waiting for driver to accept
            binding.tvCallingStatus.text = "Calling Driver"
            binding.tvCallInfo.text = "Driver has joined. Waiting for driver to accept..."
        }
    }
    
    private fun answerCall() {
        Log.d(TAG, "Call answered by user")
        binding.answerCard.visibility = android.view.View.GONE
        binding.tvCallInfo.text = "Connecting..."
        
        // Check if this is an incoming call from notification
        val isIncomingCall = intent.getBooleanExtra("is_incoming_call", false)
        val roomName = intent.getStringExtra("room_name") ?: ""
        
        if (isIncomingCall && roomName.isNotEmpty()) {
            // This is from notification - need to get LiveKit token using room name
            Log.d(TAG, "📞 Incoming call from notification - getting token for room: $roomName")
            getTokenAndConnectToRoom(roomName)
        } else {
            // This is regular call flow - just accept
            Log.d(TAG, "📞 Regular call flow - accepting call")
            liveKitManager.acceptCall()
        }
    }
    
    private fun getTokenAndConnectToRoom(roomName: String) {
        if (isFetchingToken) {
            Log.d(TAG, "⏳ Token request already in progress, skipping duplicate")
            return
        }
        isFetchingToken = true
        tokenRequestScope.launch {
            try {
                val userDataManager = com.yuave.kasookoo.data.UserDataManager(this@RingingActivity)
                val repository = com.yuave.kasookoo.data.CallRepository()
                
                val participantIdentity = getParticipantIdentity()
                val participantIdentityName = getParticipantIdentityName()
                val participantIdentityType = getParticipantIdentityType()
                val userId = userDataManager.getUserId() ?: throw Exception("User ID not found")
                
                Log.d(TAG, "📞 Getting called token for:")
                Log.d(TAG, "   - Room: $roomName")
                Log.d(TAG, "   - Participant: $participantIdentity")
                Log.d(TAG, "   - Name: $participantIdentityName")
                Log.d(TAG, "   - Type: $participantIdentityType")
                Log.d(TAG, "   - User ID: $userId")
                
                val tokenResult = repository.getCalledLiveKitToken(
                    roomName, participantIdentity, participantIdentityName, participantIdentityType, userId
                )
                
                tokenResult.onSuccess { tokenResponse ->
                    Log.d(TAG, "✅ Got called token, connecting to room...")
                    // We can now allow normal lifecycle behavior
                    suppressFinishUntilConnected = false
                    
                    // Determine call type based on user type
                    val callType = if (isCustomer) {
                        com.yuave.kasookoo.core.CallType.CUSTOMER
                    } else {
                        com.yuave.kasookoo.core.CallType.DRIVER
                    }

                    // Ensure global call type is set for downstream UI (e.g., CallActivity end flow)
                    com.yuave.kasookoo.ui.MainActivity.setCurrentCallType(callType)
                    
                    // Connect to LiveKit room - audio setup will be handled by LiveKitManager
                    // (same as caller does)
                    liveKitManager.connectToRoom(
                        token = tokenResponse.accessToken,
                        wsUrl = tokenResponse.wsUrl,
                        roomName = roomName,
                        callType = callType
                    )
                    isFetchingToken = false
                }.onFailure { error ->
                    Log.e(TAG, "❌ Failed to get called token", error)
                    withContext(Dispatchers.Main) {
                        showError("Failed to join call: ${error.message}")
                        // Keep activity open to allow retry if needed
                    }
                    isFetchingToken = false
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error getting token for incoming call", e)
                withContext(Dispatchers.Main) {
                    showError("Error joining call: ${e.message}")
                    // Keep activity open to allow retry if needed
                }
                isFetchingToken = false
            }
        }
    }
    
    // Helper methods to get participant info
    private fun getLocalUserType(): String {
        val udm = com.yuave.kasookoo.data.UserDataManager(this)
        val stored = udm.getUserType()?.lowercase()
        val resolved = stored ?: if (isCustomer) "customer" else "driver"
        Log.d(TAG, "Resolved local user type for token requests: $resolved (stored=$stored, isCustomer=$isCustomer)")
        return resolved
    }

    private fun getParticipantIdentity(): String {
        val participantName = getParticipantIdentityName()
        val localType = getLocalUserType()
        val prefix = if (localType == "customer") "customer_" else "driver_"
        val identity = prefix + participantName.replace(" ", "_").lowercase()
        Log.d(TAG, "Generated participant identity: $identity (from name: $participantName, localType: $localType)")
        return identity
    }
    
    private fun getParticipantIdentityName(): String {
        val userDataManager = com.yuave.kasookoo.data.UserDataManager(this)
        return userDataManager.getFullName() ?: "User"
    }
    
    private fun getParticipantIdentityType(): String {
        return getLocalUserType()
    }
    

    
    private fun endCall() {
        Log.d(TAG, "Call ended by user")
        liveKitManager.disconnect()
        finish()
    }
    
    private fun navigateToCallActivity() {
        val intent = Intent(this, CallActivity::class.java).apply {
            putExtra("isCustomer", isCustomer)
        }
        startActivity(intent)
        finish()
    }
    
    private fun showError(message: String) {
        android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_SHORT).show()
    }
    
    override fun onBackPressed() {
        // Prevent back button during ringing
        endCall()
        super.onBackPressed()
    }
} 