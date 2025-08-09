# 📱  Kasookoo SDK SDK

A powerful Android SDK for real-time voice communication between customers and drivers, built with LiveKit WebRTC technology and Firebase Cloud Messaging.

## 🎯 Overview

The  Kasookoo SDK SDK enables seamless voice calling functionality in Android applications, specifically designed for ride-sharing, delivery, and customer service scenarios. It provides a complete solution for both customers initiating calls and drivers receiving them.

## ✨ Features

- 🎙️ **High-Quality Voice Calls** - Crystal clear audio using LiveKit WebRTC
- 🔔 **Push Notifications** - Instant call notifications via Firebase FCM
- 📊 **Call History Management** - Automatic tracking and storage
- 🎨 **Ready-to-Use UI** - Beautiful call interfaces included
- 🔄 **Real-Time Connection Status** - Live participant monitoring
- 🛡️ **Robust Error Handling** - Comprehensive error management

## 📋 Prerequisites

- **Android API Level**: 24+ (Android 7.0)
- **Target SDK**: 34 (Android 14)
- **Kotlin**: 1.9.22+
- **Firebase Project** with FCM enabled

## 🚀 Quick Start

### 1. Installation

Add dependencies to your `app/build.gradle`:

```gradle
dependencies {
    // Core LiveKit SDK - Stable version
    implementation 'io.livekit:livekit-android:1.5.0'
    
    // Firebase for notifications
    implementation platform('com.google.firebase:firebase-bom:32.6.0')
    implementation 'com.google.firebase:firebase-messaging-ktx'
    
    // Networking
    implementation 'com.squareup.retrofit2:retrofit:2.9.0'
    implementation 'com.squareup.retrofit2:converter-gson:2.9.0'
    implementation 'com.squareup.okhttp3:logging-interceptor:4.12.0'
    
    // Coroutines
    implementation 'org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3'
}
```

### 2. Permissions

Add to `AndroidManifest.xml`:

```xml
<!-- Audio permissions -->
<uses-permission android:name="android.permission.RECORD_AUDIO" />
<uses-permission android:name="android.permission.MODIFY_AUDIO_SETTINGS" />

<!-- Network permissions -->
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />

<!-- Notification permissions -->
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
```

### 3. Firebase Setup

1. Download `google-services.json` from Firebase Console
2. Place in your `app/` directory
3. Apply plugin in `app/build.gradle`:

```gradle
plugins {
    id 'com.google.gms.google-services'
}
```

### 4. Initialize SDK

```kotlin
class YourApplication : Application() {
    lateinit var liveKitManager: LiveKitManager
    
    override fun onCreate() {
        super.onCreate()
        liveKitManager = LiveKitManager(this)
    }
}
```

## 👥 End-User Implementation

### Making a Call

```kotlin
class MainActivity : AppCompatActivity() {
    private lateinit var liveKitManager: LiveKitManager
    private val repository = CallRepository()
    
    private fun initiateCall() {
        lifecycleScope.launch {
            try {
                // Get authentication token
                val tokenResult = repository.getLiveKitToken("room-name", "user-id")
                
                tokenResult.onSuccess { response ->
                    // Connect to call room
                    liveKitManager.connectToRoom(
                        token = response.accessToken,
                        wsUrl = response.wsUrl,
                        roomName = "room-name",
                        callType = CallType.CUSTOMER
                    )
                }
            } catch (e: Exception) {
                handleError("Failed to initiate call: ${e.message}")
            }
        }
    }
}
```

### Monitoring Call State

```kotlin
// Observe call state changes
lifecycleScope.launch {
    liveKitManager.callState.collect { state ->
        when (state) {
            CallState.IDLE -> {
                // No active call
            }
            CallState.CONNECTING -> {
                showConnectingUI()
            }
            CallState.CONNECTED -> {
                // Connected, waiting for driver
            }
            CallState.IN_CALL -> {
                // Active call with driver
                navigateToCallScreen()
            }
            CallState.ERROR -> {
                handleCallError()
            }
        }
    }
}
```

### Call Controls

```kotlin
// During an active call
class CallActivity : AppCompatActivity() {
    
    // Mute/unmute microphone
    private fun toggleMute() {
        liveKitManager.toggleMicrophone()
        updateMuteButton()
    }
    
    // Enable/disable speaker
    private fun toggleSpeaker() {
        audioManager.isSpeakerphoneOn = !audioManager.isSpeakerphoneOn
        updateSpeakerButton()
    }
    
    // End call
    private fun endCall() {
        liveKitManager.disconnectFromRoom()
        finish()
    }
}
```

## 🚗 Driver Implementation

### Setting Up as Driver

```kotlin
class DriverActivity : AppCompatActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate()
        
        // Configure as driver
        setupDriverMode()
        
        // Connect to monitoring room
        connectToDriverRoom()
    }
    
    private fun setupDriverMode() {
        // Driver-specific UI setup
        isCustomer = false
        showDriverInterface()
        
        // Monitor for incoming calls
        observeIncomingCalls()
    }
}
```

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

## 🔔 Notification System

### Setting Up Notifications

```kotlin
// Create notification channel
private fun createNotificationChannel() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Call Notifications",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Notifications for incoming calls"
        }
        
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
    }
}
```

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

## 📊 Call History Management

### Tracking Calls

```kotlin
val callHistoryManager = CallHistoryManager(context)

// Get call history
val callHistory = callHistoryManager.getCallHistory()

// Add new call record
val callRecord = CallRecord(
    id = UUID.randomUUID().toString(),
    callType = CallType.CUSTOMER,
    participantIdentity = "customer_123",
    startTime = System.currentTimeMillis(),
    endTime = null,
    duration = 0,
    status = CallStatus.IN_PROGRESS
)

callHistoryManager.addCallRecord(callRecord)
```

### Call Analytics

```kotlin
// Get statistics
class CallAnalytics {
    fun getTotalCalls(): Int = callHistory.size
    
    fun getAverageCallDuration(): Long {
        return callHistory
            .filter { it.status == CallStatus.COMPLETED }
            .map { it.duration }
            .average()
            .toLong()
    }
    
    fun getAnswerRate(): Float {
        val totalCalls = callHistory.filter { it.callType == CallType.DRIVER }.size
        val answeredCalls = callHistory.filter { 
            it.callType == CallType.DRIVER && it.status == CallStatus.COMPLETED 
        }.size
        
        return if (totalCalls > 0) (answeredCalls.toFloat() / totalCalls) * 100 else 0f
    }
}
```

## 🌐 Backend Integration

### API Endpoint

Your backend needs to provide authentication tokens:

```bash
POST https://your-api.com/api/v1/bot/sdk/get-token
Content-Type: application/json

{
    "room_name": "sdk-room",
    "participant_identity": "customer_123"
}
```

### Response Format

```json
{
    "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "wsUrl": "wss://your-livekit-server.com"
}
```

### API Client Implementation

```kotlin
object ApiClient {
    private const val BASE_URL = "https://your-api.com/"
    
    private val retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
    
    val apiService: ApiService = retrofit.create(ApiService::class.java)
}

interface ApiService {
    @POST("api/v1/bot/sdk/get-token")
    suspend fun getLiveKitToken(@Body request: TokenRequest): Response<TokenResponse>
}

data class TokenRequest(
    val room_name: String,
    val participant_identity: String
)

data class TokenResponse(
    val accessToken: String,
    val wsUrl: String
)
```

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