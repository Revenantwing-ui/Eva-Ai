package com.aiautomation.assistant.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import com.aiautomation.assistant.service.FloatingWidgetService
import com.aiautomation.assistant.util.PermissionHelper

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            // Check if user has enabled auto-start in preferences
            val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            val autoStartEnabled = prefs.getBoolean("auto_start_on_boot", false)
            
            if (!autoStartEnabled) return
            
            // Check permissions
            if (!Settings.canDrawOverlays(context)) return
            if (!PermissionHelper.isAccessibilityServiceEnabled(context)) return
            
            // Start the floating widget service
            // Note: This would require the screen capture permission which is tricky on boot
            // In practice, the service would start in a limited mode and wait for user interaction
            
            // For now, we'll just show a notification that the app is ready
            // The user will need to manually start the service from the app
        }
    }
}
