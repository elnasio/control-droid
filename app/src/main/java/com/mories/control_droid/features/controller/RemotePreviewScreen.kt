package com.mories.control_droid.features.controller

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.mories.control_droid.core.model.PairedDevice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

@Composable
fun RemotePreviewScreen(
    navController: NavController, device: PairedDevice
) {
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(device.ip) {
        val client = OkHttpClient()
        while (true) {
            withContext(Dispatchers.IO) {
                try {
                    val url = "http://${device.ip}:8080/screenshot"
                    val request = Request.Builder().url(url).build()
                    client.newCall(request).execute().use { response ->
                        if (response.isSuccessful) {
                            response.body?.bytes()?.let { bytes ->
                                val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                                bitmap = bmp
                            }
                        } else {
                            Log.e("RemotePreview", "HTTP error: ${response.message}")
                        }
                    }
                } catch (e: Exception) {
                    Log.e("RemotePreview", "Exception: ${e.message}")
                }
            }
            delay(2000)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(8.dp)
    ) {
        Text("Live Screen: ${device.name}")
        Spacer(modifier = Modifier.height(16.dp))

        bitmap?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = "Remote screen",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp), contentScale = ContentScale.FillWidth
            )
        } ?: Text("Menunggu tangkapan layar...")
    }
}