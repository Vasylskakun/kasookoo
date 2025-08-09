package com.yuave.kasookoo

import android.app.Application
import com.yuave.kasookoo.core.LiveKitManager

class CallApplication : Application() {
    
    lateinit var liveKitManager: LiveKitManager
    
    override fun onCreate() {
        super.onCreate()
        liveKitManager = LiveKitManager(this)
    }
} 