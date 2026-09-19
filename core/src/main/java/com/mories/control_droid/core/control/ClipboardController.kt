package com.mories.control_droid.core.control

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context

object ClipboardController {
    fun setText(context: Context, text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("ControlDroid", text))
    }
}
