package com.aiautomation.assistant.service

import android.app.Service
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.widget.Toast
import com.aiautomation.assistant.AutomationApp
import com.aiautomation.assistant.data.ActionSequence
import com.aiautomation.assistant.ml.PatternRecognitionManager
import kotlinx.coroutines.*

class MLProcessingService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var patternRecognition: PatternRecognitionManager
    private var processingMode: ProcessingMode = ProcessingMode.IDLE
    private var automationJob: Job? = null
    
    // Helper to show toasts from background thread
    private fun showToast(message: String) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val app = application as AutomationApp
        patternRecognition = app.patternRecognition
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val mode = intent?.getStringExtra("mode")
        if (mode == "automation") startAutomationMode()
        else if (mode == "idle") stopProcessing()
        return START_STICKY
    }

    private fun startAutomationMode() {
        if (processingMode == ProcessingMode.AUTOMATION) return
        processingMode = ProcessingMode.AUTOMATION
        
        showToast("Auto Mode Started: Scanning...")
        
        automationJob = serviceScope.launch {
            executeAutomation()
        }
    }

    private fun stopProcessing() {
        processingMode = ProcessingMode.IDLE
        automationJob?.cancel()
        showToast("Auto Mode Paused")
    }

    private suspend fun executeAutomation() {
        val accessibilityService = AutomationAccessibilityService.instance
        
        // CHECK 1: Is Accessibility Service Connected?
        if (accessibilityService == null || !AutomationAccessibilityService.isServiceConnected) {
            showToast("Error: Accessibility Service NOT active!")
            processingMode = ProcessingMode.IDLE
            return
        }

        while (processingMode == ProcessingMode.AUTOMATION) {
            try {
                // CHECK 2: Do we have a screenshot?
                val screenshot = ScreenCaptureService.latestScreenshot
                if (screenshot == null) {
                    showToast("Waiting for screen...") // Debug: Tell user screen is missing
                    delay(1000)
                    continue
                }

                // CHECK 3: Do we have context?
                val uiContext = accessibilityService.captureScreenContext()
                
                // ANALYZE
                val nextAction = patternRecognition.analyzeScreen(screenshot, uiContext)
                
                if (nextAction != null) {
                    // ACTION FOUND!
                    showToast(nextAction.text ?: "Clicking...") // Debug: Tell user what we found
                    executeAction(nextAction, accessibilityService)
                    
                    // Wait for animation (Dominos take ~1s to clear)
                    delay(1500) 
                } else {
                    // No match found
                    // Uncomment next line if you want to know when it sees nothing (can be spammy)
                    // showToast("Scanning... No match") 
                }
                
                delay(600) // Scan delay
                
            } catch (e: Exception) {
                e.printStackTrace()
                delay(1000)
            }
        }
    }

    private suspend fun executeAction(
        action: ActionSequence,
        accessibilityService: AutomationAccessibilityService
    ) {
        if (action.actionType == "CLICK" && action.x != null && action.y != null) {
            accessibilityService.performClick(action.x, action.y)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopProcessing()
        serviceScope.cancel()
    }

    enum class ProcessingMode { IDLE, LEARNING, AUTOMATION }
}
