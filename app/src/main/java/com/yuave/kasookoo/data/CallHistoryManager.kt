package com.yuave.kasookoo.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.yuave.kasookoo.core.CallType
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.text.SimpleDateFormat
import java.util.*

data class CallRecord(
    val id: String = UUID.randomUUID().toString(),
    val callType: CallType,
    val contactName: String,
    val startTime: Long,
    val endTime: Long? = null,
    val duration: Long = 0, // in seconds
    val status: CallStatus = CallStatus.COMPLETED
)

enum class CallStatus {
    COMPLETED,
    MISSED,
    CANCELLED,
    FAILED
}

class CallHistoryManager(private val context: Context) {

    companion object {
        private const val TAG = "CallHistoryManager"
        private const val PREFS_NAME = "call_history"
        private const val KEY_CALL_HISTORY = "call_records"
        private const val MAX_HISTORY_SIZE = 100
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()

    fun addCallRecord(record: CallRecord) {
        try {
            val currentHistory = getCallHistory().toMutableList()
            currentHistory.add(0, record) // Add to beginning

            // Keep only last MAX_HISTORY_SIZE records
            if (currentHistory.size > MAX_HISTORY_SIZE) {
                currentHistory.removeAt(currentHistory.size - 1)
            }

            saveCallHistory(currentHistory)
            Log.d(TAG, "Added call record: ${record.id}")
        } catch (e: Exception) {
            Log.e(TAG, "Error adding call record", e)
        }
    }

    fun getCallHistory(): List<CallRecord> {
        return try {
            val json = prefs.getString(KEY_CALL_HISTORY, null) ?: return emptyList()
            val type = object : TypeToken<List<CallRecord>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting call history", e)
            emptyList()
        }
    }

    fun clearHistory() {
        try {
            prefs.edit().remove(KEY_CALL_HISTORY).apply()
            Log.d(TAG, "Call history cleared")
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing call history", e)
        }
    }

    private fun saveCallHistory(history: List<CallRecord>) {
        try {
            val json = gson.toJson(history)
            prefs.edit().putString(KEY_CALL_HISTORY, json).apply()
            Log.d(TAG, "Saved ${history.size} call records")
        } catch (e: Exception) {
            Log.e(TAG, "Error saving call history", e)
        }
    }

    fun formatCallDuration(durationSeconds: Long): String {
        return try {
            val hours = durationSeconds / 3600
            val minutes = (durationSeconds % 3600) / 60
            val seconds = durationSeconds % 60

            if (hours > 0) {
                String.format("%02d:%02d:%02d", hours, minutes, seconds)
            } else {
                String.format("%02d:%02d", minutes, seconds)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error formatting duration", e)
            "00:00"
        }
    }

    fun formatCallTime(timestamp: Long): String {
        return try {
            val sdf = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
            sdf.format(Date(timestamp))
        } catch (e: Exception) {
            Log.e(TAG, "Error formatting call time", e)
            "Unknown"
        }
    }
} 