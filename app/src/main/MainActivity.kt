package com.aiautomation.assistant

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.aiautomation.assistant.databinding.ActivityMainBinding
import com.aiautomation.assistant.service.FloatingWidgetService
import com.aiautomation.assistant.util.PermissionHelper

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var mediaProjectionManager: MediaProjectionManager? = null

    // Screen capture permission launcher
    private val screenCaptureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            result.data?.let { data ->
                // Start floating widget service with screen capture permission
                val intent = Intent(this, FloatingWidgetService::class.java).apply {
                    putExtra("resultCode", result.resultCode)
                    putExtra("data", data)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(intent)
                } else {
                    startService(intent)
                }
                
                Toast.makeText(this, "Automation started! Use the floating widget to control.", Toast.LENGTH_LONG).show()
                finish()
            }
        } else {
            Toast.makeText(this, "Screen capture permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    // Overlay permission launcher
    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (Settings.canDrawOverlays(this)) {
            requestScreenCapturePermission()
        } else {
            Toast.makeText(this, "Overlay permission is required", Toast.LENGTH_SHORT).show()
        }
    }

    // Notification permission launcher (Android 13+)
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            checkOverlayPermission()
        } else {
            Toast.makeText(this, "Notification permission recommended for service", Toast.LENGTH_SHORT).show()
            checkOverlayPermission()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        mediaProjectionManager = getSystemService(MediaProjectionManager::class.java)

        setupUI()
        checkPermissions()
    }

    private fun setupUI() {
        binding.apply {
            btnStartAutomation.setOnClickListener {
                startAutomation()
            }

            btnAccessibilitySettings.setOnClickListener {
                openAccessibilitySettings()
            }

            btnViewPatterns.setOnClickListener {
                // TODO: Open pattern viewer activity
                Toast.makeText(this@MainActivity, "Pattern viewer coming soon", Toast.LENGTH_SHORT).show()
            }

            btnSettings.setOnClickListener {
                // TODO: Open settings activity
                Toast.makeText(this@MainActivity, "Settings coming soon", Toast.LENGTH_SHORT).show()
            }
        }

        updateServiceStatus()
    }

    private fun checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                return
            }
        }
        checkOverlayPermission()
    }

    private fun checkOverlayPermission() {
        if (!Settings.canDrawOverlays(this)) {
            AlertDialog.Builder(this)
                .setTitle("Overlay Permission Required")
                .setMessage("This app needs permission to display over other apps for the floating widget.")
                .setPositiveButton("Grant") { _, _ ->
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                    overlayPermissionLauncher.launch(intent)
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun requestScreenCapturePermission() {
        mediaProjectionManager?.let { manager ->
            screenCaptureLauncher.launch(manager.createScreenCaptureIntent())
        }
    }

    private fun startAutomation() {
        // Check if accessibility service is enabled
        if (!PermissionHelper.isAccessibilityServiceEnabled(this)) {
            AlertDialog.Builder(this)
                .setTitle("Accessibility Service Required")
                .setMessage("Please enable the Automation Accessibility Service to use touch automation features.")
                .setPositiveButton("Open Settings") { _, _ ->
                    openAccessibilitySettings()
                }
                .setNegativeButton("Cancel", null)
                .show()
            return
        }

        // Check overlay permission
        if (!Settings.canDrawOverlays(this)) {
            checkOverlayPermission()
            return
        }

        // Request screen capture permission
        requestScreenCapturePermission()
    }

    private fun openAccessibilitySettings() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        startActivity(intent)
    }

    private fun updateServiceStatus() {
        val isRunning = FloatingWidgetService.isRunning
        val isAccessibilityEnabled = PermissionHelper.isAccessibilityServiceEnabled(this)

        binding.apply {
            tvServiceStatus.text = if (isRunning) {
                "Status: Running"
            } else {
                "Status: Not Running"
            }

            tvAccessibilityStatus.text = if (isAccessibilityEnabled) {
                "Accessibility: Enabled ✓"
            } else {
                "Accessibility: Disabled (Required for automation)"
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updateServiceStatus()
    }
}
