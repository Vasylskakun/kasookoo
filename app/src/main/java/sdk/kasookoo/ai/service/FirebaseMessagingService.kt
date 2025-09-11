package sdk.kasookoo.ai.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import sdk.kasookoo.ai.R
import sdk.kasookoo.ai.ui.MainActivity
import sdk.kasookoo.ai.ui.RingingActivity
import sdk.kasookoo.ai.data.FirebaseTokenManager
import sdk.kasookoo.ai.data.UserDataManager

class KasookooFirebaseMessagingService : FirebaseMessagingService() {
    
    companion object {
        private const val TAG = "FirebaseMessaging"
        private const val CHANNEL_ID = "kasookoo_calls"
        private const val CHANNEL_NAME = "Kasookoo Calls"
        private const val CHANNEL_DESCRIPTION = "Notifications for incoming calls"
        
        // Notification IDs
        private const val INCOMING_CALL_NOTIFICATION_ID = 1001
        private const val GENERAL_NOTIFICATION_ID = 1002
    }
    
    private lateinit var firebaseTokenManager: FirebaseTokenManager
    private lateinit var userDataManager: UserDataManager
    
    override fun onCreate() {
        super.onCreate()
        firebaseTokenManager = FirebaseTokenManager(this)
        userDataManager = UserDataManager(this)
    }
    
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "🔄 New FCM token: $token")
        
        // Save FCM token locally
        firebaseTokenManager.saveFCMToken(token)
        Log.d(TAG, "✅ FCM token saved locally: ${token.take(20)}...")
        
        // Note: Backend will get FCM token when user registers/logs in
        Log.d(TAG, "📤 Backend will receive FCM token during registration/login")

        // Diagnostic: Check if token is properly saved
        val savedToken = firebaseTokenManager.getStoredFCMToken()
        Log.d(TAG, "🔍 FCM Token Status:")
        Log.d(TAG, "   - Saved token exists: ${savedToken != null}")
        Log.d(TAG, "   - Token length: ${savedToken?.length ?: 0}")
        Log.d(TAG, "   - Token preview: ${savedToken?.take(20)}...")
    }
    
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        Log.d(TAG, "📨 ===== FCM MESSAGE RECEIVED =====")
        Log.d(TAG, "   - From: ${remoteMessage.from}")
        Log.d(TAG, "   - Message ID: ${remoteMessage.messageId}")
        Log.d(TAG, "   - Sent Time: ${remoteMessage.sentTime}")
        Log.d(TAG, "   - TTL: ${remoteMessage.ttl}")
        Log.d(TAG, "   - Data: ${remoteMessage.data}")
        Log.d(TAG, "   - Notification: ${remoteMessage.notification}")
        Log.d(TAG, "   - Data keys: ${remoteMessage.data.keys}")
        Log.d(TAG, "   ==================================")

        // Check if this is a silent notification (data-only message)
        val hasVisibleNotification = remoteMessage.notification != null &&
                                   (!remoteMessage.notification?.title.isNullOrEmpty() ||
                                    !remoteMessage.notification?.body.isNullOrEmpty())

        if (hasVisibleNotification) {
            Log.d(TAG, "📢 Visible notification message received")
        } else {
            Log.d(TAG, "🔕 Silent notification message received (data-only)")
        }

        // Decide visibility based on local role and message type
        val type = remoteMessage.data["type"] ?: ""
        val localUserType = userDataManager.getUserType()?.lowercase()
        Log.d(TAG, "📟 Local user type: $localUserType, Message type: $type")

        when (type) {
            // Callee rejected incoming call (sent by backend as silent notification)
            "reject_incoming_call" -> {
                Log.d(TAG, "⛔ Received reject_incoming_call notification - ending ringing/connecting UI")
                handleRejectIncomingCall(remoteMessage)
            }
            // Customer called driver → show on driver device only
            "customer_incoming_call" -> {
                if (localUserType == "driver") {
                    if (hasVisibleNotification) {
                        // Traditional notification message
                        handleCustomerIncomingCall(remoteMessage)
                    } else {
                        // Silent notification message - handle silently
                        handleSilentIncomingCall(remoteMessage)
                    }
                } else {
                    Log.d(TAG, "🚫 Suppressing customer_incoming_call for non-driver device")
                }
            }
            // Driver called customer → show on customer device only
            "driver_incoming_call" -> {
                if (localUserType == "customer") {
                    if (hasVisibleNotification) {
                        // Traditional notification message
                        handleDriverIncomingCall(remoteMessage)
                    } else {
                        // Silent notification message - handle silently
                        handleSilentIncomingCall(remoteMessage)
                    }
                } else {
                    Log.d(TAG, "🚫 Suppressing driver_incoming_call for non-customer device")
                }
            }
            // Support call - handle silently for all users who can receive support calls
            "support_incoming_call" -> {
                if (hasVisibleNotification) {
                    // Traditional notification message
                    handleSupportIncomingCall(remoteMessage)
                } else {
                    // Silent notification message - handle silently
                    handleSilentIncomingCall(remoteMessage)
                }
            }
            "call_ended" -> handleCallEnded(remoteMessage)
            "general" -> handleGeneralNotification(remoteMessage)
            else -> {
                Log.w(TAG, "⚠️ Unknown message type: $type")
                handleGeneralNotification(remoteMessage)
            }
        }
    }
    
    private fun handleCustomerIncomingCall(remoteMessage: RemoteMessage) {
        val data = remoteMessage.data
        
        // Extract notification data according to specification
        val callerName = remoteMessage.notification?.title?.replace("Incoming Call from ", "") ?: "Customer"
        val roomName = data["room_name"] ?: ""
        val participantIdentity = data["participant_identity"] ?: ""
        val type = data["type"] ?: ""
        val action = data["action"] ?: ""
        
        Log.d(TAG, "📞 Handling customer incoming call:")
        Log.d(TAG, "   - Type: $type")
        Log.d(TAG, "   - Action: $action")
        Log.d(TAG, "   - Caller Name: $callerName")
        Log.d(TAG, "   - Room Name: $roomName")
        Log.d(TAG, "   - Participant Identity: $participantIdentity")
        
        // Extract IDs for backend flows
        val callerUserId = data["caller_user_id"] ?: ""
        val calledUserId = data["called_user_id"] ?: ""

        // Show incoming call notification for driver receiving customer call
        showIncomingCallNotification(
            callType = "customer",
            callerName = callerName,
            roomName = roomName,
            participantIdentity = participantIdentity,
            callerUserId = callerUserId,
            calledUserId = calledUserId
        )
    }
    
    private fun handleDriverIncomingCall(remoteMessage: RemoteMessage) {
        val data = remoteMessage.data
        
        // Extract notification data according to specification
        val callerName = remoteMessage.notification?.title?.replace("Incoming Call from ", "") ?: "Driver"
        val roomName = data["room_name"] ?: ""
        val participantIdentity = data["participant_identity"] ?: ""
        val type = data["type"] ?: ""
        val action = data["action"] ?: ""
        
        Log.d(TAG, "📞 Handling driver incoming call:")
        Log.d(TAG, "   - Type: $type")
        Log.d(TAG, "   - Action: $action")
        Log.d(TAG, "   - Caller Name: $callerName")
        Log.d(TAG, "   - Room Name: $roomName")
        Log.d(TAG, "   - Participant Identity: $participantIdentity")
        
        // Extract IDs for backend flows
        val callerUserId = data["caller_user_id"] ?: ""
        val calledUserId = data["called_user_id"] ?: ""

        // Show incoming call notification for customer receiving driver call
        showIncomingCallNotification(
            callType = "driver",
            callerName = callerName,
            roomName = roomName,
            participantIdentity = participantIdentity,
            callerUserId = callerUserId,
            calledUserId = calledUserId
        )
    }
    
    private fun handleCallEnded(remoteMessage: RemoteMessage) {
        val data = remoteMessage.data
        val callType = data["call_type"] ?: "unknown"
        val duration = data["duration"] ?: "0"
        
        Log.d(TAG, "📞 Call ended notification:")
        Log.d(TAG, "   - Call Type: $callType")
        Log.d(TAG, "   - Duration: $duration seconds")
        
        // Show call ended notification
        showCallEndedNotification(callType, duration)
    }
    
    private fun handleGeneralNotification(remoteMessage: RemoteMessage) {
        val title = remoteMessage.notification?.title ?: "Kasookoo"
        val body = remoteMessage.notification?.body ?: "You have a new message"
        
        Log.d(TAG, "📢 General notification:")
        Log.d(TAG, "   - Title: $title")
        Log.d(TAG, "   - Body: $body")
        
        showGeneralNotification(title, body)
    }
    
    private fun showIncomingCallNotification(
        callType: String,
        callerName: String,
        roomName: String,
        participantIdentity: String,
        callerUserId: String = "",
        calledUserId: String = ""
    ) {
        // Create notification channel for Android O and above
        createNotificationChannel()
        
        // Create intent for when notification is tapped
        val intent = Intent(this, RingingActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("call_type", callType)
            putExtra("caller_name", callerName)
            putExtra("room_name", roomName)
            putExtra("participant_identity", participantIdentity)
            putExtra("caller_user_id", callerUserId)
            putExtra("called_user_id", calledUserId)
            putExtra("is_incoming_call", true)
        }
        
        val pendingIntent = PendingIntent.getActivity(
            this,
            INCOMING_CALL_NOTIFICATION_ID,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        // Create accept call intent
        val acceptIntent = Intent(this, CallActionReceiver::class.java).apply {
            action = "ACCEPT_CALL"
            putExtra("call_type", callType)
            putExtra("caller_name", callerName)
            putExtra("room_name", roomName)
            putExtra("participant_identity", participantIdentity)
            putExtra("caller_user_id", callerUserId)
            putExtra("called_user_id", calledUserId)
        }
        
        val acceptPendingIntent = PendingIntent.getBroadcast(
            this,
            INCOMING_CALL_NOTIFICATION_ID + 1,
            acceptIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        // Create decline call intent
        val declineIntent = Intent(this, CallActionReceiver::class.java).apply {
            action = "DECLINE_CALL"
            putExtra("call_type", callType)
            putExtra("room_name", roomName)
            putExtra("participant_identity", participantIdentity)
            putExtra("caller_user_id", callerUserId)
            putExtra("called_user_id", calledUserId)
            // Include local user role so receiver can route correctly if needed
            val localUserType = userDataManager.getUserType()
            putExtra("local_user_type", localUserType)
        }
        
        val declinePendingIntent = PendingIntent.getBroadcast(
            this,
            INCOMING_CALL_NOTIFICATION_ID + 2,
            declineIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        // Build notification
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Incoming Call")
            .setContentText("$callerName is calling...")
            .setSmallIcon(R.drawable.ic_call_modern)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .addAction(R.drawable.ic_call_modern, "Accept", acceptPendingIntent)
            .addAction(R.drawable.ic_call_end, "Decline", declinePendingIntent)
            .setOngoing(true)
            .build()
        
        // Show notification
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(INCOMING_CALL_NOTIFICATION_ID, notification)
        
        Log.d(TAG, "✅ Incoming call notification shown")
    }
    
    private fun showCallEndedNotification(callType: String, duration: String) {
        createNotificationChannel()
        
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        
        val pendingIntent = PendingIntent.getActivity(
            this,
            GENERAL_NOTIFICATION_ID,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Call Ended")
            .setContentText("Call ended after $duration seconds")
            .setSmallIcon(R.drawable.ic_call_end)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()
        
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(GENERAL_NOTIFICATION_ID, notification)
        
        Log.d(TAG, "✅ Call ended notification shown")
    }
    
    private fun showGeneralNotification(title: String, body: String) {
        createNotificationChannel()
        
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        
        val pendingIntent = PendingIntent.getActivity(
            this,
            GENERAL_NOTIFICATION_ID,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(body)
            .setSmallIcon(R.drawable.ic_notification)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()
        
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(GENERAL_NOTIFICATION_ID, notification)
        
        Log.d(TAG, "✅ General notification shown")
    }

    /**
     * Handle rejection of an incoming call (silent notification to the caller)
     */
    private fun handleRejectIncomingCall(remoteMessage: RemoteMessage) {
        val data = remoteMessage.data

        val roomName = data["room_name"] ?: ""
        val callerUserId = data["caller_user_id"] ?: ""
        val calledUserId = data["called_user_id"] ?: ""

        Log.d(TAG, "⛔ Call rejected by callee:")
        Log.d(TAG, "   - Room: $roomName")
        Log.d(TAG, "   - Caller: $callerUserId")
        Log.d(TAG, "   - Callee: $calledUserId")

        // Dismiss any ongoing incoming-call notification
        try {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.cancel(INCOMING_CALL_NOTIFICATION_ID)
        } catch (e: Exception) {
            Log.w(TAG, "⚠️ Failed to cancel incoming notification: ${e.message}")
        }

        // Navigate user back to main screen with a toast
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("show_toast", "Call rejected by callee")
        }
        startActivity(intent)

        Log.d(TAG, "✅ Caller notified about call rejection")
    }

    /**
     * Handle silent incoming call notifications (data-only messages)
     * This launches the ringing screen directly without showing visible notifications
     */
    private fun handleSilentIncomingCall(remoteMessage: RemoteMessage) {
        val data = remoteMessage.data

        Log.d(TAG, "🔕 Handling silent incoming call notification")

        // Extract call data from the data payload
        val callerName = data["participant_identity_name"] ?: "Unknown Caller"
        val roomName = data["room_name"] ?: ""
        val participantIdentity = data["participant_identity"] ?: ""
        val callType = when (data["type"]) {
            "customer_incoming_call" -> "customer"  // Customer calling driver → call type is customer
            "driver_incoming_call" -> "driver"      // Driver calling customer → call type is driver
            "support_incoming_call" -> "support"
            else -> "unknown"
        }
        val callerUserId = data["caller_user_id"] ?: ""
        val calledUserId = data["called_user_id"] ?: ""

        Log.d(TAG, "📞 Silent call details:")
        Log.d(TAG, "   - Caller: $callerName")
        Log.d(TAG, "   - Room: $roomName")
        Log.d(TAG, "   - Type: $callType")
        Log.d(TAG, "   - Participant ID: $participantIdentity")

        // Create intent to launch ringing activity directly
        val intent = Intent(this, sdk.kasookoo.ai.ui.RingingActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                   Intent.FLAG_ACTIVITY_CLEAR_TOP or
                   Intent.FLAG_ACTIVITY_SINGLE_TOP

            // Pass call data
            putExtra("call_type", callType)
            putExtra("caller_name", callerName)
            putExtra("room_name", roomName)
            putExtra("participant_identity", participantIdentity)
            putExtra("is_incoming_call", true)
            putExtra("is_silent_notification", true) // Flag to indicate silent notification
            putExtra("caller_user_id", callerUserId)
            putExtra("called_user_id", calledUserId)
        }

        Log.d(TAG, "🚀 Launching ringing activity for silent notification")
        startActivity(intent)

        Log.d(TAG, "✅ Silent incoming call notification handled - ringing screen launched")
    }

    /**
     * Handle traditional support call notifications (with title and body)
     * This shows visible notification for support calls
     */
    private fun handleSupportIncomingCall(remoteMessage: RemoteMessage) {
        val data = remoteMessage.data

        // Extract notification data according to specification
        val callerName = remoteMessage.notification?.title?.replace("Incoming Call from ", "") ?: "Support"
        val roomName = data["room_name"] ?: ""
        val participantIdentity = data["participant_identity"] ?: ""
        val type = data["type"] ?: ""
        val action = data["action"] ?: ""

        Log.d(TAG, "🆘 Handling support incoming call:")
        Log.d(TAG, "   - Type: $type")
        Log.d(TAG, "   - Action: $action")
        Log.d(TAG, "   - Caller Name: $callerName")
        Log.d(TAG, "   - Room Name: $roomName")
        Log.d(TAG, "   - Participant Identity: $participantIdentity")

        // Show incoming call notification for support
        showIncomingCallNotification("support", callerName, roomName, participantIdentity)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = CHANNEL_DESCRIPTION
                enableLights(true)
                enableVibration(true)
                setShowBadge(true)
            }
            
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
            
            Log.d(TAG, "✅ Notification channel created")
        }
    }
}

