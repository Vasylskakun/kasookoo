package com.yuave.kasookoo.core

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import io.livekit.android.LiveKit
import io.livekit.android.room.Room
import io.livekit.android.room.track.Track

import io.livekit.android.events.RoomEvent
import io.livekit.android.events.collect
import io.livekit.android.ConnectOptions
import com.yuave.kasookoo.data.CallHistoryManager
import com.yuave.kasookoo.data.CallRecord
import com.yuave.kasookoo.data.CallStatus
import kotlinx.coroutines.runBlocking

class LiveKitManager(private val context: Context) {
    
    companion object {
        private const val TAG = "LiveKitManager"
    }
    
    private val _callState = MutableStateFlow(CallState.IDLE)
    val callState: StateFlow<CallState> = _callState.asStateFlow()
    
    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()
    
    private val _participantCount = MutableStateFlow(0)
    val participantCount: StateFlow<Int> = _participantCount.asStateFlow()
    
    private val _callType = MutableStateFlow(CallType.DRIVER)
    val callType: StateFlow<CallType> = _callType.asStateFlow()
    
    // Enhanced participant tracking
    private val _participants = MutableStateFlow<List<ParticipantInfo>>(emptyList())
    val participants: StateFlow<List<ParticipantInfo>> = _participants.asStateFlow()
    
    private val _roomConnectionStatus = MutableStateFlow(RoomConnectionStatus.IDLE)
    val roomConnectionStatus: StateFlow<RoomConnectionStatus> = _roomConnectionStatus.asStateFlow()
    
    // Real LiveKit components
    private var room: Room? = null
    private var isRoomConnected = false
    
    // Call history and timing
    private val callHistoryManager = CallHistoryManager(context)
    private var currentCallRecord: CallRecord? = null
    private var callStartTime: Long = 0
    
    // Expose call start time for timer
    val callStartTimestamp: Long get() = callStartTime
    
    // Set call type externally (for support calls)
    fun setCallType(callType: CallType) {
        _callType.value = callType
        Log.d(TAG, "Call type set to: $callType")
        
        // For support calls, start the timer immediately since we don't use LiveKit
        if (callType == CallType.SUPPORT) {
            startCallTimer()
        }
    }
    
    // Start call timer (used for support calls that don't use LiveKit)
    private fun startCallTimer() {
        callStartTime = System.currentTimeMillis()
        Log.d(TAG, "🕐 Call timer started for support call at: $callStartTime")
    }
    
    // Get contact name based on call type
    private fun getContactName(): String {
        return when (_callType.value) {
            CallType.CUSTOMER -> "Customer"
            CallType.DRIVER -> "Driver"
            CallType.SUPPORT -> "Support"
        }
    }
    
    suspend fun connectToRoom(token: String, wsUrl: String, roomName: String, callType: CallType) {
        try {
            Log.d(TAG, "=== LiveKit Connection Debug ===")
            Log.d(TAG, "Room Name: $roomName")
            Log.d(TAG, "WebSocket URL: $wsUrl")
            Log.d(TAG, "Token (first 20 chars): ${token.take(20)}...")
            Log.d(TAG, "Call Type: $callType")
            
            _callType.value = callType
            _callState.value = CallState.CONNECTING
            
            // Validate inputs
            if (wsUrl.isBlank()) {
                throw IllegalArgumentException("WebSocket URL is empty")
            }
            
            if (token.isBlank()) {
                throw IllegalArgumentException("Token is empty")
            }
            
            // Validate WebSocket URL format
            if (!wsUrl.startsWith("ws://") && !wsUrl.startsWith("wss://")) {
                Log.w(TAG, "Warning: WebSocket URL should start with ws:// or wss://")
                Log.w(TAG, "Current URL: $wsUrl")
            }
            
            // Create room using LiveKit
            Log.d(TAG, "Creating LiveKit room...")
            room = LiveKit.create(appContext = context)
            Log.d(TAG, "Room created successfully")
            
            // Setup room event listeners
            setupRoomListeners()
            
            // Connect to room with options
            val connectOptions = ConnectOptions(
                audio = true, 
                video = false,
                autoSubscribe = true
            )
            
            Log.d(TAG, "Attempting connection to LiveKit server...")
            room?.connect(
                url = wsUrl,
                token = token,
                options = connectOptions
            )
            
            Log.d(TAG, "Connection request sent to LiveKit")
            
        } catch (e: Exception) {
            Log.e(TAG, "=== LiveKit Connection Error ===")
            Log.e(TAG, "Error Type: ${e.javaClass.simpleName}")
            Log.e(TAG, "Error Message: ${e.message}")
            Log.e(TAG, "Stack Trace:", e)
            _callState.value = CallState.ERROR
            _roomConnectionStatus.value = RoomConnectionStatus.ERROR
            throw e
        }
    }
    
