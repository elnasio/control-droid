package com.mories.control_droid.features.target

import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import com.mories.control_droid.core.control.ScreenCaptureManager

class ScreenPermissionActivity : ComponentActivity() {

    companion object {
        const val REQUEST_CODE_CAPTURE = 9001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val projectionManager =
            getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val intent = projectionManager.createScreenCaptureIntent()
        startActivityForResult(intent, REQUEST_CODE_CAPTURE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data) // ✅ tambahkan ini

        if (requestCode == REQUEST_CODE_CAPTURE && resultCode == RESULT_OK && data != null) {
            ScreenCaptureManager.setProjection(this, resultCode, data)
        }
        finish()
    }
}