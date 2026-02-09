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
        if (recordedActions.isNotEmpty()) {
            saveLearnedSequence()
        }
    }

    private suspend fun executeAutomation() {
        val accessibilityService = AutomationAccessibilityService.instance
        
        if (accessibilityService == null || !AutomationAccessibilityService.isServiceConnected) {
            processingMode = ProcessingMode.IDLE
            return
        }

        while (processingMode == ProcessingMode.AUTOMATION) {
            try {
                // 1. Get Screen Image
                val screenshot = ScreenCaptureService.latestScreenshot
                
                // 2. Get Screen Context
                val uiContext = accessibilityService.captureScreenContext()
                
                if (screenshot != null) {
                    // 3. Analyze
                    val nextAction = patternRecognition.analyzeScreen(screenshot, uiContext)
                    
                    // 4. Execute
                    nextAction?.let { action ->
                        executeAction(action, accessibilityService)
                        delay(1000) 
                    }
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
        when (action.actionType) {
            "CLICK" -> {
                action.x?.let { x ->
                    action.y?.let { y ->
                        accessibilityService.performClick(x, y)
                    }
                }
            }
            "SWIPE" -> {
                if (action.x != null && action.y != null && action.endX != null && action.endY != null) {
                    accessibilityService.performSwipe(action.x, action.y, action.endX, action.endY)
                }
            }
            "LONG_PRESS" -> {
                action.x?.let { x ->
                    action.y?.let { y ->
                        accessibilityService.performLongPress(x, y)
                    }
                }
            }
            "SCROLL" -> {
                val direction = when (action.direction) {
                    "UP" -> AutomationAccessibilityService.ScrollDirection.UP
                    "LEFT" -> AutomationAccessibilityService.ScrollDirection.LEFT
                    "RIGHT" -> AutomationAccessibilityService.ScrollDirection.RIGHT
                    else -> AutomationAccessibilityService.ScrollDirection.DOWN
                }
                accessibilityService.performScroll(direction)
            }
            "TYPE_TEXT" -> {
                action.text?.let { text ->
                    accessibilityService.typeText(text)
                }
            }
            "WAIT" -> {
                action.duration?.let { duration ->
                    delay(duration)
                }
            }
        }
    }

    private fun saveLearnedSequence() {
        serviceScope.launch {
            try {
                val app = application as AutomationApp
                val dao = app.database.actionSequenceDao()
                recordedActions.forEach { action ->
                    dao.insertActionSequence(action)
                }
                patternRecognition.updateModel(recordedActions)
                recordedActions.clear()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopProcessing()
        serviceScope.cancel()
    }

    enum class ProcessingMode {
        IDLE, LEARNING, AUTOMATION
    }
}
