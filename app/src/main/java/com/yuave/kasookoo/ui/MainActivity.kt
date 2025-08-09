package com.yuave.kasookoo.ui

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.yuave.kasookoo.CallApplication
import com.yuave.kasookoo.R
import com.yuave.kasookoo.core.CallState
import com.yuave.kasookoo.core.CallType
import com.yuave.kasookoo.core.LiveKitManager
import com.yuave.kasookoo.core.RoomConnectionStatus
import com.yuave.kasookoo.data.CallRepository
import com.yuave.kasookoo.data.UserDataManager
import com.yuave.kasookoo.databinding.ActivityMainBinding
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import android.view.View
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

class MainActivity : AppCompatActivity() {
    
    companion object {
        private const val TAG = "MainActivity"
        private const val PERMISSION_REQUEST_CODE = 100
        
        // Static variables for global state management
        private var currentSupportCallParticipantIdentity: String? = null
        private var currentSupportCallRoomName: String? = null
        private var currentCallType: CallType? = null
        private var isEndingCallProgrammatically = false
        
        fun setCurrentSupportCallParticipantIdentity(identity: String?) {
            currentSupportCallParticipantIdentity = identity
            Log.d(TAG, "Support call participant identity set to: $identity")
        }
        
        fun getCurrentSupportCallParticipantIdentity(): String? {
            return currentSupportCallParticipantIdentity
        }
        
        fun setCurrentSupportCallRoomName(roomName: String?) {
            currentSupportCallRoomName = roomName
            Log.d(TAG, "Support call room name set to: $roomName")
        }
        
        fun getCurrentSupportCallRoomName(): String? {
            return currentSupportCallRoomName
        }
        
        fun setCurrentCallType(callType: CallType?) {
            currentCallType = callType
            Log.d(TAG, "Current call type set to: $callType")
        }
        
        fun getCurrentCallType(): CallType? {
            return currentCallType
        }
        
        fun setEndingCallProgrammatically(ending: Boolean) {
            isEndingCallProgrammatically = ending
            Log.d(TAG, "isEndingCallProgrammatically set to: $ending")
        }
        
        fun resetEndingCallProgrammatically() {
            isEndingCallProgrammatically = false
            Log.d(TAG, "isEndingCallProgrammatically reset to false")
        }
        
        // Static values for testing
        private const val ROOM_NAME = "sdk-room"
    }
    
    private lateinit var binding: ActivityMainBinding
    private lateinit var liveKitManager: LiveKitManager
    private lateinit var repository: CallRepository
    private lateinit var userDataManager: UserDataManager
    
    private var isCustomer: Boolean = true
    private val deviceId = "device_${android.os.Build.SERIAL ?: System.currentTimeMillis()}"
    
    // Use static room name for testing
    private fun getRoomName(): String {
        val roomName = ROOM_NAME // Always use "sdk-room"
        Log.d(TAG, "Using room name: $roomName")
        return roomName
    }
    
    // Generate participant identity from participant identity name
    private fun getParticipantIdentity(): String {
        val participantName = getParticipantIdentityName()
        val identity = if (isCustomer) {
            "customer_${participantName.replace(" ", "_").lowercase()}"
        } else {
            "driver_${participantName.replace(" ", "_").lowercase()}"
        }
        Log.d(TAG, "Generated participant identity: $identity (from name: $participantName, isCustomer: $isCustomer)")
        return identity
    }
    
    // Generate room name based on timestamp
    private fun generateRoomName(): String {
        val timestamp = System.currentTimeMillis()
        return "room_${timestamp}"
    }
    
    // Get participant identity name from user data
    private fun getParticipantIdentityName(): String {
        return userDataManager.getFullName() ?: "User"
    }
    
    // Get participant identity type
    private fun getParticipantIdentityType(): String {
        return if (isCustomer) "customer" else "driver"
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Initialize user data manager
        userDataManager = UserDataManager(this)
        
        // Initialize FCM token
        initializeFCMToken()
        
        // Get user type from intent or from stored data
        isCustomer = intent.getBooleanExtra("isCustomer", true)
        
        // Initialize components
        liveKitManager = (application as CallApplication).liveKitManager
        repository = CallRepository()
        
        // Request permissions
        requestPermissions()
        
        // Setup UI based on user type
        setupUI()
        
        // Setup LiveKit state observers
        setupObservers()
        
        // No need for separate driver mode setup since we handle it in setupUI
    }
    
