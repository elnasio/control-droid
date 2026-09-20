package com.mories.control_droid.features.target

import android.content.Intent
import android.provider.Settings
import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.mories.control_droid.core.ConstantValue
import com.mories.control_droid.core.auth.PairingApprovalGate
import com.mories.control_droid.core.auth.PairingTokenStore
import com.mories.control_droid.core.auth.PinVerifier
import com.mories.control_droid.core.auth.TrustedControllerStore
import com.mories.control_droid.core.control.NetworkAddress
import com.mories.control_droid.core.control.PairingQrCodeGenerator
import com.mories.control_droid.core.control.ScreenCaptureManager
import com.mories.control_droid.core.model.PairingQrPayload
import com.mories.control_droid.core.server.TargetHttpServer
import com.mories.control_droid.ui.components.AppToolbar
import com.mories.control_droid.ui.components.PairingQrCard
import com.mories.control_droid.ui.components.PinDialog
import com.mories.control_droid.ui.components.StatusBadge
import kotlinx.coroutines.delay

private const val TAG = "TargetWaiting"
private const val CONTROLLER_ACTIVITY_TIMEOUT_MS = 6_000L

private data class TargetSession(
    val pinVerifier: PinVerifier,
    val pairingTokenStore: PairingTokenStore,
    val initialIsPinSet: Boolean,
    val initialToken: String
)

private fun describeError(t: Throwable): String = "${t::class.java.simpleName}: ${t.message ?: "no message"}"

