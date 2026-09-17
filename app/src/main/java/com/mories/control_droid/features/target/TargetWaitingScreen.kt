package com.mories.control_droid.features.target

import android.content.Intent
import android.provider.Settings
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.mories.control_droid.core.auth.PinVerifier
import com.mories.control_droid.core.control.ScreenCaptureManager
import com.mories.control_droid.core.server.TargetHttpServer

@Composable
fun TargetWaitingScreen(deviceName: String = "This Device") {
    val context = LocalContext.current
    val pinVerifier = remember { PinVerifier(context) }
    var isPinSet by remember { mutableStateOf(pinVerifier.isPinSet()) }
    var showPinDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        try {
            val appContext = context.applicationContext
            TargetHttpServer.startServer { appContext }
            if (ScreenCaptureManager.isReady()) {
                ScreenCaptureManager.startAutoCapture(context)
            }
        } catch (e: Exception) {
            Log.e("TargetWaiting", "Failed to start server: ${e.message}")
        }
    }

    if (showPinDialog) {
        PinSetupDialog(
            onDismiss = { showPinDialog = false },
            onConfirm = { pin ->
                pinVerifier.setPin(pin)
                isPinSet = true
                showPinDialog = false
            }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = Color.White)
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "$deviceName is ready to be controlled",
                color = Color.White,
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Waiting for controller...",
                color = Color.Gray,
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = if (isPinSet) "PIN aktif" else "⚠️ PIN belum diatur — kontrol akan ditolak",
                color = if (isPinSet) Color.Green else Color.Yellow,
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = { showPinDialog = true }) {
                Text(if (isPinSet) "Ganti PIN" else "Atur PIN")
            }
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = {
                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                context.startActivity(intent)
            }) {
                Text("Open Accessibility Settings")
            }
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = {
                val intent = Intent(context, ScreenPermissionActivity::class.java)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            }) {
                Text("Grant Screenshot Permission")
            }
        }
    }
}

@Composable
private fun PinSetupDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var pin by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Atur PIN") },
        text = {
            OutlinedTextField(
                value = pin,
                onValueChange = { pin = it },
                label = { Text("PIN") },
                singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    keyboardType = KeyboardType.NumberPassword
                )
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(pin) }, enabled = pin.isNotBlank()) {
                Text("Simpan")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Batal") }
        }
    )
}
