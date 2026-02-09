package com.aiautomation.assistant.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.util.Log
import kotlinx.coroutines.*
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
     * Performs a tap gesture.
     * Returns TRUE if the system accepted the gesture, FALSE otherwise.
     */
    suspend fun performClick(x: Float, y: Float): Boolean = suspendCoroutine { continuation ->
        // Check for invalid coordinates (common error)
        if (x < 0 || y < 0) {
            continuation.resume(false)
            return@suspendCoroutine
        }

        val path = Path().apply { moveTo(x, y) }
        
        // Click duration 50ms is standard for taps
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 50))
            .build()

        val dispatched = dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                Log.d("AUTO_SERVICE", "Click Completed at $x, $y")
                continuation.resume(true)
            }
            override fun onCancelled(gestureDescription: GestureDescription?) {
                Log.d("AUTO_SERVICE", "Click Cancelled at $x, $y")
                continuation.resume(false)
            }
        }, null)

        if (!dispatched) {
            Log.e("AUTO_SERVICE", "System rejected gesture dispatch")
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