    private fun initializeFCMToken() {
        userDataManager.getFCMToken { token ->
            Log.d(TAG, "📱 FCM token initialized: ${token.take(20)}...")
        }
    }
    
    private fun setupUI() {
        // Show user info
        val userName = userDataManager.getFullName() ?: "User"
        val userType = userDataManager.getUserType() ?: "Unknown"
        
        if (isCustomer) {
            // Customer UI - show both call buttons
            binding.btnCallDriver.setOnClickListener {
                initiateCall(CallType.DRIVER)
            }
            
            binding.btnCallSupport.setOnClickListener {
                initiateCall(CallType.SUPPORT)
            }
            
            binding.btnCallHistory.setOnClickListener {
                openCallHistory()
            }
            
            binding.tvWelcome.text = "Welcome $userName!\nChoose who to call:"
            binding.btnCallDriver.isEnabled = true
            binding.btnCallSupport.isEnabled = true
            
            // Show support card for customers
            binding.supportCardContainer.visibility = View.VISIBLE
            
        } else {
            // Driver UI - show call customer button only
            binding.btnCallDriver.text = "Call Customer"
            binding.btnCallDriver.setOnClickListener {
                initiateCall(CallType.CUSTOMER) // Driver calling customer
            }
            binding.btnCallDriver.isEnabled = true
            
            // Update driver card title
            binding.tvDriverCardTitle.text = "Call Customer"
            
            // Hide support card for drivers
            binding.supportCardContainer.visibility = View.GONE
            
            binding.tvWelcome.text = "Driver Mode\nAvailable - You can call customers"
        }
        
        // Add logout functionality to the header menu button
        binding.btnCallHistory.setOnClickListener {
            showLogoutDialog()
        }
    }
    
    private fun setupObservers() {
        lifecycleScope.launch {
            liveKitManager.callState.collect { state ->
                Log.d(TAG, "🔄 Call state changed: $state")
                Log.d(TAG, "📍 Current activity: ${this.javaClass.simpleName}")
                Log.d(TAG, "👤 Is customer: $isCustomer")
                Log.d(TAG, "🛑 Is ending call programmatically: $isEndingCallProgrammatically")
                
                // Don't trigger ANY navigation if we're ending a call programmatically
                if (isEndingCallProgrammatically) {
                    Log.d(TAG, "🛑 Skipping ALL navigation - call being ended programmatically")
                    // Only reset the flag when we reach IDLE state
                    if (state == CallState.IDLE) {
                        Log.d(TAG, "🔄 Resetting isEndingCallProgrammatically flag")
                        resetEndingCallProgrammatically()
                    }
                    return@collect
                }
                
                when (state) {
                    CallState.CONNECTING -> {
                        Log.d(TAG, "🚀 Navigating to ringing activity (CONNECTING)")
                        // Navigate directly to ringing screen instead of modal
                        navigateToRingingActivity()
                    }
                    CallState.CONNECTED -> {
                        if (!isCustomer) {
                            Log.d(TAG, "🚗 Driver connected to room, now listen for incoming calls")
                            // Driver connected to room, now listen for incoming calls
                            // No longer needed with new calling system
                        } else {
                            Log.d(TAG, "👤 Customer connected to room")
                        }
                    }
                    CallState.INCOMING_CALL -> {
                        if (!isCustomer) {
                            Log.d(TAG, "📞 Showing incoming call dialog for driver")
                            // No longer needed with new calling system
                        } else {
                            Log.d(TAG, "👤 Customer received incoming call")
                        }
                    }
                    CallState.WAITING_FOR_DRIVER_ACCEPTANCE -> {
                        if (!isCustomer) {
                            Log.d(TAG, "🚗 Driver waiting for customer to join - stay on current screen")
                            // Driver should stay on current screen, no navigation needed
                        } else {
                            Log.d(TAG, "👤 Customer waiting for driver to accept - stay on ringing screen")
                            // Customer should stay on ringing screen, no navigation needed
                        }
                    }
                    CallState.IN_CALL -> {
                        // For support calls, let RingingActivity handle navigation
                        // For driver calls, MainActivity can handle navigation
                        val currentCallType = getCurrentCallType()
                        if (currentCallType == CallType.SUPPORT) {
                            Log.d(TAG, "🆘 Support call IN_CALL - letting RingingActivity handle navigation")
                        } else if (currentCallType == CallType.DRIVER && isCustomer) {
                            Log.d(TAG, "👤 Customer IN_CALL - letting RingingActivity handle navigation")
                            // Let RingingActivity handle navigation for customer
                        } else {
                            Log.d(TAG, "📱 Navigating to call activity (IN_CALL)")
                            navigateToCallActivity()
                        }
                    }
                    CallState.ERROR -> {
                        Log.d(TAG, "❌ Connection failed, showing error")
                        showError("Connection failed. Please try again.")
                    }
                    CallState.IDLE -> {
                        Log.d(TAG, "🔄 Call state is IDLE - call ended")
                        // Don't navigate when call ends, let the current activity handle it
                    }
                    else -> {
                        Log.d(TAG, "ℹ️ Call state: $state - no action taken")
                        // Do nothing
                    }
                }
            }
        }
    }
    
