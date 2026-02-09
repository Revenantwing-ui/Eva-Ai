package com.aiautomation.assistant

import android.Manifest
import android.content.Context
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
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.aiautomation.assistant.databinding.ActivityMainBinding
import com.aiautomation.assistant.service.FloatingWidgetService
import com.aiautomation.assistant.util.PermissionHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var mediaProjectionManager: MediaProjectionManager? = null

    // --- LAUNCHERS ---

    // 1. Model File Picker (The "Settings" Feature)
    private val modelPickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { importModelFile(it) }
    }

    // 2. Screen Capture Permission
    private val screenCaptureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            result.data?.let { data ->
                val intent = Intent(this, FloatingWidgetService::class.java).apply {
                    putExtra("resultCode", result.resultCode)
                    putExtra("data", data)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(intent)
                } else {
                    startService(intent)
                }
                Toast.makeText(this, "Automation Started!", Toast.LENGTH_LONG).show()
                finish()
            }
        } else {
            Toast.makeText(this, "Screen capture permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    // 3. Overlay Permission
    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (Settings.canDrawOverlays(this)) {
            requestScreenCapturePermission()
        }
    }

    // 4. Notification Permission
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ ->
        checkOverlayPermission()
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
            // Start Button
            btnStartAutomation.setOnClickListener {
                startAutomation()
            }

            // Accessibility Button
            btnAccessibilitySettings.setOnClickListener {
                openAccessibilitySettings()
            }

            // View Patterns (Toast only for now as it's dev-only)
            btnViewPatterns.setOnClickListener {
                Toast.makeText(this@MainActivity, "Logging patterns to internal DB", Toast.LENGTH_SHORT).show()
            }

            // SETTINGS BUTTON -> Opens Model Picker
            btnSettings.setOnClickListener {
                showModelPickerDialog()
            }
        }
        updateServiceStatus()
    }

    private fun showModelPickerDialog() {
        val prefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val currentModel = prefs.getString("model_path", "Default (midas_model.bin)")

        AlertDialog.Builder(this)
            .setTitle("Midas Brain Configuration")
            .setMessage("Current Brain:\n$currentModel\n\nTo change the brain, select a compatible .bin file (e.g., gemma-2b-it-gpu-int4.bin).")
            .setPositiveButton("Select .bin File") { _, _ ->
                // Launch file picker for any file type (bin often has no mime type)
                modelPickerLauncher.launch(arrayOf("*/*"))
            }
            .setNeutralButton("Reset to Default") { _, _ ->
                prefs.edit().remove("model_path").remove("use_custom_model").apply()
                updateServiceStatus()
                Toast.makeText(this, "Reset to default model", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun importModelFile(uri: Uri) {
        val progressDialog = AlertDialog.Builder(this)
            .setTitle("Importing Brain...")
            .setMessage("Optimizing model for 4GB RAM usage.\nPlease wait...")
            .setCancelable(false)
            .create()
        progressDialog.show()

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Determine file name
                var fileName = "custom_model.bin"
                contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        if (nameIndex != -1) fileName = cursor.getString(nameIndex)
                    }
                }

                // Stream copy to internal storage
                val inputStream = contentResolver.openInputStream(uri)
                val destFile = File(filesDir, fileName)
                
                inputStream?.use { input ->
                    FileOutputStream(destFile).use { output ->
                        input.copyTo(output)
                    }
                }

                // Save Preference
                getSharedPreferences("app_prefs", Context.MODE_PRIVATE).edit()
                    .putString("model_path", fileName)
                    .putBoolean("use_custom_model", true)
                    .apply()

                withContext(Dispatchers.Main) {
                    progressDialog.dismiss()
                    updateServiceStatus()
                    Toast.makeText(this@MainActivity, "Brain Imported: $fileName", Toast.LENGTH_LONG).show()
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    progressDialog.dismiss()
                    Toast.makeText(this@MainActivity, "Import Failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                return
            }
        }
        checkOverlayPermission()
    }

    private fun checkOverlayPermission() {
        if (!Settings.canDrawOverlays(this)) {
            AlertDialog.Builder(this)
                .setTitle("Permission Required")
                .setMessage("Midas needs 'Display Over Other Apps' to function.")
                .setPositiveButton("Grant") { _, _ ->
                    val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
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
        if (!PermissionHelper.isAccessibilityServiceEnabled(this)) {
            Toast.makeText(this, "Enable Accessibility Service first", Toast.LENGTH_LONG).show()
            openAccessibilitySettings()
            return
        }
        if (!Settings.canDrawOverlays(this)) {
            checkOverlayPermission()
            return
        }
        requestScreenCapturePermission()
    }

    private fun openAccessibilitySettings() {
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    private fun updateServiceStatus() {
        val isRunning = FloatingWidgetService.isRunning
        val isAccessibilityEnabled = PermissionHelper.isAccessibilityServiceEnabled(this)

        binding.apply {
            tvServiceStatus.text = if (isRunning) "Status: Running" else "Status: Stopped"
            tvAccessibilityStatus.text = if (isAccessibilityEnabled) "Accessibility: Active" else "Accessibility: Disabled"
            
            val prefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            val modelName = prefs.getString("model_path", "Default (midas_model.bin)")
            tvAppSubtitle.text = "Active Brain: $modelName"
        }
    }

    override fun onResume() {
        super.onResume()
        updateServiceStatus()
    }
}
