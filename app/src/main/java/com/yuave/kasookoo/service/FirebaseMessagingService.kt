package com.yuave.kasookoo.service

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
import com.yuave.kasookoo.R
import com.yuave.kasookoo.ui.MainActivity
import com.yuave.kasookoo.ui.RingingActivity
import com.yuave.kasookoo.data.FirebaseTokenManager
import com.yuave.kasookoo.data.UserDataManager

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
    }
    
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        
        Log.d(TAG, "📨 Received FCM message:")
        Log.d(TAG, "   - From: ${remoteMessage.from}")
        Log.d(TAG, "   - Data: ${remoteMessage.data}")
        Log.d(TAG, "   - Notification: ${remoteMessage.notification}")
        
        // Decide visibility based on local role and message type
        val type = remoteMessage.data["type"] ?: ""
        val localUserType = userDataManager.getUserType()?.lowercase()
        Log.d(TAG, "📟 Local user type: $localUserType, Message type: $type")

        when (type) {
            // Customer called driver → show on driver device only
            "customer_incoming_call" -> {
                if (localUserType == "driver") {
                    handleCustomerIncomingCall(remoteMessage)
                } else {
                    Log.d(TAG, "🚫 Suppressing customer_incoming_call for non-driver device")
                }
            }
            // Driver called customer → show on customer device only
            "driver_incoming_call" -> {
                if (localUserType == "customer") {
                    handleDriverIncomingCall(remoteMessage)
                } else {
                    Log.d(TAG, "🚫 Suppressing driver_incoming_call for non-customer device")
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
        
        // Show incoming call notification for driver receiving customer call
        showIncomingCallNotification("customer", callerName, roomName, participantIdentity)
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
        
        // Show incoming call notification for customer receiving driver call
        showIncomingCallNotification("driver", callerName, roomName, participantIdentity)
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
        participantIdentity: String
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

