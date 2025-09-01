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
    private var isSilentNotification: Boolean = false

    // Silent notification data storage
    private var silentCallType: String? = null
    private var silentRoomName: String? = null
    private var silentParticipantIdentity: String? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRingingBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Get user type from intent
        isCustomer = intent.getBooleanExtra("isCustomer", true)
        
        // Initialize LiveKit manager
        liveKitManager = (application as CallApplication).liveKitManager
        
        // Resolve local role from stored data
        runCatching {
            val userType = com.yuave.kasookoo.data.UserDataManager(this).getUserType()
            if (!userType.isNullOrBlank()) {
                isCustomer = userType.equals("customer", ignoreCase = true)
            }
        }

        // Check if this was launched from a silent notification FIRST
        // This must happen before setting suppressFinishUntilConnected
        isSilentNotification = intent.getBooleanExtra("is_silent_notification", false)

        // Check if this activity was launched for an incoming call
        isIncomingCall = intent.getBooleanExtra("is_incoming_call", false)

        // Check if we should auto-accept the call (from notification action)
        autoAcceptFromNotification = intent.getBooleanExtra("auto_accept", false)

        // Set suppress finish flag - for silent notifications OR incoming calls
        suppressFinishUntilConnected = isIncomingCall || isSilentNotification

        if (autoAcceptFromNotification) {
            Log.d(TAG, "🔄 Auto-accepting call from notification")
            answerCall()
        }

        if (isSilentNotification) {
            Log.d(TAG, "🔕 Launched from silent notification - no visible notification shown")
            Log.d(TAG, "🔕 Silent notification: Suppressing finish until call connects (suppressFinishUntilConnected=$suppressFinishUntilConnected)")

            // Extract call data and start the call process
            handleSilentNotificationCall()
        }

        // Initialize LiveKit manager and setup UI
        setupUI()
        setupClickListeners()
        observeCallState()

        Log.d(TAG, "RingingActivity setup complete:")
        Log.d(TAG, "  - isSilentNotification: $isSilentNotification")
        Log.d(TAG, "  - isIncomingCall: $isIncomingCall")
        Log.d(TAG, "  - suppressFinishUntilConnected: $suppressFinishUntilConnected")
        Log.d(TAG, "  - autoAcceptFromNotification: $autoAcceptFromNotification")
    }

    /**
     * Handle silent notification call - extract data and start call process
     */
    private fun handleSilentNotificationCall() {
        try {
            Log.d(TAG, "🔕 Processing silent notification call data")

            // Extract call data from intent (passed from FirebaseMessagingService)
            val callType = intent.getStringExtra("call_type") ?: "unknown"
            val callerName = intent.getStringExtra("caller_name") ?: "Unknown Caller"
            val roomName = intent.getStringExtra("room_name") ?: ""
            val participantIdentity = intent.getStringExtra("participant_identity") ?: ""

            Log.d(TAG, "🔕 Silent call data extracted:")
            Log.d(TAG, "   - Call Type: $callType")
            Log.d(TAG, "   - Caller: $callerName")
            Log.d(TAG, "   - Room: $roomName")
            Log.d(TAG, "   - Participant ID: $participantIdentity")

            // Store the call data for later use when user accepts
            this.silentCallType = callType
            this.silentRoomName = roomName
            this.silentParticipantIdentity = participantIdentity

            // Set up the call type in MainActivity (for UI setup only)
            when (callType) {
                "customer" -> {
                    com.yuave.kasookoo.ui.MainActivity.setCurrentCallType(CallType.CUSTOMER)
                    Log.d(TAG, "🔕 Set call type to CUSTOMER")
                }
                "driver" -> {
                    com.yuave.kasookoo.ui.MainActivity.setCurrentCallType(CallType.DRIVER)
                    Log.d(TAG, "🔕 Set call type to DRIVER")
                }
                "support" -> {
                    com.yuave.kasookoo.ui.MainActivity.setCurrentCallType(CallType.SUPPORT)
                    Log.d(TAG, "🔕 Set call type to SUPPORT")
                }
                else -> {
                    Log.w(TAG, "🔕 Unknown call type: $callType")
                    finish()
                    return
                }
            }

            // Store participant identity for support calls
            if (callType == "support") {
                com.yuave.kasookoo.ui.MainActivity.setCurrentSupportCallParticipantIdentity(participantIdentity)
                com.yuave.kasookoo.ui.MainActivity.setCurrentSupportCallRoomName(roomName)
                Log.d(TAG, "🔕 Stored support call data: $participantIdentity, $roomName")
            }

            // DON'T start connection yet - wait for user to accept the call
            Log.d(TAG, "🔕 Silent notification processed - waiting for user to accept call")

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error handling silent notification call: ${e.message}")
            finish()
        }
    }

    /**
     * Start the call connection process for silent notifications (called after user accepts)
     */
    private fun startSilentCallConnection(roomName: String, callType: String, participantIdentity: String) {
        Log.d(TAG, "📞 Starting call connection process for accepted silent notification")
        Log.d(TAG, "   - Call Type: $callType")
        Log.d(TAG, "   - Room: $roomName")
        Log.d(TAG, "   - Participant: $participantIdentity")
        Log.d(TAG, "   - Is Customer: $isCustomer")

        // This mimics the traditional notification flow but for silent notifications
        // We'll get the LiveKit token and connect to the room
        when (callType) {
            "customer" -> {
                // Driver receiving customer call - need to get called token
                if (!isCustomer) { // If we're the driver
                    Log.d(TAG, "📞 Driver receiving customer call - getting called token")
                    getCalledTokenForSilentCall(roomName, participantIdentity, "driver")
                } else {
                    Log.e(TAG, "❌ Invalid call type configuration: customer call but user is customer")
                }
            }
            "driver" -> {
                // Customer receiving driver call - need to get called token
                if (isCustomer) { // If we're the customer
                    Log.d(TAG, "📞 Customer receiving driver call - getting called token")
                    getCalledTokenForSilentCall(roomName, participantIdentity, "customer")
                } else {
                    Log.e(TAG, "❌ Invalid call type configuration: driver call but user is not customer")
                }
            }
            "support" -> {
                // Support call - connect directly (no token needed for receiver)
                Log.d(TAG, "📞 Support call - connecting to room directly")
                connectToSupportCall(roomName)
            }
            else -> {
                Log.e(TAG, "❌ Unknown call type for silent notification: $callType")
            }
        }
    }

    /**
     * Get called token for silent notification call (called after user accepts)
     */
    private fun getCalledTokenForSilentCall(roomName: String, participantIdentity: String, participantType: String) {
        Log.d(TAG, "📞 Getting called token for accepted silent notification call")

        // Use the same token fetching logic as traditional notifications
        val userDataManager = com.yuave.kasookoo.data.UserDataManager(this)
        val userId = userDataManager.getUserId()

        if (userId.isNullOrBlank()) {
            Log.e(TAG, "❌ No user ID found for silent call")
            finish()
            return
        }

        // Get called token using repository
        val repository = com.yuave.kasookoo.data.CallRepository()
        val participantName = userDataManager.getFullName() ?: "User"

        Log.d(TAG, "📞 Fetching called token:")
        Log.d(TAG, "   - Room: $roomName")
        Log.d(TAG, "   - Participant ID: $participantIdentity")
        Log.d(TAG, "   - Participant Type: $participantType")
        Log.d(TAG, "   - User ID: $userId")

        tokenRequestScope.launch {
            try {
                isFetchingToken = true

                val tokenResult = repository.getCalledLiveKitToken(
                    roomName = roomName,
                    participantIdentity = participantIdentity,
                    participantIdentityName = participantName,
                    participantIdentityType = participantType,
                    calledUserId = userId
                )

                tokenResult.onSuccess { tokenResponse ->
                    Log.d(TAG, "✅ Got called token for accepted silent notification")
                    connectToRoomWithToken(tokenResponse.accessToken, tokenResponse.wsUrl, roomName, participantType)
                }.onFailure { error ->
                    Log.e(TAG, "❌ Failed to get called token for accepted silent notification: ${error.message}")
                    finish()
                }

            } catch (e: Exception) {
                Log.e(TAG, "❌ Error in silent call token request: ${e.message}")
                finish()
            } finally {
                isFetchingToken = false
            }
        }
    }

    /**
     * Connect to room with token (for non-support calls)
     */
    private fun connectToRoomWithToken(token: String, wsUrl: String, roomName: String, callType: String) {
        Log.d(TAG, "🔕 Connecting to room with token for silent call")

        val liveKitCallType = when (callType) {
            "customer" -> CallType.CUSTOMER
            "driver" -> CallType.DRIVER
            else -> CallType.CUSTOMER
        }

        liveKitManager.setCallType(liveKitCallType)

        lifecycleScope.launch {
            try {
                liveKitManager.connectToRoom(token, wsUrl, roomName, liveKitCallType)
                Log.d(TAG, "✅ Silent call connection initiated")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Failed to connect silent call: ${e.message}")
                finish()
            }
        }
    }

    /**
     * Connect to support call (simplified for silent notifications)
     */
    private fun connectToSupportCall(roomName: String) {
        Log.d(TAG, "🔕 Connecting to support call")

        liveKitManager.setCallType(CallType.SUPPORT)

        // For support calls, we might need a different approach
        // For now, just set the call type and let the UI handle it
        Log.d(TAG, "✅ Support call type set for silent notification")
    }

            private fun setupUI() {
        // Check if this is a support call by checking the stored call type
        val currentCallType = com.yuave.kasookoo.ui.MainActivity.getCurrentCallType()
        val isSupportCall = currentCallType == CallType.SUPPORT

        Log.d(TAG, "🎨 Setting up UI:")
        Log.d(TAG, "   - Current Call Type: $currentCallType")
        Log.d(TAG, "   - Is Support Call: $isSupportCall")
        Log.d(TAG, "   - Is Silent Notification: $isSilentNotification")
        Log.d(TAG, "   - Is Customer: $isCustomer")

        if (isSupportCall) {
            // Support call UI
            Log.d(TAG, "🎨 Setting up support call UI")
            binding.tvCallingStatus.text = "Calling Support"
            binding.tvContactName.text = "Customer Support"
            binding.tvCallInfo.text = if (isSilentNotification) "Silent notification - Connecting to support team..." else "Connecting to support team..."
            // Set support icon (you might want to add a support icon)
            binding.ivContactAvatar.setImageResource(R.drawable.ic_person)
            binding.answerCard.visibility = android.view.View.GONE
        } else if (isSilentNotification) {
            // Handle silent notification UI based on call direction (INCOMING calls only)
            Log.d(TAG, "🎨 Setting up silent notification UI for incoming call")
            setupSilentNotificationUI(currentCallType)
        } else {
            // Handle outgoing call UI (caller who initiated the call)
            Log.d(TAG, "🎨 Setting up outgoing call UI for caller")
            setupOutgoingCallUI(currentCallType)
        }
    }

    private fun setupOutgoingCallUI(callType: CallType?) {
        when (callType) {
            CallType.CUSTOMER -> {
                // Driver calling customer (outgoing call)
                if (!isCustomer) {
                    Log.d(TAG, "📞 Driver making outgoing call to customer")
                    binding.tvCallingStatus.text = "Calling Customer"
                    binding.tvContactName.text = "Customer"
                    binding.tvCallInfo.text = "Establishing connection to room"
                    binding.ivContactAvatar.setImageResource(R.drawable.ic_person)
                    binding.answerCard.visibility = android.view.View.GONE
                } else {
                    Log.w(TAG, "❌ Invalid call configuration: customer call type but user is customer")
                }
            }
            CallType.DRIVER -> {
                // Customer calling driver (outgoing call)
                if (isCustomer) {
                    Log.d(TAG, "📞 Customer making outgoing call to driver")
                    binding.tvCallingStatus.text = "Calling Driver"
                    binding.tvContactName.text = "Driver"
                    binding.tvCallInfo.text = "Establishing connection to room"
                    binding.ivContactAvatar.setImageResource(R.drawable.ic_driver_modern)
                    binding.answerCard.visibility = android.view.View.GONE
                } else {
                    Log.w(TAG, "❌ Invalid call configuration: driver call type but user is not customer")
                }
            }
            else -> {
                // Fallback for unknown call types
                Log.w(TAG, "⚠️ Unknown outgoing call type: $callType")
                binding.tvCallingStatus.text = "Calling..."
                binding.tvContactName.text = "Contact"
                binding.tvCallInfo.text = "Establishing connection..."
                binding.ivContactAvatar.setImageResource(R.drawable.ic_person)
                binding.answerCard.visibility = android.view.View.GONE
            }
        }
    }

    private fun setupSilentNotificationUI(callType: CallType?) {
        Log.d(TAG, "🔕 Setting up silent notification UI:")
        Log.d(TAG, "   - Call Type: $callType")
        Log.d(TAG, "   - Is Customer: $isCustomer")

        // For silent notifications, the call type indicates who initiated the call
        // We need to determine the UI based on who is receiving vs who is calling
        val isReceivingCall = true // All silent notifications are for receivers

        if (isReceivingCall) {
            // This device is receiving a call - show incoming call UI
            when (callType) {
                CallType.CUSTOMER -> {
                    // Received customer_incoming_call - customer is calling this device
                    Log.d(TAG, "🔕 Silent notification: Receiving call from customer")
                    Log.d(TAG, "   → Setting: Incoming Call + Customer + Accept buttons")
                    binding.tvCallingStatus.text = "Incoming Call"
                    binding.tvContactName.text = "Customer"
                    binding.tvCallInfo.text = "Silent notification - Touch to answer"
                    binding.ivContactAvatar.setImageResource(R.drawable.ic_person)
                    binding.answerCard.visibility = android.view.View.VISIBLE
                }
                CallType.DRIVER -> {
                    // Received driver_incoming_call - driver is calling this device
                    Log.d(TAG, "🔕 Silent notification: Receiving call from driver")
                    Log.d(TAG, "   → Setting: Incoming Call + Driver + Accept buttons")
                    binding.tvCallingStatus.text = "Incoming Call"
                    binding.tvContactName.text = "Driver"
                    binding.tvCallInfo.text = "Silent notification - Touch to answer"
                    binding.ivContactAvatar.setImageResource(R.drawable.ic_driver_modern)
                    binding.answerCard.visibility = android.view.View.VISIBLE
                }
                else -> {
                    // Fallback for unknown call types
                    Log.w(TAG, "🔕 Silent notification: Unknown call type $callType")
                    Log.d(TAG, "   → Setting: Incoming Call + Unknown + Accept buttons")
                    binding.tvCallingStatus.text = "Incoming Call"
                    binding.tvContactName.text = "Unknown"
                    binding.tvCallInfo.text = "Silent notification - Touch to answer"
                    binding.ivContactAvatar.setImageResource(R.drawable.ic_person)
                    binding.answerCard.visibility = android.view.View.VISIBLE
                }
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
                            // Also don't finish for silent notifications until call connects
                            if (!suppressFinishUntilConnected && !isSilentNotification) {
                                Log.d(TAG, "🏁 Finishing on IDLE (normal flow)")
                                Log.d(TAG, "  - suppressFinishUntilConnected: $suppressFinishUntilConnected")
                                Log.d(TAG, "  - isSilentNotification: $isSilentNotification")
                                Log.d(TAG, "  - isIncomingCall: $isIncomingCall")
                                finish()
                            } else {
                                Log.d(TAG, "⏸️ Suppressing finish on IDLE (incoming call or silent notification flow)")
                                Log.d(TAG, "  - suppressFinishUntilConnected: $suppressFinishUntilConnected")
                                Log.d(TAG, "  - isSilentNotification: $isSilentNotification")
                                Log.d(TAG, "  - isIncomingCall: $isIncomingCall")
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
                            // Also don't finish for silent notifications until call connects
                            if (!suppressFinishUntilConnected && !isSilentNotification) {
                                Log.d(TAG, "🏁 Finishing on IDLE (normal flow)")
                                Log.d(TAG, "  - suppressFinishUntilConnected: $suppressFinishUntilConnected")
                                Log.d(TAG, "  - isSilentNotification: $isSilentNotification")
                                Log.d(TAG, "  - isIncomingCall: $isIncomingCall")
                                finish()
                            } else {
                                Log.d(TAG, "⏸️ Suppressing finish on IDLE (incoming call or silent notification flow)")
                                Log.d(TAG, "  - suppressFinishUntilConnected: $suppressFinishUntilConnected")
                                Log.d(TAG, "  - isSilentNotification: $isSilentNotification")
                                Log.d(TAG, "  - isIncomingCall: $isIncomingCall")
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

        // Check if this is a silent notification call
        if (isSilentNotification && silentCallType != null && silentRoomName != null && silentParticipantIdentity != null) {
            Log.d(TAG, "📞 Silent notification call accepted - starting connection")
            Log.d(TAG, "   - Type: $silentCallType")
            Log.d(TAG, "   - Room: $silentRoomName")
            Log.d(TAG, "   - Participant: $silentParticipantIdentity")

            // Start the connection process for silent notification
            startSilentCallConnection(silentRoomName!!, silentCallType!!, silentParticipantIdentity!!)
        } else {
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

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "RingingActivity onResume - isSilentNotification: $isSilentNotification")

        // Additional check for silent notifications
        if (isSilentNotification) {
            Log.d(TAG, "🔕 Silent notification active - ensuring UI is visible")

            // Force the activity to stay on top for silent notifications
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)

            // Ensure we're not finishing due to IDLE state
            suppressFinishUntilConnected = true
        }
    }
} 