@Composable
fun TargetWaitingScreen(deviceName: String = "This Device") {
    val context = LocalContext.current

    val sessionResult = remember {
        runCatching {
            val pinVerifier = PinVerifier(context)
            val pairingTokenStore = PairingTokenStore(context)
            TargetSession(
                pinVerifier = pinVerifier,
                pairingTokenStore = pairingTokenStore,
                initialIsPinSet = pinVerifier.isPinSet(),
                initialToken = pairingTokenStore.getOrCreateToken()
            )
        }.onFailure { e -> Log.e(TAG, "Target session setup failed", e) }
    }

    val session = sessionResult.getOrNull()
    if (session == null) {
        TargetSetupErrorScreen(sessionResult.exceptionOrNull())
        return
    }

    val pinVerifier = session.pinVerifier
    val pairingTokenStore = session.pairingTokenStore
    var isPinSet by remember { mutableStateOf(session.initialIsPinSet) }
    var pairingToken by remember { mutableStateOf(session.initialToken) }
    var showPinDialog by remember { mutableStateOf(false) }
    var qrError by remember { mutableStateOf<String?>(null) }

    val qrPayload = remember(deviceName, pairingToken) {
        runCatching {
            NetworkAddress.localIpv4()?.let { ip ->
                PairingQrPayload(
                    name = deviceName,
                    ip = ip,
                    port = ConstantValue.PORT_VALUE,
                    token = pairingToken
                )
            }
        }.onFailure { e ->
            Log.e(TAG, "Resolving local IP failed", e)
            qrError = describeError(e)
        }.getOrNull()
    }
    val qrBitmap = remember(qrPayload) {
        qrPayload?.let {
            runCatching { PairingQrCodeGenerator.create(PairingQrPayload.encode(it), 420) }
                .onFailure { e ->
                    Log.e(TAG, "QR generation failed", e)
                    qrError = describeError(e)
                }
                .getOrNull()
        }
    }

    LaunchedEffect(Unit) {
        try {
            val appContext = context.applicationContext
            TargetHttpServer.startServer { appContext }
            if (ScreenCaptureManager.isReady()) {
                ScreenCaptureManager.startAutoCapture(context)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start server: ${e.message}")
        }
    }

    val trustedControllerStore = remember { TrustedControllerStore(context) }
    val isScreenSharing by ScreenCaptureManager.isSharing.collectAsState()
    val lastControllerActivityAt by TargetHttpServer.lastControllerActivityAt.collectAsState()
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    var trustedControllers by remember { mutableStateOf(trustedControllerStore.getAll()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000)
            now = System.currentTimeMillis()
            trustedControllers = trustedControllerStore.getAll()
        }
    }
    val isControllerConnected = lastControllerActivityAt?.let { now - it < CONTROLLER_ACTIVITY_TIMEOUT_MS } ?: false

    val pendingPairingRequest by PairingApprovalGate.pendingRequest.collectAsState()
    pendingPairingRequest?.let { request ->
        AlertDialog(
            onDismissRequest = { },
            title = { Text("Permintaan pairing") },
            text = {
                Text("\"${request.controllerName}\" ingin memasangkan diri dan mengontrol perangkat ini.")
            },
            confirmButton = {
                TextButton(onClick = { PairingApprovalGate.approve() }) { Text("Terima") }
            },
            dismissButton = {
                TextButton(onClick = { PairingApprovalGate.reject() }) { Text("Tolak") }
            }
        )
    }

    if (showPinDialog) {
        PinSetupDialog(
            onDismiss = { showPinDialog = false },
            onConfirm = { pin ->
                runCatching { pinVerifier.setPin(pin) }
                    .onSuccess { isPinSet = true }
                    .onFailure { e -> Log.e(TAG, "Failed to set PIN", e) }
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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text("Perangkat siap digunakan", style = MaterialTheme.typography.headlineSmall)
                Text(
                    text = if (isControllerConnected) {
                        "$deviceName sedang terhubung dengan Controller."
                    } else {
                        "$deviceName menunggu koneksi dari Controller."
                    },
                    modifier = Modifier.padding(top = 4.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                StatusBadge(
                    label = if (isControllerConnected) "Terhubung dengan controller" else "Menunggu controller",
                    active = isControllerConnected,
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
                                onRegenerate = {
                                    runCatching { pairingTokenStore.regenerateToken() }
                                        .onSuccess { newToken -> pairingToken = newToken }
                                        .onFailure { e -> Log.e(TAG, "Failed to regenerate token", e) }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 12.dp)
                            )
                        } ?: Text(
                            qrError ?: "Alamat jaringan belum tersedia.",
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
                        Text("Controller terpercaya", style = MaterialTheme.typography.titleLarge)
                        Text(
                            text = if (trustedControllers.isEmpty()) {
                                "Belum ada Controller yang disetujui. Setiap pairing baru akan meminta persetujuan di sini."
                            } else {
                                "Controller berikut sudah disetujui dan tidak akan diminta pairing ulang."
                            },
                            modifier = Modifier.padding(top = 6.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        trustedControllers.forEach { controller ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                            ) {
                                Text(controller.name, modifier = Modifier.weight(1f))
                                TextButton(onClick = {
                                    trustedControllerStore.revoke(controller.id)
                                    trustedControllers = trustedControllerStore.getAll()
                                }) {
                                    Text("Hapus akses")
                                }
                            }
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
                            enabled = !isScreenSharing,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp)
                        ) {
                            Text("Izinkan screen capture")
                        }
                        if (isScreenSharing) {
                            Button(
                                onClick = { context.stopService(Intent(context, ScreenCaptureService::class.java)) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer,
                                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp)
                            ) {
                                Text("Hentikan screen share")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TargetSetupErrorScreen(error: Throwable?) {
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = { AppToolbar(title = "Target mode") }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            Text("Gagal menyiapkan mode Target", style = MaterialTheme.typography.headlineSmall)
            Text(
                text = "Terjadi error saat menyiapkan perangkat ini sebagai Target. " +
                    "Kirim pesan error di bawah ini agar bisa diperbaiki:",
                modifier = Modifier.padding(top = 8.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            ElevatedCard(modifier = Modifier.padding(top = 16.dp)) {
                Text(
                    text = error?.let(::describeError) ?: "Kesalahan tidak diketahui",
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.error
                )
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
