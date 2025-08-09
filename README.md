# 📱 Kasookoo SDK (Android)

Production-ready calling flows for Customer ↔ Driver and Support, built with LiveKit (WebRTC) and Firebase Cloud Messaging (FCM).

## What’s included

- LiveKit integration with resilient audio setup (caller and callee symmetric)
- Customer ↔ Driver call flows using two token APIs:
  - `get-caller-livekit-token` (outgoing)
  - `get-called-livekit-token` (incoming)
- FCM-based incoming call notifications with strict routing rules
- Registration/Login to register and update FCM tokens with backend
- Device info as JSON object in all auth requests
- In-call UI (mute, speaker), Ringing UI, and automatic state transitions
- SIP-based Support calls (make/end) with dynamic `room_name`

## Requirements

- Android 7.0+ (API 24), Target SDK 34
- Kotlin 1.9+
- A Firebase project with valid `google-services.json`

## Setup (summary)

1) Dependencies (Gradle): LiveKit, Firebase Messaging, Retrofit/OkHttp, Coroutines.  
2) Permissions: INTERNET, ACCESS_NETWORK_STATE, RECORD_AUDIO, MODIFY_AUDIO_SETTINGS, POST_NOTIFICATIONS.  
3) Firebase: add `google-services.json`, apply `com.google.gms.google-services` plugin.  

## Key modules/files

- `core/LiveKitManager.kt`: Connect/disconnect, call states, participant tracking, audio setup, forced transitions
- `ui/MainActivity.kt`: Role-based entry, call initiation, global call type
- `ui/RingingActivity.kt`: Incoming/outgoing ringing, token fetch for callee, auto-accept when launched from notification action, no accept UI in that path
- `ui/CallActivity.kt`: In-call controls (mute, speaker), end-call UX
- `service/KasookooFirebaseMessagingService` (file name `FirebaseMessagingService.kt`): Receives FCM and routes notifications per rules
- `service/CallActionReceiver.kt`: Accept/Decline from notification; passes `auto_accept=true` for instant connect path
- `data/ApiService.kt` + `data/ApiClient.kt`: Retrofit endpoints for caller/called tokens, registration and update, support make/end
- `data/UserDataManager.kt` + `data/FirebaseTokenManager.kt`: Local user data and robust FCM token generation with retry/backoff

## Call flows

### Customer → Driver (Customer calls)
- App calls `get-caller-livekit-token` with:
  - `room_name` (dynamic), `participant_identity`, `participant_identity_name`, `participant_identity_type = customer`, `caller_user_id`
- Driver receives FCM type `customer_incoming_call`.
- Driver taps Accept (notification) → app fetches called token via `get-called-livekit-token` with:
  - `called_user_id` (driver), `participant_identity` (from driver device), `participant_identity_name`, `participant_identity_type = driver`, `room_name`
- Driver Ringing screen: when accepted from notification, the accept UI is skipped (shows Connecting → Connected only).
- When both join, customer becomes IN_CALL immediately; driver transitions to IN_CALL automatically.

### Driver → Customer (Driver calls)
- App calls `get-caller-livekit-token` with caller as driver (`participant_identity_type = driver`).
- Customer receives FCM type `driver_incoming_call` and accepts from notification.
- App fetches called token with `participant_identity_type = customer` and joins the room. Both UIs go IN_CALL.

### Support call (SIP)
- `makeSupportCall` with dynamic `room_name` and local `participant_name`.
- Token from response is used to join LiveKit; support agent join flips state to IN_CALL.  
- `endSupportCall` uses stored `participant_identity` and `room_name`.

## Notification routing rules

- `customer_incoming_call` → shown on Driver devices only
- `driver_incoming_call` → shown on Customer devices only
- We inspect local stored role to decide whether to show/suppress
- Accept from notification sets `auto_accept=true`: the driver’s accept UI is hidden; the screen shows Connecting, then Connected

## Authentication and FCM token registration

- Registration (`register-caller-or-called-for-firebase-token`):
  - Body: `user_type`, `user_id`, `device_token` (FCM), `device_info` (JSON map), `device_type="android"`
- Login/Update (`update-caller-or-called-for-firebase-token`):
  - Body: `user_type`, `user_id`, `device_token` (old), `new_device_token` (fresh FCM), `device_info` (JSON map), `device_type="android"`
