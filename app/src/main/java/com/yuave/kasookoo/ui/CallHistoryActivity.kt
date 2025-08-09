package com.yuave.kasookoo.ui

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.yuave.kasookoo.CallApplication
import com.yuave.kasookoo.R
import com.yuave.kasookoo.core.CallType
import com.yuave.kasookoo.core.LiveKitManager
import com.yuave.kasookoo.data.CallRecord
import com.yuave.kasookoo.data.CallHistoryManager
import com.yuave.kasookoo.databinding.ActivityCallHistoryBinding

class CallHistoryActivity : AppCompatActivity() {
    
    companion object {
        private const val TAG = "CallHistoryActivity"
    }
    
    private var binding: ActivityCallHistoryBinding? = null
    private lateinit var liveKitManager: LiveKitManager
    private lateinit var historyManager: CallHistoryManager
    private lateinit var adapter: CallHistoryAdapter
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "CallHistoryActivity onCreate started")
        
        try {
            // Try to inflate the layout
            Log.d(TAG, "Attempting to inflate layout...")
            try {
                binding = ActivityCallHistoryBinding.inflate(layoutInflater)
                setContentView(binding?.root)
                Log.d(TAG, "Layout inflated successfully")
            } catch (bindingError: Exception) {
                Log.e(TAG, "Binding failed, trying simple layout", bindingError)
                // Fallback to simple layout
                setContentView(R.layout.activity_call_history)
                Log.d(TAG, "Simple layout set successfully")
                // Don't try to use binding if it failed
                binding = null
            }
            
            // Initialize components
            liveKitManager = (application as CallApplication).liveKitManager
            historyManager = CallHistoryManager(this)
            Log.d(TAG, "Components initialized")
            
            setupToolbar()
            setupRecyclerView()
            loadCallHistory()
            Log.d(TAG, "CallHistoryActivity onCreate completed successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing CallHistoryActivity", e)
            Log.e(TAG, "Stack trace:", e)
            // Try to show error in a simple way
            try {
                android.widget.Toast.makeText(this, "Error: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
            } catch (toastError: Exception) {
                Log.e(TAG, "Even toast failed", toastError)
            }
        }
    }
    
    private fun setupToolbar() {
        try {
            if (binding != null) {
                // Set up clear history button
                binding?.btnClearHistory?.setOnClickListener {
                    showClearHistoryDialog()
                }
            } else {
                // Fallback for when binding is null
                Log.w(TAG, "Binding is null, toolbar setup skipped")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up toolbar", e)
        }
    }
    
    private fun setupRecyclerView() {
        try {
            adapter = CallHistoryAdapter(emptyList(), historyManager)
            if (binding != null) {
                binding?.recyclerViewHistory?.apply {
                    layoutManager = LinearLayoutManager(this@CallHistoryActivity)
                    adapter = this@CallHistoryActivity.adapter
                }
            } else {
                // Fallback for when binding is null
                Log.w(TAG, "Binding is null, RecyclerView setup skipped")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up RecyclerView", e)
        }
    }
    
    private fun loadCallHistory() {
        try {
            Log.d(TAG, "Loading call history...")
            val history = liveKitManager.getCallHistory()
            Log.d(TAG, "Loaded ${history.size} call history records")
            
            // Add sample data for testing if no history exists
            if (history.isEmpty()) {
                Log.d(TAG, "No history found, adding sample data...")
                addSampleCallHistory()
            }
            
            val updatedHistory = liveKitManager.getCallHistory()
            Log.d(TAG, "Updated history has ${updatedHistory.size} records")
            adapter.updateHistory(updatedHistory)
            
            if (updatedHistory.isEmpty()) {
                Log.d(TAG, "Still no history, showing empty state")
                try {
                    if (binding != null) {
                        binding?.tvEmptyState?.visibility = View.VISIBLE
                        binding?.recyclerViewHistory?.visibility = View.GONE
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error setting visibility", e)
                }
            } else {
                Log.d(TAG, "Showing history list")
                try {
                    if (binding != null) {
                        binding?.tvEmptyState?.visibility = View.GONE
                        binding?.recyclerViewHistory?.visibility = View.VISIBLE
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error setting visibility", e)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading call history", e)
            showError("Failed to load call history: ${e.message}")
        }
    }
    
    private fun addSampleCallHistory() {
        try {
            val sampleRecords = listOf(
                CallRecord(
                    callType = CallType.DRIVER,
                    contactName = "Driver John",
                    startTime = System.currentTimeMillis() - (2 * 60 * 60 * 1000), // 2 hours ago
                    endTime = System.currentTimeMillis() - (2 * 60 * 60 * 1000) + (5 * 60 * 1000), // 5 min call
                    duration = 5 * 60 // 5 minutes
                ),
                CallRecord(
                    callType = CallType.SUPPORT,
                    contactName = "Support Team",
                    startTime = System.currentTimeMillis() - (24 * 60 * 60 * 1000), // 1 day ago
                    endTime = System.currentTimeMillis() - (24 * 60 * 60 * 1000) + (3 * 60 * 1000), // 3 min call
                    duration = 3 * 60 // 3 minutes
                )
            )
            
            sampleRecords.forEach { record ->
                historyManager.addCallRecord(record)
            }
            
            Log.d(TAG, "Added ${sampleRecords.size} sample call records")
        } catch (e: Exception) {
            Log.e(TAG, "Error adding sample call history", e)
        }
    }
    
    private fun showClearHistoryDialog() {
        AlertDialog.Builder(this)
            .setTitle("Clear Call History")
            .setMessage("Are you sure you want to delete all call history? This action cannot be undone.")
            .setPositiveButton("Clear") { _, _ ->
                try {
                    liveKitManager.clearCallHistory()
                    loadCallHistory()
                } catch (e: Exception) {
                    Log.e(TAG, "Error clearing call history", e)
                    showError("Failed to clear call history")
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun showError(message: String) {
        Log.e(TAG, "Showing error: $message")
        try {
            // Show error message to user
            if (binding != null) {
                binding?.tvEmptyState?.visibility = View.VISIBLE
                binding?.recyclerViewHistory?.visibility = View.GONE
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error showing error message", e)
        }
        // Always show toast as fallback
        try {
            android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_LONG).show()
        } catch (toastError: Exception) {
            Log.e(TAG, "Even toast failed", toastError)
        }
    }
}

class CallHistoryAdapter(
    private var callHistory: List<CallRecord>,
    private val historyManager: CallHistoryManager
) : RecyclerView.Adapter<CallHistoryAdapter.CallHistoryViewHolder>() {
    
    fun updateHistory(newHistory: List<CallRecord>) {
        callHistory = newHistory
        notifyDataSetChanged()
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CallHistoryViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_call_history, parent, false)
        return CallHistoryViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: CallHistoryViewHolder, position: Int) {
        try {
            holder.bind(callHistory[position], historyManager)
        } catch (e: Exception) {
            Log.e("CallHistoryAdapter", "Error binding item at position $position", e)
        }
    }
    
    override fun getItemCount(): Int = callHistory.size
    
    class CallHistoryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val ivContactIcon: ImageView = itemView.findViewById(R.id.iv_contact_icon)
        private val tvContactName: TextView = itemView.findViewById(R.id.tv_contact_name)
        private val tvCallType: TextView = itemView.findViewById(R.id.tv_call_type)
        private val tvCallTime: TextView = itemView.findViewById(R.id.tv_call_time)
        private val tvCallDuration: TextView = itemView.findViewById(R.id.tv_call_duration)
        
        fun bind(record: CallRecord, historyManager: CallHistoryManager) {
            try {
                // Set contact icon (using system icon for simplicity)
                ivContactIcon.setImageResource(android.R.drawable.ic_menu_call)
                
                // Set contact info
                tvContactName.text = record.contactName
                tvCallType.text = when (record.callType) {
                    CallType.CUSTOMER -> "Customer Call"
                    CallType.DRIVER -> "Driver Call"
                    CallType.SUPPORT -> "Support Call"
                }
                
                // Format and set call time
                tvCallTime.text = historyManager.formatCallTime(record.startTime)
                
                // Format and set duration
                tvCallDuration.text = historyManager.formatCallDuration(record.duration)
            } catch (e: Exception) {
                Log.e("CallHistoryViewHolder", "Error binding record", e)
            }
        }
    }
} 