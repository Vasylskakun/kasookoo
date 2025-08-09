package com.yuave.kasookoo.core

import android.util.Log
import kotlinx.coroutines.flow.StateFlow

/**
 * Utility class to monitor LiveKit room connections and participant status
 */
class RoomMonitor(private val liveKitManager: LiveKitManager) {
    
    companion object {
        private const val TAG = "RoomMonitor"
    }
    
    /**
     * Check if customer and driver are connected to the same room
     */
    fun isCustomerDriverConnected(): Boolean {
        return liveKitManager.hasCustomerAndDriver()
    }
    
    /**
     * Get current room status
     */
    fun getRoomStatus(): RoomConnectionStatus {
        return liveKitManager.roomConnectionStatus.value
    }
    
    /**
     * Get participant count
     */
    fun getParticipantCount(): Int {
        return liveKitManager.participantCount.value
    }
    
    /**
     * Get detailed participant information
     */
    fun getParticipantDetails(): String {
        return liveKitManager.getParticipantDetails()
    }
    
    /**
     * Check if a call is currently active
     */
    fun isCallActive(): Boolean {
        return liveKitManager.isCustomerDriverCallActive()
    }
    
    /**
     * Get formatted room status for display
     */
    fun getFormattedStatus(): String {
        val status = getRoomStatus()
        val participantCount = getParticipantCount()
        val participants = getParticipantDetails()
        
        return when (status) {
            RoomConnectionStatus.IDLE -> "Not connected to room"
            RoomConnectionStatus.CONNECTING -> "Connecting to room..."
            RoomConnectionStatus.CONNECTED -> "Connected to room (${participantCount} participants)"
            RoomConnectionStatus.MULTIPLE_PARTICIPANTS -> "Multiple participants: $participants"
            RoomConnectionStatus.CALL_ACTIVE -> "Call active: $participants"
            RoomConnectionStatus.DISCONNECTED -> "Disconnected from room"
            RoomConnectionStatus.ERROR -> "Connection error"
        }
    }
    
    /**
     * Log current room status for debugging
     */
    fun logRoomStatus() {
        Log.d(TAG, "=== Room Status ===")
        Log.d(TAG, "Status: ${getRoomStatus()}")
        Log.d(TAG, "Participants: ${getParticipantCount()}")
        Log.d(TAG, "Details: ${getParticipantDetails()}")
        Log.d(TAG, "Customer+Driver: ${isCustomerDriverConnected()}")
        Log.d(TAG, "Call Active: ${isCallActive()}")
        Log.d(TAG, "==================")
    }
    
    /**
     * Get StateFlow for observing room status changes
     */
    fun observeRoomStatus(): StateFlow<RoomConnectionStatus> {
        return liveKitManager.roomConnectionStatus
    }
    
    /**
     * Get StateFlow for observing participant count changes
     */
    fun observeParticipantCount(): StateFlow<Int> {
        return liveKitManager.participantCount
    }
    
    /**
     * Get StateFlow for observing participant list changes
     */
    fun observeParticipants(): StateFlow<List<ParticipantInfo>> {
        return liveKitManager.participants
    }
} 