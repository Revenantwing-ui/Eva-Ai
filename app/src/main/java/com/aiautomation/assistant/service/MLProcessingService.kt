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

    // UI Helper for debugging
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
        showToast("AI Started: Searching for matches...")
        
        automationJob = serviceScope.launch {
            executeAutomation()
        }
    }

    private fun stopProcessing() {
        processingMode = ProcessingMode.IDLE
        automationJob?.cancel()
        showToast("AI Stopped")
    }

    private suspend fun executeAutomation() {
        val accessibilityService = AutomationAccessibilityService.getInstance()
        
        // Safety Check
        if (accessibilityService == null || !AutomationAccessibilityService.isServiceEnabled()) {
            showToast("Error: Accessibility Service Not Connected")
            return
        }

        while (processingMode == ProcessingMode.AUTOMATION) {
            try {
                // 1. Capture Screen
                val screenshot = ScreenCaptureService.latestScreenshot
                if (screenshot == null) {
                    showToast("Waiting for screen...")
                    delay(1000)
                    continue
                }

                // 2. Capture Context (Text)
                val uiContext = accessibilityService.captureScreenContext()
                
                // 3. Analyze
                // FIX: Use the new analyzeScreen method instead of the old placeholder
                val nextAction = patternRecognition.analyzeScreen(screenshot, uiContext)
                
                // 4. Act
                if (nextAction != null) {
                    showToast(nextAction.text ?: "Clicking...")
                    executeAction(nextAction, accessibilityService)
                    
                    // Wait for animation (Games are slower than code)
                    delay(1200) 
                }
                
                delay(500) // Scan frequency
                
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
            // FIX: Use simulateNaturalTap for hardware simulation
            accessibilityService.simulateNaturalTap(action.x, action.y)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopProcessing()
        serviceScope.cancel()
    }

    enum class ProcessingMode { IDLE, LEARNING, AUTOMATION }
}
