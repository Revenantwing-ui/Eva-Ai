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
    private var currentSequenceStart = 0L

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
        currentSequenceStart = System.currentTimeMillis()
        recordedActions.clear()
        
        // Start observing user actions
        observeUserActions()
    }

    private fun startAutomationMode() {
        processingMode = ProcessingMode.AUTOMATION
        
        // Start automation execution
        automationJob = serviceScope.launch {
            executeAutomation()
        }
    }

    private fun stopProcessing() {
        processingMode = ProcessingMode.IDLE
        automationJob?.cancel()
        
        if (recordedActions.isNotEmpty()) {
            // Save learned sequence
            saveLearnedSequence()
        }
    }

    private fun observeUserActions() {
        // This will be called from ScreenCaptureService when new frames arrive
        // Pattern recognition will identify UI elements and track user interactions
    }

    /**
     * Updated Automation Loop:
     * 1. Captures Screen Image (Visuals)
     * 2. Captures Accessibility Nodes (Context/Text)
     * 3. Sends both to PatternRecognitionManager
     */
    private suspend fun executeAutomation() {
        val accessibilityService = AutomationAccessibilityService.instance
        
        // Ensure accessibility service is connected and ready
        if (accessibilityService == null || !AutomationAccessibilityService.isServiceConnected) {
            processingMode = ProcessingMode.IDLE
            return
        }

        while (processingMode == ProcessingMode.AUTOMATION) {
            try {
                // 1. Get Screen Image (Visuals)
                val screenshot = ScreenCaptureService.latestScreenshot
                
                // 2. Get Screen Context (Text & Structure)
                val uiContext = accessibilityService.captureScreenContext()
                
                if (screenshot != null) {
                    // 3. Analyze using both Image and Context
                    // This uses the new analyzeScreen method we added to PatternRecognitionManager
                    val nextAction = patternRecognition.analyzeScreen(screenshot, uiContext)
                    
                    // 4. Execute Action if found
                    nextAction?.let { action ->
                        executeAction(action, accessibilityService)
                        
                        //
