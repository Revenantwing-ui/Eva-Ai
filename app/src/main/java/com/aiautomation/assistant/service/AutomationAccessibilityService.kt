package com.aiautomation.assistant.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.*
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class AutomationAccessibilityService : AccessibilityService() {

    companion object {
        var instance: AutomationAccessibilityService? = null
            private set
        
        // RELIABLE flag to check if service is running
        var isServiceConnected = false
            private set
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        isServiceConnected = true
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        isServiceConnected = false
        instance = null
        return super.onUnbind(intent)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Optional: Listen to events if needed for triggers
    }

    override fun onInterrupt() {
        isServiceConnected = false
    }

    /**
     * Captures the current screen UI structure for the AI
     * Returns a list of text and clickable elements
     */
    fun captureScreenContext(): List<UIContextNode> {
        val root = rootInActiveWindow ?: return emptyList()
        val nodes = mutableListOf<UIContextNode>()
        
        fun traverse(node: AccessibilityNodeInfo) {
            val rect = Rect()
            node.getBoundsInScreen(rect)
            
            // Only add interesting nodes (have text or are clickable)
            if (node.text != null || node.isClickable || node.isEditable) {
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
        
        try {
            traverse(root)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        return nodes
    }

    /**
     * Perform a click at specific coordinates
     */
    suspend fun performClick(x: Float, y: Float): Boolean = suspendCoroutine { continuation ->
        val path = Path().apply {
            moveTo(x, y)
        }

        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 100))
            .build()

        dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                continuation.resume(true)
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                continuation.resume(false)
            }
        }, null)
    }

    /**
     * Perform a swipe gesture
     */
    suspend fun performSwipe(
        startX: Float, 
        startY: Float, 
        endX: Float, 
        endY: Float, 
        duration: Long = 300
    ): Boolean = suspendCoroutine { continuation ->
        val path = Path().apply {
            moveTo(startX, startY)
            lineTo(endX, endY)
        }

        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, duration))
            .build()

        dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                continuation.resume(true)
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                continuation.resume(false)
            }
        }, null)
    }

    /**
     * Perform a long press
     */
    suspend fun performLongPress(x: Float, y: Float, duration: Long = 1000): Boolean = 
        suspendCoroutine { continuation ->
        val path = Path().apply {
            moveTo(x, y)
        }

        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, duration))
            .build()

        dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                continuation.resume(true)
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                continuation.resume(false)
            }
        }, null)
    }

    /**
     * Perform a scroll gesture
     */
    suspend fun performScroll(direction: ScrollDirection, amount: Float = 500f): Boolean {
        val displayMetrics = resources.displayMetrics
        val centerX = displayMetrics.widthPixels / 2f
        val centerY = displayMetrics.heightPixels / 2f

        return when (direction) {
            ScrollDirection.UP -> performSwipe(centerX, centerY, centerX, centerY - amount)
            ScrollDirection.DOWN -> performSwipe(centerX, centerY, centerX, centerY + amount)
            ScrollDirection.LEFT -> performSwipe(centerX, centerY, centerX - amount, centerY)
            ScrollDirection.RIGHT -> performSwipe(centerX, centerY, centerX + amount, centerY)
        }
    }

    /**
     * Type text (requires input field to be focused)
     */
    fun typeText(text: String): Boolean {
        val rootNode = rootInActiveWindow ?: return false
        
        // Find focused node
        val focusedNode = rootNode.findFocus(AccessibilityEvent.TYPE_VIEW_FOCUSED)
        
        return if (focusedNode != null && focusedNode.isEditable) {
            val arguments = android.os.Bundle().apply {
                putCharSequence(
                    android.view.accessibility.AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                    text
                )
            }
            focusedNode.performAction(
                android.view.accessibility.AccessibilityNodeInfo.ACTION_SET_TEXT,
                arguments
            )
        } else {
            false
        }
    }

    /**
     * Press back button
     */
    fun pressBack(): Boolean {
        return performGlobalAction(GLOBAL_ACTION_BACK)
    }

    /**
     * Press home button
     */
    fun pressHome(): Boolean {
        return performGlobalAction(GLOBAL_ACTION_HOME)
    }

    /**
     * Press recent apps button
     */
    fun pressRecents(): Boolean {
        return performGlobalAction(GLOBAL_ACTION_RECENTS)
    }

    /**
     * Open notifications
     */
    fun openNotifications(): Boolean {
        return performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        serviceScope.cancel()
    }

    enum class ScrollDirection {
        UP, DOWN, LEFT, RIGHT
    }
}

/**
 * Data class to hold context info for the AI
 */
data class UIContextNode(
    val text: String,
    val className: String,
    val isClickable: Boolean,
    val isEditable: Boolean,
    val bounds: Rect
)
