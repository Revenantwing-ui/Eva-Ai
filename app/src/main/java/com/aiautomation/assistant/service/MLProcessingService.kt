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
        showToast("Auto Mode: Simulating Hardware Input...")
        
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
        
        if (accessibilityService == null || !AutomationAccessibilityService.isServiceConnected) {
            showToast("ERROR: Accessibility Service Disconnected")
            delay(2000)
            return
        }

        while (processingMode == ProcessingMode.AUTOMATION) {
            try {
                val screenshot = ScreenCaptureService.latestScreenshot
                if (screenshot == null) {
                    showToast("Waiting for screen...") 
                    delay(1000)
                    continue
                }

                val uiContext = accessibilityService.captureScreenContext()
                
                // ANALYZE
                val nextAction = patternRecognition.analyzeScreen(screenshot, uiContext)
                
                // ACT
                if (nextAction != null) {
                    showToast("Tap: ${nextAction.text ?: "Target"}") 
                    
                    val success = executeAction(nextAction, accessibilityService)
                    if (!success) showToast("Tap Failed - Check Permissions")
                    
                    // Wait for human-like reaction time + animation
                    delay(1500) 
                }
                
                delay(500) 
                
            } catch (e: Exception) {
                e.printStackTrace()
                delay(1000)
            }
        }
    }

    private suspend fun executeAction(
        action: ActionSequence,
        accessibilityService: AutomationAccessibilityService
    ): Boolean {
        if (action.actionType == "CLICK" && action.x != null && action.y != null) {
            // USE THE NEW NATURAL TAP FUNCTION
            return accessibilityService.simulateNaturalTap(action.x, action.y)
        }
        return false
    }

    override fun onDestroy() {
        super.onDestroy()
        stopProcessing()
        serviceScope.cancel()
    }

    enum class ProcessingMode { IDLE, LEARNING, AUTOMATION }
}