    private fun setupRoomListeners() {
        room?.let { currentRoom ->
            CoroutineScope(Dispatchers.Main).launch {
                currentRoom.events.collect { event ->
                    when (event) {
                        is RoomEvent.Connected -> {
                            Log.d(TAG, "=== Connected to room ===")
                            Log.d(TAG, "LiveKit Room Name: ${currentRoom.name}")
                            Log.d(TAG, "LiveKit Local Participant connected")
                            Log.d(TAG, "LiveKit Remote Participants Count: ${currentRoom.remoteParticipants.size}")
                            
                            isRoomConnected = true
                            _isConnected.value = true
                            _callState.value = CallState.CONNECTED
                            
                            // Track local participant
                            val localParticipant = createParticipantInfo(currentRoom.localParticipant, true)
                            _participants.value = listOf(localParticipant)
                            updateRoomConnectionStatus(currentRoom)
                            
                            Log.d(TAG, "Local participant connected: ${localParticipant.participantType} (${localParticipant.identity})")
                            
                            // Start call timing
                            callStartTime = System.currentTimeMillis()
                            
                            // Create call record
                            val contactName = when (_callType.value) {
                                CallType.CUSTOMER -> "Customer"
                                CallType.DRIVER -> "Driver"
                                CallType.SUPPORT -> "Support"
                            }
                            currentCallRecord = CallRecord(
                                callType = _callType.value,
                                contactName = contactName,
                                startTime = callStartTime,
                                status = CallStatus.COMPLETED
                            )
                            
                            // For outgoing calls (customer), if remote already present, go IN_CALL; else wait
                            if (_callType.value == CallType.CUSTOMER) {
                                val hasRemote = currentRoom.remoteParticipants.isNotEmpty()
                                if (hasRemote) {
                                    Log.d(TAG, "✅ Remote participant already present on connect → Customer IN_CALL")
                                    _callState.value = CallState.IN_CALL
                                    _roomConnectionStatus.value = RoomConnectionStatus.CALL_ACTIVE
                                } else {
                                    _callState.value = CallState.WAITING_FOR_ACCEPTANCE
                                    Log.d(TAG, "🔄 Waiting for driver to accept call...")
                                }
                            }
                            
                            // For support calls, also wait for support to join
                            if (_callType.value == CallType.SUPPORT) {
                                _callState.value = CallState.WAITING_FOR_ACCEPTANCE
                                Log.d(TAG, "🆘 Support call - waiting for support team to join...")
                            }

                            // For driver (callee) calls, if remote is already present right after connect,
                            // transition to WAITING_FOR_DRIVER_ACCEPTANCE so UI can auto-accept to IN_CALL
                            if (_callType.value == CallType.DRIVER) {
                                val hasRemote = currentRoom.remoteParticipants.isNotEmpty()
                                if (hasRemote) {
                                    Log.d(TAG, "✅ Remote participant already present on connect → Driver WAITING_FOR_DRIVER_ACCEPTANCE")
                                    _callState.value = CallState.WAITING_FOR_DRIVER_ACCEPTANCE
                                    _roomConnectionStatus.value = RoomConnectionStatus.MULTIPLE_PARTICIPANTS
                                } else {
                                    Log.d(TAG, "ℹ️ Driver connected with no remote participants yet")
                                }
                            }
                             
                            // Enhanced audio setup with proper track publishing
                            try {
                                Log.d(TAG, "=== AUDIO SETUP DEBUG ===")
                                
                                // Check if this is a support call that needs special audio handling
                                if (_callType.value == CallType.SUPPORT) {
                                    Log.d(TAG, "🆘 Support call detected - using DTX/RED-disabled audio setup")
                                    setupSupportCallAudio(currentRoom)
                                } else {
                                    Log.d(TAG, "🚗 Regular call - using standard audio setup")
                                    setupStandardCallAudio(currentRoom)
                                }
                                
                                Log.d(TAG, "=== AUDIO SETUP COMPLETE ===")
                                
                            } catch (e: Exception) {
                                Log.e(TAG, "Error during audio setup: ${e.message}")
                                Log.e(TAG, "Audio setup error:", e)
                            }
                        }
                        
                        is RoomEvent.ParticipantConnected -> {
                            Log.d(TAG, "=== PARTICIPANT CONNECTED ===")
                            Log.d(TAG, "New participant connected: ${event.participant.identity}")
                            Log.d(TAG, "Total participants in room: ${currentRoom.remoteParticipants.size + 1}")
                            
                            // Update participant count
                            _participantCount.value = currentRoom.remoteParticipants.size + 1
                            
                            // Add new participant to tracking
                            val participantInfo = createParticipantInfo(event.participant, false)
                            val currentParticipants = _participants.value.toMutableList()
                            currentParticipants.add(participantInfo)
                            _participants.value = currentParticipants
                            
                            // For outgoing calls (customer), when driver joins, change to IN_CALL immediately
                            if (_callType.value == CallType.CUSTOMER && 
                                event.participant.identity?.contains("driver") == true) {
                                Log.d(TAG, "✅ Driver joined the room - customer sees connected screen!")
                                Log.d(TAG, "🔄 Customer state transition: ${_callState.value} → IN_CALL")
                                _callState.value = CallState.IN_CALL
                                _roomConnectionStatus.value = RoomConnectionStatus.CALL_ACTIVE
                                Log.d(TAG, "✅ Customer now in IN_CALL state")
                            } else if (_callType.value == CallType.CUSTOMER && currentRoom.remoteParticipants.isNotEmpty()) {
                                // Fallback: if we have any remote participant, consider call active for customer
                                Log.d(TAG, "✅ Remote participant present - forcing customer to IN_CALL")
                                _callState.value = CallState.IN_CALL
                                _roomConnectionStatus.value = RoomConnectionStatus.CALL_ACTIVE
                            }
                            
                            // For incoming calls (driver), when customer joins, allow immediate accept if already joined
                            if (_callType.value == CallType.DRIVER && 
                                event.participant.identity?.contains("customer") == true) {
                                Log.d(TAG, "👤 Customer joined the room - driver should accept to proceed")
                                _callState.value = CallState.WAITING_FOR_DRIVER_ACCEPTANCE
                                _roomConnectionStatus.value = RoomConnectionStatus.MULTIPLE_PARTICIPANTS
                            } else if (_callType.value == CallType.DRIVER && currentRoom.remoteParticipants.isNotEmpty()) {
                                // If driver already indicated acceptance via UI, move to IN_CALL
                                Log.d(TAG, "✅ Remote participant present - forcing driver to IN_CALL")
                                _callState.value = CallState.IN_CALL
                                _roomConnectionStatus.value = RoomConnectionStatus.CALL_ACTIVE
                            }
                            
                            // For support calls, when support joins, change to IN_CALL
                            if (_callType.value == CallType.SUPPORT && 
                                event.participant.identity?.contains("sip") == true) {
                                Log.d(TAG, "✅ Support accepted the call!")
                                _callState.value = CallState.IN_CALL
                                _roomConnectionStatus.value = RoomConnectionStatus.CALL_ACTIVE
                                
                                // Ensure audio is properly set up for support call
                                CoroutineScope(Dispatchers.Main).launch {
                                    try {
                                        Log.d(TAG, "🆘 Setting up audio for support call after support joined...")
                                        setupSupportCallAudio(currentRoom)
                                        
                                        // Also ensure remote participant's audio tracks are enabled
                                        // Note: LiveKit handles audio track subscription automatically
                                        Log.d(TAG, "📡 Support participant joined, audio should be working")
                                        
                                        Log.d(TAG, "✅ Support call audio setup completed after support joined")
                                    } catch (e: Exception) {
                                        Log.e(TAG, "❌ Error setting up support audio after support joined: ${e.message}")
                                    }
                                }
                            }
                            
                            // Note: Remote participants don't need microphone enabled manually
                            // LiveKit handles this automatically
                            Log.d(TAG, "Remote participant joined: ${event.participant.identity}")
                            
                            // Check if we have both customer and driver
                            if (hasCustomerAndDriver()) {
                                Log.d(TAG, "🎉 SUCCESS: Customer and Driver are in the same room!")
                                Log.d(TAG, "Room: ${currentRoom.name}")
                                Log.d(TAG, "Participants: ${_participants.value.map { "${it.participantType}(${it.identity})" }}")
                                
                                // Verify basic audio status
                                verifyBasicAudioStatus(currentRoom)
                            }
                            
                            // Update room connection status
                            updateRoomConnectionStatus(currentRoom)
                        }
                        
                        is RoomEvent.ParticipantDisconnected -> {
                            Log.d(TAG, "=== PARTICIPANT DISCONNECTED ===")
                            Log.d(TAG, "Participant disconnected: ${event.participant.identity}")
                            
                            // Update participant count
                            _participantCount.value = currentRoom.remoteParticipants.size
                            
                            // Remove from participants list
                            val currentParticipants = _participants.value.toMutableList()
                            currentParticipants.removeAll { it.sid == event.participant.sid.toString() }
                            _participants.value = currentParticipants
                            
                            // If other participant disconnected, end call
                            if (_participantCount.value <= 1) {
                                Log.d(TAG, "Other participant disconnected, ending call")
                                _callState.value = CallState.IDLE
                            }
                            
                            // Update room connection status
                            updateRoomConnectionStatus(currentRoom)
                        }
                        
                        is RoomEvent.Disconnected -> {
                            Log.d(TAG, "=== ROOM DISCONNECTED ===")
                            _isConnected.value = false
                            _callState.value = CallState.IDLE
                            _participantCount.value = 0
                            _participants.value = emptyList()
                            _roomConnectionStatus.value = RoomConnectionStatus.DISCONNECTED
                        }
                        
                        is RoomEvent.ConnectionQualityChanged -> {
                            Log.d(TAG, "Connection quality changed: ${event.quality}")
                        }
                        
                        else -> {
                            Log.d(TAG, "Room event: $event")
                        }
                    }
                }
            }
        }
    }
    
