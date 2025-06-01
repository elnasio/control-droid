package com.mories.control_droid.features.controller

import android.graphics.BitmapFactory
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.mories.control_droid.core.model.PairedDevice
import com.mories.control_droid.core.networking.LiveFrameReceiver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

@Composable
fun RemotePreviewScreen(
    navController: NavController, device: PairedDevice
) {
    var bitmapData by remember { mutableStateOf<ByteArray?>(null) }

    // Remember receiver
    val receiver = remember(device.ip) { LiveFrameReceiver(device.ip) }

    // Lifecycle-safe start & stop
    LaunchedEffect(device.ip) {
        withContext(Dispatchers.IO) {
            try {
                val url = "http://${device.ip}:8080/screenshot"
                val request = Request.Builder().url(url).build()
                val response = OkHttpClient().newCall(request).execute()
                if (response.isSuccessful) {
                    response.body?.bytes()?.let {
                        bitmapData = it
                    }
                }
            } catch (e: Exception) {
                Log.e("RemotePreview", "Failed to load screenshot: ${e.message}")
            }
        }
    }
    DisposableEffect(Unit) {
        receiver.connect { newBytes ->
            bitmapData = newBytes
        }
        onDispose {
            receiver.disconnect()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(8.dp)
    ) {
        Text("Live Screen: ${device.name}")

        Spacer(modifier = Modifier.height(16.dp))

        bitmapData?.let { bytes ->
            val bmp = remember(bytes) { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = "Remote screen",
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16 / 9f)
            )
        } ?: Text("Menunggu tangkapan layar...")
    }
}