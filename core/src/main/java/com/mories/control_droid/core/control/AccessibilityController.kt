package com.mories.control_droid.core.control

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Build
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.mories.control_droid.core.model.DeviceAction
import com.mories.control_droid.core.model.GestureRequest

class AccessibilityController : AccessibilityService() {

    companion object {
        private var instance: AccessibilityController? = null

        fun performAction(action: DeviceAction) {
            val service = instance
            if (service == null) {
                Log.e("Accessibility", "Service not connected")
                return
            }

            Log.d("Accessibility", "Trying to perform: ${action.name}")

            val success = when (action) {
                DeviceAction.GLOBAL_BACK -> service.performGlobalAction(GLOBAL_ACTION_BACK)
                DeviceAction.GLOBAL_HOME -> service.performGlobalAction(GLOBAL_ACTION_HOME)
                DeviceAction.GLOBAL_RECENT -> service.performGlobalAction(GLOBAL_ACTION_RECENTS)

                // CATATAN:
                // GLOBAL_ACTION_TAKE_SCREENSHOT hanya bekerja di Android 11+ (API 30+)
                DeviceAction.CAPTURE_SCREEN -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        service.performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT)
                    } else {
                        Log.w("Accessibility", "Screenshot not supported on this Android version")
                        false
                    }
                }

                else -> false
            }

            Log.d("Accessibility", "Action result: $success")
        }

        fun performGesture(request: GestureRequest) {
            val service = instance ?: run {
                Log.e("Accessibility", "Service not connected")
                return
            }
            val metrics = service.resources.displayMetrics
            val startX = request.startX.coerceIn(0f, 1f) * metrics.widthPixels
            val startY = request.startY.coerceIn(0f, 1f) * metrics.heightPixels
            val endX = request.endX.coerceIn(0f, 1f) * metrics.widthPixels
            val endY = request.endY.coerceIn(0f, 1f) * metrics.heightPixels
            val path = Path().apply {
                moveTo(startX, startY)
                lineTo(endX, endY)
            }
            val gesture = GestureDescription.Builder()
                .addStroke(
                    GestureDescription.StrokeDescription(
                        path,
                        0,
                        request.durationMs.coerceIn(1, 5_000)
                    )
                )
                .build()
            service.dispatchGesture(gesture, null, null)
        }

        fun pasteClipboard(): Boolean {
            val service = instance ?: return false
            val focused = service.rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
                ?: return false
            return focused.performAction(AccessibilityNodeInfo.ACTION_PASTE)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }
}
