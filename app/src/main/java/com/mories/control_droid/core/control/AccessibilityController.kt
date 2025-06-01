package com.mories.control_droid.core.control

import android.accessibilityservice.AccessibilityService
import android.os.Build
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.mories.control_droid.core.model.DeviceAction

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