package com.mories.control_droid.features.target

import android.content.Intent
import android.provider.Settings
import android.util.Log
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.mories.control_droid.core.auth.PinVerifier
import com.mories.control_droid.core.auth.PairingTokenStore
import com.mories.control_droid.core.ConstantValue
import com.mories.control_droid.core.control.ScreenCaptureManager
import com.mories.control_droid.core.control.NetworkAddress
import com.mories.control_droid.core.control.PairingQrCodeGenerator
import com.mories.control_droid.core.model.PairingQrPayload
import com.mories.control_droid.core.server.TargetHttpServer
import com.mories.control_droid.ui.components.AppToolbar
import com.mories.control_droid.ui.components.PairingQrCard
import com.mories.control_droid.ui.components.PinDialog
import com.mories.control_droid.ui.components.StatusBadge

@Composable
fun TargetWaitingScreen(deviceName: String = "This Device") {
    val context = LocalContext.current
    val pinVerifier = remember { PinVerifier(context) }
    val pairingTokenStore = remember { PairingTokenStore(context) }
    var isPinSet by remember { mutableStateOf(pinVerifier.isPinSet()) }
    var pairingToken by remember { mutableStateOf(pairingTokenStore.getOrCreateToken()) }
    var showPinDialog by remember { mutableStateOf(false) }
    val qrPayload = remember(deviceName, pairingToken) {
        NetworkAddress.localIpv4()?.let { ip ->
            PairingQrPayload(
                name = deviceName,
                ip = ip,
                port = ConstantValue.PORT_VALUE,
                token = pairingToken
            )
        }
    }
    val qrBitmap = remember(qrPayload) {
        qrPayload?.let { PairingQrCodeGenerator.create(PairingQrPayload.encode(it), 420) }
    }

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

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = { AppToolbar(title = "Target mode") }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text("Perangkat siap digunakan", style = MaterialTheme.typography.headlineSmall)
                Text(
                    text = "$deviceName menunggu koneksi dari Controller.",
                    modifier = Modifier.padding(top = 4.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                StatusBadge(
                    label = "Menunggu controller",
                    active = true,
                    modifier = Modifier.padding(top = 10.dp)
                )
            }
            item {
                ElevatedCard {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Pairing perangkat", style = MaterialTheme.typography.titleLarge)
                        Text(
                            text = "Scan QR ini dari perangkat Controller yang berada di jaringan Wi-Fi yang sama.",
                            modifier = Modifier.padding(top = 6.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        qrBitmap?.let { bitmap ->
                            PairingQrCard(
                                bitmap = bitmap.asImageBitmap(),
                                onRegenerate = { pairingToken = pairingTokenStore.regenerateToken() },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 12.dp)
                            )
                        } ?: Text(
                            "Alamat jaringan belum tersedia.",
                            modifier = Modifier.padding(top = 12.dp),
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
            item {
                ElevatedCard {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Keamanan", style = MaterialTheme.typography.titleLarge)
                        Text(
                            text = if (isPinSet) {
                                "PIN aktif. Controller harus memiliki akses pairing yang valid."
                            } else {
                                "Atur PIN agar permintaan kontrol legacy tidak ditolak."
                            },
                            modifier = Modifier.padding(top = 6.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        StatusBadge(
                            label = if (isPinSet) "PIN aktif" else "PIN belum diatur",
                            active = isPinSet,
                            modifier = Modifier.padding(top = 12.dp)
                        )
                        Button(
                            onClick = { showPinDialog = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp)
                        ) {
                            Text(if (isPinSet) "Ganti PIN" else "Atur PIN")
                        }
                    }
                }
            }
            item {
                ElevatedCard {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Izin perangkat", style = MaterialTheme.typography.titleLarge)
                        Text(
                            text = "Aktifkan Accessibility dan izin tangkapan layar agar semua fitur kontrol tersedia.",
                            modifier = Modifier.padding(top = 6.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(
                            onClick = {
                                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                context.startActivity(intent)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp)
                        ) {
                            Text("Buka Accessibility Settings")
                        }
                        Button(
                            onClick = {
                                val intent = Intent(context, ScreenPermissionActivity::class.java)
                                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                context.startActivity(intent)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp)
                        ) {
                            Text("Izinkan screen capture")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PinSetupDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    PinDialog(
        title = "Atur PIN",
        confirmLabel = "Simpan",
        onDismiss = onDismiss,
        onConfirm = onConfirm
    )
}
