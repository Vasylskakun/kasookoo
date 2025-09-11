package sdk.kasookoo.ai.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import sdk.kasookoo.ai.core.CallType
import sdk.kasookoo.ai.core.LiveKitManager
import sdk.kasookoo.ai.ui.RingingActivity

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
        val participantIdentity = intent.getStringExtra("participant_identity") ?: ""
        
        Log.d(TAG, "❌ Declining call:")
        Log.d(TAG, "   - Call Type: $callType")
        Log.d(TAG, "   - Room Name: $roomName")
        Log.d(TAG, "   - Participant Identity: $participantIdentity")
        
        // Clear the notification
        clearNotification(context)
        
        // Send decline signal to backend so caller is notified
        try {
            val udm = sdk.kasookoo.ai.data.UserDataManager(context)
            val repository = sdk.kasookoo.ai.data.CallRepository()
            val calledUserId = udm.getUserId() ?: ""
            val participantIdentityName = udm.getFullName() ?: "User"
            val participantIdentityType = udm.getUserType() ?: if (callType == "driver") "driver" else "customer"
            val callerUserId = intent.getStringExtra("caller_user_id") ?: ""

            if (roomName.isNotEmpty() && calledUserId.isNotEmpty()) {
                Log.d(TAG, "📤 Sending rejectIncomingCall to backend...")
                CoroutineScope(Dispatchers.IO).launch {
                    val result = repository.rejectIncomingCall(
                        roomName = roomName,
                        participantIdentity = participantIdentity,
                        participantIdentityName = participantIdentityName,
                        participantIdentityType = participantIdentityType,
                        callerUserId = callerUserId,
                        calledUserId = calledUserId
                    )
                    result.onSuccess { resp ->
                        Log.d(TAG, "✅ rejectIncomingCall sent successfully: ${resp.message}")
                    }.onFailure { e ->
                        Log.e(TAG, "❌ Failed to send rejectIncomingCall: ${e.message}")
                    }
                }
            } else {
                Log.w(TAG, "⚠️ Missing data for rejectIncomingCall (roomName/calledUserId)")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error preparing rejectIncomingCall: ${e.message}")
        }
        
        // Determine local role and navigate back to the correct main screen state
        val localIsCustomer: Boolean = try {
            val udm = sdk.kasookoo.ai.data.UserDataManager(context)
            udm.getUserType()?.equals("customer", ignoreCase = true) == true
        } catch (_: Exception) { true }

        val mainIntent = Intent(context, sdk.kasookoo.ai.ui.MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("isCustomer", localIsCustomer)
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