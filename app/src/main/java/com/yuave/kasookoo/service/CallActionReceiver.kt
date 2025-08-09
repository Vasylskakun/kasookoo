package com.yuave.kasookoo.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.yuave.kasookoo.core.CallType
import com.yuave.kasookoo.core.LiveKitManager
import com.yuave.kasookoo.ui.RingingActivity

class CallActionReceiver : BroadcastReceiver() {
    
    companion object {
        private const val TAG = "CallActionReceiver"
    }
    
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        Log.d(TAG, "📞 Call action received: $action")
        
        when (action) {
            "ACCEPT_CALL" -> handleAcceptCall(context, intent)
            "DECLINE_CALL" -> handleDeclineCall(context, intent)
            else -> {
                Log.w(TAG, "⚠️ Unknown action: $action")
            }
        }
    }
    
    private fun handleAcceptCall(context: Context, intent: Intent) {
        val callType = intent.getStringExtra("call_type") ?: "unknown"
        val callerName = intent.getStringExtra("caller_name") ?: "Unknown"
        val roomName = intent.getStringExtra("room_name") ?: ""
        val participantIdentity = intent.getStringExtra("participant_identity") ?: ""
        
        Log.d(TAG, "✅ Accepting call:")
        Log.d(TAG, "   - Call Type: $callType")
        Log.d(TAG, "   - Caller Name: $callerName")
        Log.d(TAG, "   - Room Name: $roomName")
        Log.d(TAG, "   - Participant Identity: $participantIdentity")
        
        // Clear the notification
        clearNotification(context)
        
        // Navigate to ringing activity with call details
        val ringingIntent = Intent(context, RingingActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("call_type", callType)
            putExtra("caller_name", callerName)
            putExtra("room_name", roomName)
            putExtra("participant_identity", participantIdentity)
            putExtra("is_incoming_call", true)
            putExtra("auto_accept", true) // Auto-accept the call
        }
        
        context.startActivity(ringingIntent)
        
        Log.d(TAG, "✅ Call accepted, navigating to ringing activity")
    }
    
    private fun handleDeclineCall(context: Context, intent: Intent) {
        val callType = intent.getStringExtra("call_type") ?: "unknown"
        val roomName = intent.getStringExtra("room_name") ?: ""
        
        Log.d(TAG, "❌ Declining call:")
        Log.d(TAG, "   - Call Type: $callType")
        Log.d(TAG, "   - Room Name: $roomName")
        
        // Clear the notification
        clearNotification(context)
        
        // TODO: Send decline signal to backend
        // For now, just log the decline
        Log.d(TAG, "📤 Call declined - should send signal to backend")
        
        // Navigate back to main activity
        val mainIntent = Intent(context, com.yuave.kasookoo.ui.MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        context.startActivity(mainIntent)
        
        Log.d(TAG, "✅ Call declined, navigating to main activity")
    }
    
    private fun clearNotification(context: Context) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        notificationManager.cancel(1001) // INCOMING_CALL_NOTIFICATION_ID
        Log.d(TAG, "🧹 Notification cleared")
    }
} 