- `device_info` is a JSON object, e.g. `{ platform, version, api_level, manufacturer, model, device_id }`
- FCM token generation includes validation and retry with exponential backoff

## Participant identity

- `participant_identity` is derived from the local user’s name:  
  `customer_{full_name_sanitized}` or `driver_{full_name_sanitized}`
- The callee always sends its own role in `participant_identity_type`

## UI/UX notes

- Ringing (driver, accepted via notification): accept UI removed automatically
- Background updated to white/green theme; driver’s Support card hidden; logout icon updated
- Speaker control: real toggle with icons; default speaker ON in-call and restored on exit

### Handling Incoming Calls

```kotlin
// Firebase messaging service for drivers
class CallNotificationService : FirebaseMessagingService() {
    
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        if (remoteMessage.data["type"] == "incoming_call") {
            showIncomingCallNotification(
                callerName = remoteMessage.data["caller_name"] ?: "Customer",
                callId = remoteMessage.data["call_id"] ?: "",
                token = remoteMessage.data["livekit_token"] ?: "",
                roomName = remoteMessage.data["room_name"] ?: ""
            )
        }
    }
    
    private fun showIncomingCallNotification(
        callerName: String,
        callId: String,
        token: String,
        roomName: String
    ) {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_call)
            .setContentTitle("Incoming Call")
            .setContentText("$callerName is calling...")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setFullScreenIntent(createCallIntent(callId, token), true)
            .addAction(R.drawable.ic_call, "Answer", createAnswerIntent(callId))
            .addAction(R.drawable.ic_call_end, "Decline", createDeclineIntent(callId))
            .build()
            
        notificationManager.notify(NOTIFICATION_ID, notification)
    }
}
```

### Driver Call Interface

```kotlin
class DriverCallActivity : AppCompatActivity() {
    
    // Accept incoming call
    private fun acceptCall() {
        lifecycleScope.launch {
            liveKitManager.connectToRoom(
                token = intent.getStringExtra("token") ?: "",
                wsUrl = intent.getStringExtra("wsUrl") ?: "",
                roomName = intent.getStringExtra("roomName") ?: "",
                callType = CallType.DRIVER
            )
        }
    }
    
    // Hands-free controls for drivers
    private fun setupDriverControls() {
        // Enable speaker by default for hands-free
        audioManager.isSpeakerphoneOn = true
        
        // Large buttons for easy access while driving
        binding.muteButton.minimumHeight = 120.dp
        binding.endCallButton.minimumHeight = 120.dp
    }
}
```

## Disconnect behavior

- End call sets local state to `IDLE` immediately and disconnects the LiveKit room; the remote peer is notified by LiveKit
- Callee path ensures global call type is set so the End button always works
- If call type is already cleared, the in-call screen finishes gracefully

### Handling Call Actions

```kotlin
class CallActionReceiver : BroadcastReceiver() {
    
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            "ACTION_ANSWER_CALL" -> {
                val callId = intent.getStringExtra("CALL_ID")
                // Launch call activity
                launchCallActivity(context, callId, autoAnswer = true)
            }
            
            "ACTION_DECLINE_CALL" -> {
                val callId = intent.getStringExtra("CALL_ID")
                // Dismiss notification and decline call
                dismissNotification(context)
                declineCall(callId)
            }
        }
    }
}
```

## API (actual backend contract used)

Caller (outgoing):
```json
POST /api/v1/bot/sdk/get-caller-livekit-token
{
  "room_name": "room_...",
  "participant_identity": "customer_vasyl",
  "participant_identity_name": "Vasyl",
  "participant_identity_type": "customer|driver",
  "caller_user_id": "..."
}
```

Called (incoming):
```json
POST /api/v1/bot/sdk/get-called-livekit-token
{
  "room_name": "room_...",
  "participant_identity": "driver_waseem_akhtar",
  "participant_identity_name": "Waseem Akhtar",
  "participant_identity_type": "driver|customer",
  "called_user_id": "..."
}
```

Token responses are flat objects: `{ "accessToken": "...", "wsUrl": "wss://..." }`

## Troubleshooting

- Notifications go to the wrong device: verify FCM type and local stored role; routing suppresses mismatched roles
- Called token failure due to lifecycle cancellation: Ringing uses a SupervisorJob scope and suppresses finish until token arrives
- “Call already ended” when ending from callee: fixed by setting global call type on join; screen now finishes even if type is cleared

