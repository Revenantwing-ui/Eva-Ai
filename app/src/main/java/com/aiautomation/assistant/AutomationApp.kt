package com.aiautomation.assistant

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.aiautomation.assistant.data.AppDatabase
import com.aiautomation.assistant.ml.PatternRecognitionManager

class AutomationApp : Application() {

    companion object {
        const val CHANNEL_ID = "automation_service_channel"
        const val CHANNEL_NAME = "Automation Service"
        lateinit var instance: AutomationApp
            private set
    }

    lateinit var database: AppDatabase
        private set

    lateinit var patternRecognition: PatternRecognitionManager
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        // Initialize database
        database = AppDatabase.getDatabase(this)

        // Initialize ML pattern recognition
        patternRecognition = PatternRecognitionManager(this)

        // Create notification channel for foreground services
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Channel for automation service notifications"
                setShowBadge(false)
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
}
