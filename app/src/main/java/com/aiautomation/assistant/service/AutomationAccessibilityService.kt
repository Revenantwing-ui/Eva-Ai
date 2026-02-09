package com.aiautomation.assistant.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.util.Log
import kotlinx.coroutines.*
import java.util.Random
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class AutomationAccessibilityService : AccessibilityService() {

    companion object {
        var instance: AutomationAccessibilityService? = null
            private set
        var isServiceConnected = false
            private set
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val random = Random()

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        isServiceConnected = true
        Log.d("AUTO_SERVICE", "Service Connected Successfully")
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        isServiceConnected = false
        instance = null
        return super.onUnbind(intent)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() { isServiceConnected = false }

    fun captureScreenContext(): List<UIContextNode> {
        val root = rootInActiveWindow ?: return emptyList()
        val nodes = mutableListOf<UIContextNode>()
        fun traverse(node: AccessibilityNodeInfo) {
            val rect = Rect()
            node.getBoundsInScreen(rect)
            if (node.text != null || node.isClickable) {
                nodes.add(UIContextNode(
                    text = node.text?.toString() ?: "",
                    className = node.className?.toString() ?: "",
                    isClickable = node.isClickable,
                    isEditable = node.isEditable,
                    bounds = rect
                ))
            }
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { traverse(it) }
            }
        }
        try { traverse(root) } catch (e: Exception) { e.printStackTrace() }
        return nodes
    }

    /**
     * SIMULATE HARDWARE PRESS:
     * Adds jitter, realistic duration, and micro-movement to mimic a human finger.
     * This bypasses bot detection and "dead click" issues.
     */
    suspend fun simulateNaturalTap(x: Float, y: Float): Boolean = suspendCoroutine { continuation ->
        if (x < 0 || y < 0) {
            continuation.resume(false)
            return@suspendCoroutine
        }

        // 1. ADD HUMAN JITTER (+/- 5 pixels)
        // Real fingers never hit the exact same pixel twice.
        val jitterX = x + (random.nextInt(10) - 5)
        val jitterY = y + (random.nextInt(10) - 5)

        // 2. CREATE MICRO-MOVEMENT PATH
        // A "hardware" tap is rarely a single point; it's a tiny streak.
        val path = Path().apply {
            moveTo(jitterX, jitterY)
            // Move 2 pixels to simulate the finger pad rolling
            lineTo(jitterX + 2, jitterY + 2)
        }
        
        // 3. REALISTIC DURATION (80ms - 130ms)
        // Instant (0ms) clicks are ignored by games.
        val duration = (80 + random.nextInt(50)).toLong()

        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, duration))
            .build()

        val dispatched = dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                Log.d("AUTO_SERVICE", "Hardware Tap: $jitterX, $jitterY ($duration ms)")
                continuation.resume(true)
            }
            override fun onCancelled(gestureDescription: GestureDescription?) {
                Log.d("AUTO_SERVICE", "Tap Cancelled")
                continuation.resume(false)
            }
        }, null)

        if (!dispatched) {
            Log.e("AUTO_SERVICE", "System rejected gesture")
            continuation.resume(false)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        serviceScope.cancel()
    }
}

data class UIContextNode(
    val text: String,
    val className: String,
    val isClickable: Boolean,
    val isEditable: Boolean,
    val bounds: Rect
)