    private fun observeRoomStatus() {
        lifecycleScope.launch {
            liveKitManager.roomConnectionStatus.collect { status ->
                Log.d(TAG, "Driver room status: $status")
                updateDriverStatus(status)
            }
        }
        
        lifecycleScope.launch {
            liveKitManager.participants.collect { participants ->
                Log.d(TAG, "Driver participants: ${liveKitManager.getParticipantDetails()}")
                updateDriverStatusText()
            }
        }
    }
    
    private fun updateDriverStatus(status: RoomConnectionStatus) {
        val statusText = when (status) {
            RoomConnectionStatus.CONNECTING -> "Connecting to room..."
            RoomConnectionStatus.CONNECTED -> "Connected to room - Waiting for customers"
            RoomConnectionStatus.MULTIPLE_PARTICIPANTS -> "Customer joined - Incoming call!"
            RoomConnectionStatus.CALL_ACTIVE -> "Call in progress"
            RoomConnectionStatus.DISCONNECTED -> "Disconnected from room"
            RoomConnectionStatus.ERROR -> "Connection error"
            else -> "Unknown status"
        }
        
        binding.tvWelcome.text = "Driver Mode\n$statusText"
        Log.d(TAG, "Driver status updated: $statusText")
    }
    
    private fun updateDriverStatusText() {
        val participantDetails = liveKitManager.getParticipantDetails()
        Log.d(TAG, "Current room participants: $participantDetails")
        
        if (liveKitManager.hasCustomerAndDriver()) {
            Log.d(TAG, "🎉 SUCCESS: Customer and Driver are in the same room!")
            Log.d(TAG, "Room: ${getRoomName()}")
            Log.d(TAG, "Participants: $participantDetails")
        }
    }
    
