package com.yuave.kasookoo.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.yuave.kasookoo.R
import com.yuave.kasookoo.core.CallType
import com.yuave.kasookoo.ui.RingingActivity
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class CallNotificationService : FirebaseMessagingService() {
    
    companion object {
        private const val TAG = "CallNotificationService"
        private const val CHANNEL_ID = "call_notification_channel"
        private const val NOTIFICATION_ID = 1001
    }
    
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }
    
    override fun onNewToken(token: String) {
        Log.d(TAG, "Refreshed token: $token")
        // Send token to your server
        sendRegistrationToServer(token)
    }
    
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        Log.d(TAG, "From: ${remoteMessage.from}")
        
        // Check if message contains data payload
        if (remoteMessage.data.isNotEmpty()) {
            Log.d(TAG, "Message data payload: ${remoteMessage.data}")
            handleDataMessage(remoteMessage.data)
        }
        
        // Check if message contains notification payload
        remoteMessage.notification?.let {
            Log.d(TAG, "Message Notification Body: ${it.body}")
            // Handle notification payload if needed
        }
    }
    
    private fun handleDataMessage(data: Map<String, String>) {
        val messageType = data["type"]
        
        when (messageType) {
            "incoming_call" -> {
                val callType = data["call_type"] ?: "DRIVER"
                val callerName = data["caller_name"] ?: "Unknown"
                val callId = data["call_id"] ?: ""
                val token = data["livekit_token"] ?: ""
                val roomName = data["room_name"] ?: ""
                
                showIncomingCallNotification(callType, callerName, callId, token, roomName)
            }
            "call_ended" -> {
                dismissCallNotification()
            }
        }
    }
    
    private fun showIncomingCallNotification(
        callType: String,
        callerName: String,
        callId: String,
        token: String,
        roomName: String
    ) {
        val intent = Intent(this, RingingActivity::class.java).apply {
            putExtra("CALL_TYPE", callType)
            putExtra("CALLER_NAME", callerName)
            putExtra("CALL_ID", callId)
            putExtra("TOKEN", token)
            putExtra("ROOM_NAME", roomName)
            putExtra("IS_INCOMING", true)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent, 
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        
        // Answer action
        val answerIntent = Intent(this, CallActionReceiver::class.java).apply {
            action = "ACTION_ANSWER_CALL"
            putExtra("CALL_ID", callId)
        }
        val answerPendingIntent = PendingIntent.getBroadcast(
            this, 1, answerIntent, 
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        
        // Decline action
        val declineIntent = Intent(this, CallActionReceiver::class.java).apply {
            action = "ACTION_DECLINE_CALL"
            putExtra("CALL_ID", callId)
        }
        val declinePendingIntent = PendingIntent.getBroadcast(
            this, 2, declineIntent, 
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_call)
            .setContentTitle("Incoming Call")
            .setContentText("$callerName is calling...")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setFullScreenIntent(pendingIntent, true)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setOngoing(true)
            .addAction(R.drawable.ic_call, "Answer", answerPendingIntent)
            .addAction(R.drawable.ic_call_end, "Decline", declinePendingIntent)
            .build()
        
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
    }
    
    private fun dismissCallNotification() {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(NOTIFICATION_ID)
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Call Notifications"
            val descriptionText = "Notifications for incoming calls"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    private fun sendRegistrationToServer(token: String) {
        // TODO: Implement this method to send token to your backend
        // This would typically involve making an API call to register the device token
        Log.d(TAG, "Token should be sent to server: $token")
    }
} 