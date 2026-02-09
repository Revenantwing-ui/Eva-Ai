package com.aiautomation.assistant.service

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.aiautomation.assistant.AutomationApp
import com.aiautomation.assistant.MainActivity
import com.aiautomation.assistant.R
import com.aiautomation.assistant.databinding.FloatingWidgetBinding
// Import the service to access the static flag
import com.aiautomation.assistant.service.AutomationAccessibilityService 
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class FloatingWidgetService : Service() {

    companion object {
        var isRunning = false
            private set
        
        const val ACTION_STOP_SERVICE = "com.aiautomation.assistant.STOP_SERVICE"
    }

    private lateinit var windowManager: WindowManager
    private lateinit var floatingView: View
    private lateinit var binding: FloatingWidgetBinding
    private var screenCaptureService: ScreenCaptureService? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var isLearningMode = false
    private var isAutoMode = false

    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        
        // Inflate the floating widget layout
        binding = FloatingWidgetBinding.inflate(LayoutInflater.from(this))
        floatingView = binding.root

        setupFloatingWidget()
        setupClickListeners()
        
        // Start as foreground service
        startForeground(1, createNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (
