import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.view.accessibility.AccessibilityEvent
import android.util.Log

class AutomationAccessibilityService : AccessibilityService() {

    companion object {
        var instance: AutomationAccessibilityService? = null
            private set
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.d("AutoService", "Accessibility Service Connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Essential for keeping the service alive and monitoring changes
    }

    override fun onInterrupt() {
        Log.d("AutoService", "Accessibility Service Interrupted")
        instance = null
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
    }

    fun performClick(x: Float, y: Float) {
        val path = Path().apply { moveTo(x, y) }
        val builder = GestureDescription.Builder()
        val stroke = GestureDescription.StrokeDescription(path, 0, 50)
        builder.addStroke(stroke)
        dispatchGesture(builder.build(), null, null)
        Log.d("AutoService", "Click executed at $x, $y")
    }
}