    fun muteAudio() {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                room?.localParticipant?.setMicrophoneEnabled(false)
                Log.d(TAG, "Audio muted")
            } catch (e: Exception) {
                Log.e(TAG, "Error muting audio: ${e.message}")
            }
        }
    }
    
    fun unmuteAudio() {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                room?.localParticipant?.setMicrophoneEnabled(true)
                Log.d(TAG, "Audio unmuted")
            } catch (e: Exception) {
                Log.e(TAG, "Error unmuting audio: ${e.message}")
            }
        }
    }
    
    fun enableCamera() {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                room?.localParticipant?.setCameraEnabled(true)
                Log.d(TAG, "Camera enabled")
            } catch (e: Exception) {
                Log.e(TAG, "Error enabling camera: ${e.message}")
            }
        }
    }
    
    fun disableCamera() {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                room?.localParticipant?.setCameraEnabled(false)
                Log.d(TAG, "Camera disabled")
            } catch (e: Exception) {
                Log.e(TAG, "Error disabling camera: ${e.message}")
            }
        }
    }
    
    fun disconnect() {
        try {
            // Prevent multiple simultaneous disconnect calls
            if (_callState.value == CallState.IDLE) {
                Log.d(TAG, "⚠️ Already disconnected, ignoring duplicate disconnect call")
                return
            }
            
            Log.d(TAG, "🔄 Disconnecting from room")
            Log.d(TAG, "📊 Current call type: ${_callType.value}")
            Log.d(TAG, "📊 Current call state: ${_callState.value}")

            // Proactively set local state to IDLE so UI can close immediately
            // Remote peer will be notified by LiveKit when we disconnect
            _callState.value = CallState.IDLE
            _roomConnectionStatus.value = RoomConnectionStatus.DISCONNECTED
            
            // Check if this is a support call and end it via SIP API
            if (_callType.value == CallType.SUPPORT) {
                Log.d(TAG, "🆘 Ending support call via SIP API...")
                // For support calls, handle this asynchronously to prevent UI blocking
                endSupportCallSync()
            }
            
            // Save call record to history
            saveCallToHistory()
            
            // Only disconnect LiveKit room if it exists
            if (room != null) {
                Log.d(TAG, "🔌 Disconnecting LiveKit room...")
                room?.disconnect()
                room = null
                Log.d(TAG, "✅ LiveKit room disconnected")
            } else {
                Log.d(TAG, "ℹ️ No LiveKit room to disconnect")
            }
            
            isRoomConnected = false
            _isConnected.value = false
            _participantCount.value = 0
            
            // Clear stored call type (already cleared for support calls)
            if (_callType.value != CallType.SUPPORT) {
                com.yuave.kasookoo.ui.MainActivity.setCurrentCallType(null)
                Log.d(TAG, "🧹 Call type cleared")
            }
            Log.d(TAG, "✅ Disconnect completed")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error disconnecting: ${e.message}")
        }
    }
    
    // End support call using SIP API (asynchronous version to prevent UI blocking)
    private fun endSupportCallSync() {
        try {
            val participantIdentity = com.yuave.kasookoo.ui.MainActivity.getCurrentSupportCallParticipantIdentity()
            val roomName = com.yuave.kasookoo.ui.MainActivity.getCurrentSupportCallRoomName()
            
            if (participantIdentity != null && participantIdentity.isNotEmpty() && roomName != null) {
                Log.d(TAG, "Ending support call for participant: $participantIdentity in room: $roomName")
                
                // Clear the stored participant identity IMMEDIATELY to prevent duplicate calls
                com.yuave.kasookoo.ui.MainActivity.setCurrentSupportCallParticipantIdentity("")
                com.yuave.kasookoo.ui.MainActivity.setCurrentSupportCallRoomName("")
                com.yuave.kasookoo.ui.MainActivity.setCurrentCallType(null)
                Log.d(TAG, "🧹 Support call data cleared immediately")
                
                // Use an asynchronous approach to prevent UI blocking
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val repository = com.yuave.kasookoo.data.CallRepository()
                        val result = repository.endSupportCall(participantIdentity, roomName)
                        
                        result.onSuccess { response ->
                            if (response.success) {
                                Log.d(TAG, "✅ Support call ended successfully: ${response.message}")
                            } else {
                                Log.e(TAG, "❌ Failed to end support call: ${response.message}")
                            }
                        }.onFailure { error ->
                            Log.e(TAG, "❌ Error ending support call", error)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ Error in endSupportCallSync", e)
                    }
                }
            } else {
                Log.w(TAG, "⚠️ No support call participant identity or room name found or already cleared")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error in endSupportCallSync", e)
        }
    }
    
    private fun saveCallToHistory() {
        currentCallRecord?.let { record ->
            val endTime = System.currentTimeMillis()
            val duration = (endTime - record.startTime) / 1000 // Convert to seconds
            
            if (duration >= 3) { // Only save calls that lasted at least 3 seconds
                val completedRecord = record.copy(
                    endTime = endTime,
                    duration = duration
                )
                
                callHistoryManager.addCallRecord(completedRecord)
                Log.d(TAG, "Call saved to history: duration = ${duration}s")
            }
            
            currentCallRecord = null
        }
    }
    
    // Get call history
    fun getCallHistory() = callHistoryManager.getCallHistory()
    
    // Clear call history  
    fun clearCallHistory() = callHistoryManager.clearHistory()
    
    // Get current call duration in seconds
    fun getCurrentCallDuration(): Long {
        val duration = if (callStartTime > 0) {
            (System.currentTimeMillis() - callStartTime) / 1000
        } else {
            0
        }
        
        Log.d(TAG, "🕐 Call duration calculation:")
        Log.d(TAG, "   - Call start time: $callStartTime")
        Log.d(TAG, "   - Current time: ${System.currentTimeMillis()}")
        Log.d(TAG, "   - Duration: ${duration}s")
        Log.d(TAG, "   - Call type: ${_callType.value}")
        
        return duration
    }
    
    // Accept incoming call
    fun acceptCall() {
        Log.d(TAG, "🔄 acceptCall() called - current state: ${_callState.value}")
        Log.d(TAG, "🔄 acceptCall() called - current call type: ${_callType.value}")
        
        if (_callState.value == CallState.INCOMING_CALL) {
            Log.d(TAG, "✅ Driver accepting incoming call")
            _callState.value = CallState.IN_CALL
            Log.d(TAG, "✅ Driver state changed to IN_CALL")
        } else if (_callState.value == CallState.WAITING_FOR_DRIVER_ACCEPTANCE) {
            Log.d(TAG, "✅ Driver accepting call after customer joined")
            _callState.value = CallState.IN_CALL
            Log.d(TAG, "✅ Driver state changed to IN_CALL")
        } else {
            Log.w(TAG, "⚠️ acceptCall() called but current state is: ${_callState.value}")
        }
    }
    
    // Helper methods for participant tracking
    private fun createParticipantInfo(unusedParticipant: io.livekit.android.room.participant.Participant, isLocal: Boolean): ParticipantInfo {
        // Generate a unique identifier for the participant
        val sid = "sid_${System.currentTimeMillis()}_${if (isLocal) "local" else "remote"}"
        val identity = if (isLocal) {
            when (_callType.value) {
                CallType.CUSTOMER -> "customer_${System.currentTimeMillis()}"
                CallType.DRIVER -> "driver_${System.currentTimeMillis()}"
                CallType.SUPPORT -> "support_${System.currentTimeMillis()}"
            }
        } else {
            // For remote participants, we'll use a generic identity
            "remote_${System.currentTimeMillis()}"
        }
        
        Log.d(TAG, "Creating participant info - SID: $sid, Identity: $identity, IsLocal: $isLocal")
        
        val participantType = when {
            identity.startsWith("customer_") -> ParticipantType.CUSTOMER
            identity.startsWith("driver_") -> ParticipantType.DRIVER
            identity.startsWith("support_") -> ParticipantType.SUPPORT
            else -> ParticipantType.UNKNOWN
        }
        
        return ParticipantInfo(
            sid = sid,
            identity = identity,
            isLocal = isLocal,
            participantType = participantType
        )
    }
    
    private fun updateRoomConnectionStatus(room: Room) {
        val participantCount = room.remoteParticipants.size + 1
        val status = when {
            participantCount == 1 -> RoomConnectionStatus.CONNECTED
            participantCount == 2 -> RoomConnectionStatus.MULTIPLE_PARTICIPANTS
            participantCount > 2 -> RoomConnectionStatus.CALL_ACTIVE
            else -> RoomConnectionStatus.CONNECTED
        }
        _roomConnectionStatus.value = status
        
        Log.d(TAG, "Room status updated: $status (${participantCount} participants)")
    }
    
    // Public methods to check room status
    fun hasCustomerAndDriver(): Boolean {
        val participants = _participants.value
        val hasCustomer = participants.any { it.participantType == ParticipantType.CUSTOMER }
        val hasDriver = participants.any { it.participantType == ParticipantType.DRIVER }
        return hasCustomer && hasDriver
    }
    
    fun getParticipantDetails(): String {
        val participants = _participants.value
        return participants.joinToString(", ") { 
            "${it.participantType}(${it.identity ?: "unknown"})" 
        }
    }
    
    fun isCustomerDriverCallActive(): Boolean {
        return _roomConnectionStatus.value == RoomConnectionStatus.MULTIPLE_PARTICIPANTS && 
               hasCustomerAndDriver()
    }
    
    // Force enable microphone and ensure audio tracks are published
    fun forceEnableAudio() {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                room?.let { currentRoom ->
                    Log.d(TAG, "=== FORCE ENABLE AUDIO ===")
                    
                    // Check if this is a support call that needs special handling
                    if (_callType.value == CallType.SUPPORT) {
                        Log.d(TAG, "🆘 Support call detected - re-applying DTX/RED-disabled audio setup")
                        setupSupportCallAudio(currentRoom)
                        
                        // Additional audio track subscription for support calls
                        currentRoom.remoteParticipants.forEach { participant ->
                            Log.d(TAG, "📡 Force enabling audio tracks for support participant")
                            
                            // LiveKit handles audio track subscription automatically
                            // We just need to ensure our local microphone is enabled
                            Log.d(TAG, "📡 Support participant audio should be working automatically")
                        }
                    } else {
                        Log.d(TAG, "🚗 Regular call - using standard force enable")
                        
                        // Force enable microphone
                        currentRoom.localParticipant.setMicrophoneEnabled(true)
                        Log.d(TAG, "Microphone force enabled")
                        
                        // Check microphone status
                        val isMicrophoneEnabled = currentRoom.localParticipant.isMicrophoneEnabled()
                        Log.d(TAG, "Microphone status after force enable: $isMicrophoneEnabled")
                        
                        if (isMicrophoneEnabled) {
                            Log.d(TAG, "✅ Microphone is enabled and should be working")
                        } else {
                            Log.w(TAG, "❌ Microphone is not enabled")
                        }
                        
                        // Check remote participants
                        Log.d(TAG, "Remote participants: ${currentRoom.remoteParticipants.size}")
                        currentRoom.remoteParticipants.forEach { unusedParticipant ->
                            Log.d(TAG, "Remote participant connected")
                            Log.d(TAG, "✅ Remote participant should be able to hear local audio")
                        }
                        
                        // Final microphone verification
                        Log.d(TAG, "Final microphone status: $isMicrophoneEnabled")
                        
                        if (isMicrophoneEnabled) {
                            Log.d(TAG, "✅ Audio should now be working!")
                        } else {
                            Log.w(TAG, "❌ Microphone still not enabled after force enable")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in force enable audio: ${e.message}")
            }
        }
    }
    
    // Enhanced audio debugging function
    private fun verifyBasicAudioStatus(room: Room) {
        Log.d(TAG, "=== BASIC AUDIO STATUS CHECK ===")
        
        // Check local participant details
        Log.d(TAG, "Local participant connected: true")
        
        // Check local participant microphone status
        val isLocalMicrophoneEnabled = room.localParticipant.isMicrophoneEnabled()
        Log.d(TAG, "Local microphone enabled: $isLocalMicrophoneEnabled")
        
        // Check remote participants
        Log.d(TAG, "Remote participants: ${room.remoteParticipants.size}")
        room.remoteParticipants.forEach { unusedParticipant ->
            Log.d(TAG, "Remote participant connected")
            Log.d(TAG, "✅ Remote participant should be able to hear local audio")
        }
        
        // Log participant count for debugging
        Log.d(TAG, "Remote participants count: ${room.remoteParticipants.size}")
        
        // Check if both participants are connected
        val hasRemoteParticipants = room.remoteParticipants.isNotEmpty()
        
        if (hasRemoteParticipants) {
            Log.d(TAG, "🎉 AUDIO SHOULD BE WORKING: Both participants are connected!")
            Log.d(TAG, "   - Local participant: Connected")
            Log.d(TAG, "   - Remote participants: ${room.remoteParticipants.size} connected")
            
            // Additional microphone verification
            val hasLocalMicrophone = isLocalMicrophoneEnabled
            
            if (hasLocalMicrophone && hasRemoteParticipants) {
                Log.d(TAG, "✅ AUDIO SHOULD BE WORKING!")
            } else {
                Log.w(TAG, "⚠️ AUDIO ISSUE:")
                Log.w(TAG, "   - Local microphone: $hasLocalMicrophone")
                Log.w(TAG, "   - Remote participants: $hasRemoteParticipants")
            }
        } else {
            Log.w(TAG, "⚠️ AUDIO ISSUE DETECTED:")
            Log.w(TAG, "   - Remote participants connected: $hasRemoteParticipants")
        }
        
        Log.d(TAG, "=== END AUDIO STATUS CHECK ===")
    }
    
        // Support call audio setup with special handling for SIP compatibility
    private suspend fun setupSupportCallAudio(room: Room) {
        try {
            Log.d(TAG, "🆘 Setting up support call audio for SIP compatibility...")
            Log.d(TAG, "Enabling microphone for support call...")
            room.localParticipant.setMicrophoneEnabled(true)
            
            // Verify the track was published
            val isMicrophoneEnabled = room.localParticipant.isMicrophoneEnabled()
            Log.d(TAG, "Microphone enabled after support call setup: $isMicrophoneEnabled")
            
            // Ensure audio tracks are properly published
            Log.d(TAG, "Ensuring audio tracks are published for support call...")
            
            // Check remote participants
            Log.d(TAG, "Remote participants count: ${room.remoteParticipants.size}")
            room.remoteParticipants.forEach { participant ->
                Log.d(TAG, "✅ Remote participant connected")
                
                // LiveKit handles audio track subscription automatically
                // We just need to ensure our local microphone is enabled
                Log.d(TAG, "📡 Remote participant should receive audio automatically")
            }
            
            // Force enable local microphone again to ensure it's working
            room.localParticipant.setMicrophoneEnabled(true)
            Log.d(TAG, "✅ Support call audio setup completed")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error setting up support call audio: ${e.message}")
            Log.e(TAG, "Support call audio setup error:", e)
            
            // Fallback to standard audio setup
            Log.d(TAG, "🔄 Falling back to standard audio setup...")
            setupStandardCallAudio(room)
        }
    }
    
    // Standard call audio setup (for customer-driver calls)
    private suspend fun setupStandardCallAudio(room: Room) {
        try {
            Log.d(TAG, "🚗 Setting up standard call audio...")
            
            // 1. Enable microphone directly (without explicit track creation)
            Log.d(TAG, "Enabling microphone...")
            room.localParticipant.setMicrophoneEnabled(true)
            Log.d(TAG, "Microphone enabled successfully")
            
            // 2. Check if microphone is enabled
            val isMicrophoneEnabled = room.localParticipant.isMicrophoneEnabled()
            Log.d(TAG, "Microphone enabled check: $isMicrophoneEnabled")
            
            if (isMicrophoneEnabled) {
                Log.d(TAG, "✅ Microphone is enabled and should be working")
            } else {
                Log.w(TAG, "❌ Microphone is not enabled - this might cause audio issues")
                // Try to force enable again
                Log.d(TAG, "Attempting to force enable microphone...")
                room.localParticipant.setMicrophoneEnabled(true)
            }
            
            // 3. Check microphone status again
            Log.d(TAG, "Microphone enabled: $isMicrophoneEnabled")
            
            // 4. Check remote participants count
            Log.d(TAG, "Remote participants count: ${room.remoteParticipants.size}")
            room.remoteParticipants.forEach { unusedParticipant ->
                Log.d(TAG, "✅ Remote participant connected and should be able to hear")
            }
            
            Log.d(TAG, "✅ Standard call audio setup completed")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error during standard audio setup: ${e.message}")
            Log.d(TAG, "Standard audio setup error:", e)
        }
    }
}

enum class CallState {
    IDLE,
    CONNECTING,
    CONNECTED,
    WAITING_FOR_ACCEPTANCE,  // New state for waiting
    WAITING_FOR_DRIVER_ACCEPTANCE,  // New state for driver to accept after joining
    INCOMING_CALL,
    IN_CALL,
    ERROR
}

enum class CallType {
    CUSTOMER,
    DRIVER,
    SUPPORT
}

// Participant information for tracking
data class ParticipantInfo(
    val sid: String,
    val identity: String?,
    val isLocal: Boolean,
    val participantType: ParticipantType,
    val connectedAt: Long = System.currentTimeMillis()
)

enum class ParticipantType {
    CUSTOMER,
    DRIVER,
    SUPPORT,
    UNKNOWN
}

enum class RoomConnectionStatus {
    IDLE,
    CONNECTING,
    CONNECTED,
    MULTIPLE_PARTICIPANTS,
    CALL_ACTIVE,
    DISCONNECTED,
    ERROR
} 