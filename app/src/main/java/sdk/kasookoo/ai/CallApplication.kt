package sdk.kasookoo.ai

import android.app.Application
import sdk.kasookoo.ai.core.LiveKitManager

class CallApplication : Application() {
    
    lateinit var liveKitManager: LiveKitManager
    
    override fun onCreate() {
        super.onCreate()
        liveKitManager = LiveKitManager(this)
    }
} 