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
        
        showToast("Initializing Midas...")
        
        automationJob = serviceScope.launch {
            // Load model (might take time if copying file)
            patternRecognition.loadMidasModel()
            showToast("Midas Active. Thinking...")
            
            executeAutomation()
        }
    }

    private fun stopProcessing() {
        processingMode = ProcessingMode.IDLE
        automationJob?.cancel()
        showToast("Midas Stopped")
    }

    private suspend fun executeAutomation() {
        val accessibilityService = AutomationAccessibilityService.instance
        
        if (accessibilityService == null || !AutomationAccessibilityService.isServiceConnected) {
            showToast("Error: Accessibility Service Not Connected")
            stopProcessing()
            return
        }

        while (processingMode == ProcessingMode.AUTOMATION) {
            try {
                // 1. Get Context
                val uiContext = accessibilityService.captureScreenContext()
                
                if (uiContext.isEmpty()) {
                    delay(500)
                    continue
                }

                // 2. Analyze
                // Use a dummy bitmap for now as we are text-focused
                val dummyBitmap = android.graphics.Bitmap.createBitmap(1, 1, android.graphics.Bitmap.Config.ARGB_8888)
                val nextAction = patternRecognition.analyzeScreen(dummyBitmap, uiContext)
                
                // 3. Act
                if (nextAction != null) {
                    showToast(nextAction.text ?: "Acting...")
                    
                    if (nextAction.actionType == "CLICK" && nextAction.x != null && nextAction.y != null) {
                        accessibilityService.simulateNaturalTap(nextAction.x, nextAction.y)
                        // Give app time to respond
                        delay(2500) 
                    }
                }
                
                // Thinking interval
                delay(1000) 
                
            } catch (e: Exception) {
                e.printStackTrace()
                delay(1000)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopProcessing()
        serviceScope.cancel()
    }

    enum class ProcessingMode { IDLE, LEARNING, AUTOMATION }
}
