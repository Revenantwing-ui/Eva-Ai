package com.aiautomation.assistant.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.aiautomation.assistant.AutomationApp
import com.aiautomation.assistant.data.ActionSequence
import com.aiautomation.assistant.ml.PatternRecognitionManager
import kotlinx.coroutines.*

class MLProcessingService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var patternRecognition: PatternRecognitionManager
    
    private var processingMode: ProcessingMode = ProcessingMode.IDLE
    private var automationJob: Job? = null
    private val recordedActions = mutableListOf<ActionSequence>()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val app = application as AutomationApp
        patternRecognition = app.patternRecognition
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val mode = intent?.getStringExtra("mode")
        when (mode) {
            "learning" -> startLearningMode()
            "automation" -> startAutomationMode()
            "idle" -> stopProcessing()
        }
        return START_STICKY
    }

    private fun startLearningMode() {
        processingMode = ProcessingMode.LEARNING
        recordedActions.clear()
    }

    private fun startAutomationMode() {
        processingMode = ProcessingMode.AUTOMATION
        automationJob = serviceScope.launch {
            executeAutomation()
        }
    }

    private fun stopProcessing() {
        processingMode = ProcessingMode.IDLE
        automationJob?.cancel()
    }

    private suspend fun executeAutomation() {
        val accessibilityService = AutomationAccessibilityService.instance
        
        // Ensure service is connected before trying to run
        if (accessibilityService == null || !AutomationAccessibilityService.isServiceConnected) {
            processingMode = ProcessingMode.IDLE
            return
        }

        while (processingMode == ProcessingMode.AUTOMATION) {
            try {
                // 1. Get Screen Image (Visuals)
                val screenshot = ScreenCaptureService.latestScreenshot
                
                // 2. Get Screen Context (Text & Buttons)
                val uiContext = accessibilityService.captureScreenContext()
                
                if (screenshot != null) {
                    // 3. DECIDE: Pass both Visuals and Text to the Brain
                    // THIS IS THE CRITICAL LINE THAT WAS MISSING
                    val nextAction = patternRecognition.analyzeScreen(screenshot, uiContext)
                    
                    // 4. ACT: Perform the click/action
                    nextAction?.let { action ->
                        executeAction(action, accessibilityService)
                        delay(1200) // Wait for game animation
                    }
                }
                delay(500) // Scan rate
                
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
        when (action.actionType) {
            "CLICK" -> {
                if (action.x != null && action.y != null) {
                    accessibilityService.performClick(action.x, action.y)
                }
            }
            "SWIPE" -> {
                if (action.x != null && action.y != null && action.endX != null && action.endY != null) {
                    accessibilityService.performSwipe(action.x, action.y, action.endX, action.endY)
                }
            }
            // Add other actions as needed
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopProcessing()
        serviceScope.cancel()
    }

    enum class ProcessingMode { IDLE, LEARNING, AUTOMATION }
}