## Version

- Version: 1.1.0  
- Last Updated: August 2025  
- Min Android: API 24  
- Target Android: API 34

## 🧪 Testing

### Single Device Testing

```kotlin
// For testing without multiple devices
private fun simulateCall() {
    // Auto-progress through call states for project
    Handler(Looper.getMainLooper()).postDelayed({
        _callState.value = CallState.CONNECTING
    }, 1000)
    
    Handler(Looper.getMainLooper()).postDelayed({
        _callState.value = CallState.IN_CALL
    }, 4000)
}
```

### Multi-Device Testing

1. **Setup**: Install app on two devices
2. **Device 1**: Select "Customer" → Initiate call
3. **Device 2**: Select "Driver" → Receive notification
4. **Verify**: Audio connection between devices

### Test Scenarios

- **Customer-to-Driver**: Basic call flow
- **Notification Handling**: Background call reception
- **Audio Quality**: Clear communication
- **Error Recovery**: Network interruption handling
- **Call History**: Proper tracking and storage

## 🚨 Troubleshooting

### Common Issues

**Audio Not Working**
```kotlin
// Check audio permissions and setup
private fun verifyAudioSetup(): Boolean {
    val hasPermission = ContextCompat.checkSelfPermission(
        this, 
        Manifest.permission.RECORD_AUDIO
    ) == PackageManager.PERMISSION_GRANTED
    
    val audioMode = audioManager.mode == AudioManager.MODE_IN_COMMUNICATION
    
    return hasPermission && audioMode
}
```

**Connection Failures**
```kotlin
// Monitor connection status
liveKitManager.roomConnectionStatus.collect { status ->
    when (status) {
        RoomConnectionStatus.ERROR -> {
            showError("Connection failed. Check internet connection.")
        }
        RoomConnectionStatus.DISCONNECTED -> {
            showError("Call disconnected. Attempting to reconnect...")
            retryConnection()
        }
    }
}
```

**Notifications Not Received**
```kotlin
// Verify notification setup
private fun checkNotificationPermissions(): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    } else {
        NotificationManagerCompat.from(this).areNotificationsEnabled()
    }
}
```

## 💡 Best Practices

### For Customers
- **Request permissions** before initiating calls
- **Provide clear feedback** for all call states
- **Handle errors gracefully** with retry mechanisms
- **Test audio quality** on various devices

### For Drivers
- **Optimize for hands-free** operation
- **Use large UI elements** for easy access while driving
- **Enable speaker mode** by default
- **Provide clear call status** indicators

### Performance
- **Use coroutines** for non-blocking operations
- **Implement proper lifecycle** management
- **Monitor battery usage** during long calls
- **Handle background states** correctly

## 📖 API Reference

### Core Classes

#### LiveKitManager
```kotlin
class LiveKitManager(context: Context) {
    suspend fun connectToRoom(token: String, wsUrl: String, roomName: String, callType: CallType)
    suspend fun disconnectFromRoom()
    fun toggleMicrophone()
    fun forceEnableAudio()
    
    val callState: StateFlow<CallState>
    val isConnected: StateFlow<Boolean>
    val participantCount: StateFlow<Int>
}
```

#### CallRepository
```kotlin
class CallRepository {
    suspend fun getLiveKitToken(roomName: String, participantIdentity: String): Result<TokenResponse>
}
```

#### CallHistoryManager
```kotlin
class CallHistoryManager(context: Context) {
    fun addCallRecord(record: CallRecord)
    fun getCallHistory(): List<CallRecord>
    fun updateCallRecord(id: String, endTime: Long, status: CallStatus)
}
```

### Enums

```kotlin
enum class CallState {
    IDLE, CONNECTING, CONNECTED, IN_CALL, ERROR
}

enum class CallType {
    CUSTOMER, DRIVER, SUPPORT
}

enum class CallStatus {
    IN_PROGRESS, COMPLETED, MISSED, FAILED
}
```

## 🔗 Links

- [LiveKit Documentation](https://docs.livekit.io/)
- [Firebase Cloud Messaging](https://firebase.google.com/docs/cloud-messaging)
- [Android Audio Development](https://developer.android.com/guide/topics/media/audio)

---

**Version**: 1.0.0  
**Last Updated**: July 2025  
**Minimum Android Version**: API 24 (Android 7.0)  
**Target Android Version**: API 34 (Android 14) 