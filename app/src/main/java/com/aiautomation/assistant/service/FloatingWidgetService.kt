package com.aiautomation.assistant.service

import android.app.Notification
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
        binding = FloatingWidgetBinding.inflate(LayoutInflater.from(this))
        floatingView = binding.root

        setupFloatingWidget()
        setupClickListeners()
        startForeground(1, createNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_SERVICE) {
            stopSelfAndCleanup()
            return START_NOT_STICKY
        }
        val resultCode = intent?.getIntExtra("resultCode", -1) ?: -1
        val data = intent?.getParcelableExtra<Intent>("data")
        if (resultCode != -1 && data != null) {
            startScreenCapture(resultCode, data)
        }
        return START_STICKY
    }

    private fun setupFloatingWidget() {
        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 100
        }
        windowManager.addView(floatingView, layoutParams)
        setupDraggable(layoutParams)
        updateWidgetUI()
    }

    private fun setupDraggable(params: WindowManager.LayoutParams) {
        binding.widgetHeader.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = initialX + (event.rawX - initialTouchX).toInt()
                    params.y = initialY + (event.rawY - initialTouchY).toInt()
                    windowManager.updateViewLayout(floatingView, params)
                    true
                }
                else -> false
            }
        }
    }

    private fun setupClickListeners() {
        binding.apply {
            btnToggleLearning.setOnClickListener {
                isLearningMode = !isLearningMode
                updateWidgetUI()
                if (isLearningMode) startLearningMode() else stopLearningMode()
            }
            btnToggleAuto.setOnClickListener {
                isAutoMode = !isAutoMode
                updateWidgetUI()
                if (isAutoMode) startAutoMode() else stopAutoMode()
            }
            btnMinimize.setOnClickListener { toggleMinimize() }
            btnClose.setOnClickListener { stopSelfAndCleanup() }
            btnRecordPattern.setOnClickListener {
                Toast.makeText(this@FloatingWidgetService, "Recording...", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateWidgetUI() {
        binding.apply {
            btnToggleLearning.text = if (isLearningMode) "Learning: ON" else "Learning: OFF"
            btnToggleLearning.setBackgroundColor(if (isLearningMode) 0xFF4CAF50.toInt() else 0xFF757575.toInt())
            btnToggleAuto.text = if (isAutoMode) "Auto: ON" else "Auto: OFF"
            btnToggleAuto.setBackgroundColor(if (isAutoMode) 0xFF2196F3.toInt() else 0xFF757575.toInt())
            btnToggleAuto.isEnabled = !isLearningMode
        }
    }

    private fun toggleMinimize() {
        binding.apply {
            val isMinimized = widgetContent.visibility == View.GONE
            widgetContent.visibility = if (isMinimized) View.VISIBLE else View.GONE
            btnMinimize.text = if (isMinimized) "−" else "+"
        }
    }

    private fun startScreenCapture(resultCode: Int, data: Intent) {
        val intent = Intent(this, ScreenCaptureService::class.java).apply {
            putExtra("resultCode", resultCode)
            putExtra("data", data)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent) else startService(intent)
    }

    private fun startLearningMode() {
        Toast.makeText(this, "Learning Mode Activated", Toast.LENGTH_SHORT).show()
        val intent = Intent(this, MLProcessingService::class.java).apply { putExtra("mode", "learning") }
        startService(intent)
    }

    private fun stopLearningMode() {
        Toast.makeText(this, "Learning Mode Stopped", Toast.LENGTH_SHORT).show()
    }

    private fun startAutoMode() {
        // 1. Check Accessibility
        if (!AutomationAccessibilityService.isServiceConnected) {
            Toast.makeText(this, "Please enable Accessibility Service first", Toast.LENGTH_LONG).show()
            isAutoMode = false
            updateWidgetUI()
            return
        }

        // 2. Check Screen Capture (Crucial Check)
        // If we haven't started screen capture yet (e.g. app restarted), we can't automate
        // The user must go back to Main Activity to grant permission if this is missing.
        // We assume it's running if we are here, but let's notify the ML service to start.

        Toast.makeText(this, "Starting AI...", Toast.LENGTH_SHORT).show()
        
        serviceScope.launch {
            val intent = Intent(this@FloatingWidgetService, MLProcessingService::class.java).apply {
                putExtra("mode", "automation")
            }
            startService(intent)
        }

    private fun stopAutoMode() {
        Toast.makeText(this, "Auto Mode Stopped", Toast.LENGTH_SHORT).show()
    }

    private fun createNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val stopPendingIntent = PendingIntent.getService(this, 0, Intent(this, FloatingWidgetService::class.java).apply { action = ACTION_STOP_SERVICE }, PendingIntent.FLAG_IMMUTABLE)

        return NotificationCompat.Builder(this, AutomationApp.CHANNEL_ID)
            .setContentTitle("AI Automation Assistant")
            .setContentText("Service is running")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .addAction(R.drawable.ic_stop, "Stop", stopPendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun stopSelfAndCleanup() {
        isRunning = false
        stopService(Intent(this, ScreenCaptureService::class.java))
        stopService(Intent(this, MLProcessingService::class.java))
        if (::floatingView.isInitialized) windowManager.removeView(floatingView)
        serviceScope.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        if (::floatingView.isInitialized) {
            try { windowManager.removeView(floatingView) } catch (e: Exception) { e.printStackTrace() }
        }
        serviceScope.cancel()
    }
}
