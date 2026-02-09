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
        showToast("Auto Mode Started")
        
        automationJob = serviceScope.launch {
            executeAutomation()
        }
    }

    private fun stopProcessing() {
        processingMode = ProcessingMode.IDLE
        automationJob?.cancel()
        showToast("Auto Mode Stopped")
    }

    private suspend fun executeAutomation() {
        // FIX: Using the property 'instance' instead of function 'getInstance()'
        val accessibilityService = AutomationAccessibilityService.instance
        
        // FIX: Using 'isServiceConnected' instead of 'isServiceEnabled()'
        if (accessibilityService == null || !AutomationAccessibilityService.isServiceConnected) {
            showToast("Error: Accessibility Service Not Connected")
            stopProcessing()
            return
        }

        while (processingMode == ProcessingMode.AUTOMATION) {
            try {
                val screenshot = ScreenCaptureService.latestScreenshot
                if (screenshot == null) {
                    delay(1000)
                    continue
                }

                val uiContext = accessibilityService.captureScreenContext()
                
                // Pass data to brain
                val nextAction = patternRecognition.analyzeScreen(screenshot, uiContext)
                
                if (nextAction != null) {
                    showToast(nextAction.text ?: "Clicking...")
                    executeAction(nextAction, accessibilityService)
                    delay(1500) // Wait for game animation
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
    ) {
        if (action.actionType == "CLICK" && action.x != null && action.y != null) {
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