    private fun initiateCall(callType: CallType) {
        lifecycleScope.launch {
            try {
                when (callType) {
                    CallType.SUPPORT -> {
                        // Use new SIP-based support call API
                        Log.d(TAG, "🚀 Initiating support call using SIP API...")
                        
                        // Generate dynamic room name and get participant name
                        val roomName = generateRoomName()
                        val participantName = getParticipantIdentityName()
                        
                        Log.d(TAG, "📞 Support call details:")
                        Log.d(TAG, "   - Phone number: +443333054030")
                        Log.d(TAG, "   - Room name: $roomName")
                        Log.d(TAG, "   - Participant name: $participantName")
                        
                        // Set current call type for UI updates
                        setCurrentCallType(CallType.SUPPORT)
                        
                        val supportCallResult = repository.makeSupportCall(roomName, participantName)
                        
                        supportCallResult.onSuccess { supportResponse ->
                            Log.d(TAG, "✅ Support call initiated successfully!")
                            Log.d(TAG, "📋 Response: ${supportResponse.message}")
                            
                            if (supportResponse.success && supportResponse.data != null) {
                                val callData = supportResponse.data!!
                                
                                // Store participant identity and room name for ending call later
                                setCurrentSupportCallParticipantIdentity(callData.call_details.participant_identity)
                                setCurrentSupportCallRoomName(callData.room_name)
                              
                                
                                // Set LiveKitManager call type for proper disconnect handling
                                liveKitManager.setCallType(CallType.SUPPORT)
                                
                                // ✅ Connect to LiveKit room using the token from response
                                Log.d(TAG, "🔗 Connecting to LiveKit room for support call...")
                                
                                // Use the same WebSocket URL as driver calls since support call API doesn't provide it
                                val wsUrl = "wss://kasookoosdk-3af68qx7.livekit.cloud"
                                
                                Log.d(TAG, "🔗 Using WebSocket URL: $wsUrl")
                                Log.d(TAG, "🔗 Room Session ID: ${callData.room_session_id}")
                                Log.d(TAG, "🔗 Room Name: ${callData.room_name}")
                                
                                liveKitManager.connectToRoom(
                                    token = callData.room_token,
                                    wsUrl = wsUrl,
                                    roomName = callData.room_name,
                                    callType = CallType.SUPPORT
                                )
                                
                                // Navigate to ringing screen for support call
                                navigateToRingingActivity()
                                
                                
                            } else {
                                Log.e(TAG, "❌ Support call failed: ${supportResponse.message}")
                                showError("Failed to initiate support call: ${supportResponse.message}")
                                // Clear call type on failure
                                setCurrentCallType(null)
                            }
                        }.onFailure { error ->
                            Log.e(TAG, "❌ Failed to make support call", error)
                            showError("Failed to initiate support call: ${error.message}")
                            // Clear call type on failure
                            setCurrentCallType(null)
                        }
                    }
                    
                    CallType.DRIVER -> {
                        // Use new WebRTC calling API for driver calls
                        Log.d(TAG, "🚗 Initiating driver call using new WebRTC API...")
                        
                        // Set current call type for UI updates
                        setCurrentCallType(CallType.DRIVER)
                        
                        val roomName = generateRoomName()
                        val participantIdentity = getParticipantIdentity()
                        val participantIdentityName = getParticipantIdentityName()
                        val participantIdentityType = getParticipantIdentityType()
                        val userId = userDataManager.getUserId() ?: throw Exception("User ID not found")
                        
                        Log.d(TAG, "📞 Call details:")
                        Log.d(TAG, "   - Room: $roomName")
                        Log.d(TAG, "   - Participant: $participantIdentity")
                        Log.d(TAG, "   - Name: $participantIdentityName")
                        Log.d(TAG, "   - Type: $participantIdentityType")
                        Log.d(TAG, "   - User ID: $userId")
                        
                        val tokenResult = repository.getCallerLiveKitToken(
                            roomName, participantIdentity, participantIdentityName, participantIdentityType, userId
                        )
                        
                        tokenResult.onSuccess { tokenResponse ->
                            Log.d(TAG, "✅ Got caller token, connecting to room...")
                            
                            // Connect to LiveKit room
                            val participantCallType = if (isCustomer) CallType.CUSTOMER else CallType.DRIVER
                            Log.d(TAG, "Customer initiating call to $callType, but connecting as $participantCallType")
                            
                            liveKitManager.connectToRoom(
                                token = tokenResponse.accessToken,
                                wsUrl = tokenResponse.wsUrl,
                                roomName = roomName,
                                callType = participantCallType
                            )
                        }.onFailure { error ->
                            Log.d(TAG, "Failed to get caller token", error)
                            showError("Failed to initiate call: ${error.message}")
                            setCurrentCallType(null)
                        }
                    }
                    
                    CallType.CUSTOMER -> {
                        // Driver calling customer using WebRTC API
                        Log.d(TAG, "👤 Driver initiating call to customer using WebRTC API...")
                        
                        // Set current call type for UI updates
                        setCurrentCallType(CallType.CUSTOMER)
                        
                        val roomName = generateRoomName()
                        val participantIdentity = getParticipantIdentity()
                        val participantIdentityName = getParticipantIdentityName()
                        val participantIdentityType = getParticipantIdentityType()
                        val userId = userDataManager.getUserId() ?: throw Exception("User ID not found")
                        
                        Log.d(TAG, "📞 Call details:")
                        Log.d(TAG, "   - Room: $roomName")
                        Log.d(TAG, "   - Participant: $participantIdentity")
                        Log.d(TAG, "   - Name: $participantIdentityName")
                        Log.d(TAG, "   - Type: $participantIdentityType")
                        Log.d(TAG, "   - User ID: $userId")
                        
                        val tokenResult = repository.getCallerLiveKitToken(
                            roomName, participantIdentity, participantIdentityName, participantIdentityType, userId
                        )
                        
                        tokenResult.onSuccess { tokenResponse ->
                            Log.d(TAG, "✅ Got caller token, connecting to room...")
                            
                            // Connect to LiveKit room
                            val participantCallType = if (isCustomer) CallType.CUSTOMER else CallType.DRIVER
                            Log.d(TAG, "Driver initiating call to $callType, but connecting as $participantCallType")
                            
                            liveKitManager.connectToRoom(
                                token = tokenResponse.accessToken,
                                wsUrl = tokenResponse.wsUrl,
                                roomName = roomName,
                                callType = participantCallType
                            )
                        }.onFailure { error ->
                            Log.d(TAG, "Failed to get caller token", error)
                            showError("Failed to initiate call: ${error.message}")
                            setCurrentCallType(null)
                        }
                    }
                    
                    else -> {
                        Log.w(TAG, "Unsupported call type: $callType")
                        showError("Unsupported call type")
                    }
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error initiating call", e)
                showError("Error initiating call: ${e.message}")
            }
        }
    }
    
    private fun navigateToRingingActivity() {
        val intent = Intent(this, RingingActivity::class.java).apply {
            putExtra("isCustomer", isCustomer)
        }
        startActivity(intent)
    }
    
    private fun navigateToCallActivity() {
        // Prevent multiple CallActivity instances
        val currentCallType = getCurrentCallType()
        if (currentCallType == CallType.SUPPORT) {
            // For support calls, check if we're already in a call activity
            val isInCallActivity = isFinishing || isDestroyed
            if (isInCallActivity) {
                Log.d(TAG, "🛑 Already in call activity or activity finishing, skipping navigation")
                return
            }
        }
        
        Log.d(TAG, "📱 Navigating to CallActivity...")
        val intent = Intent(this, CallActivity::class.java).apply {
            putExtra("isCustomer", isCustomer)
        }
        startActivity(intent)
    }
    
    private fun requestPermissions() {
        val permissions = arrayOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CAMERA,
            Manifest.permission.MODIFY_AUDIO_SETTINGS,
            Manifest.permission.ACCESS_NETWORK_STATE,
            Manifest.permission.POST_NOTIFICATIONS
        )
        
        val missingPermissions = permissions.filter { permission ->
            ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED
        }
        
        if (missingPermissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missingPermissions.toTypedArray(), PERMISSION_REQUEST_CODE)
        }
    }
    
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            val allGranted = grantResults.all { it == PackageManager.PERMISSION_GRANTED }
            if (!allGranted) {
                showError("Permissions are required for voice calling")
            }
        }
    }
    
    // Removed modal dialog - using direct navigation instead
    
    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
    
    private fun openCallHistory() {
        try {
            Log.d(TAG, "Opening call history...")
            val intent = Intent(this, CallHistoryActivity::class.java)
            startActivity(intent)
            Log.d(TAG, "Call history activity started successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error opening call history", e)
            showError("Failed to open call history: ${e.message}")
        }
    }
    
    private fun showLogoutDialog() {
        AlertDialog.Builder(this)
            .setTitle("Logout")
            .setMessage("Are you sure you want to logout?")
            .setPositiveButton("Logout") { _, _ ->
                logout()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun logout() {
        // Clear only login status, keep user data
        userDataManager.clearLoginStatus()
        
        // Navigate back to user selection (which will redirect to login/registration)
        val intent = Intent(this, UserSelectionActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // No modal to clean up
    }
} 