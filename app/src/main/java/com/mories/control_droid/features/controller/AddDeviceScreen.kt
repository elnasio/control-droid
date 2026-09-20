package com.mories.control_droid.features.controller

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.mories.control_droid.core.auth.ControllerIdentityStore
import com.mories.control_droid.core.control.DeviceScanner
import com.mories.control_droid.core.model.PairingQrPayload
import com.mories.control_droid.core.model.PairedDevice
import com.mories.control_droid.core.networking.DeviceHttpClient
import com.mories.control_droid.ui.components.DeviceCard
import com.mories.control_droid.ui.components.AppToolbar
import com.mories.control_droid.ui.components.PinDialog
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.UUID

@Composable
fun AddDeviceScreen(
    pairedDevices: List<PairedDevice>,
    onDeviceFound: (PairedDevice) -> Unit,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val controllerIdentity = remember { ControllerIdentityStore(context) }
    var devices by remember { mutableStateOf<List<PairedDevice>>(emptyList()) }
    var isScanning by remember { mutableStateOf(false) }
    var isAwaitingApproval by remember { mutableStateOf(false) }
    var pendingDevice by remember { mutableStateOf<PairedDevice?>(null) }
    var pairingError by remember { mutableStateOf<String?>(null) }
    val mainScope = remember { MainScope() }
    val qrLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        val payload = result.contents?.let(PairingQrPayload::decode)
        if (payload == null) {
            pairingError = if (result.contents == null) "QR scan dibatalkan" else "QR ControlDroid tidak valid"
        } else {
            isAwaitingApproval = true
            pairingError = null
            DeviceHttpClient(
                targetIp = payload.ip,
                targetPort = payload.port,
                pin = "",
                accessToken = payload.token,
                controllerId = controllerIdentity.getOrCreateId(),
                controllerName = controllerIdentity.displayName()
            ).verifyPairing(payload.token) { valid, message ->
                mainScope.launch(Dispatchers.Main) {
                    isAwaitingApproval = false
                    if (valid) {
                        // Reuse the existing entry's id/pin when this IP is already paired, so
                        // re-scanning the same Target updates it in place instead of adding a
                        // duplicate row in the paired-device list.
                        val existing = pairedDevices.find { it.ip == payload.ip }
                        onDeviceFound(
                            existing?.copy(
                                name = payload.name,
                                accessToken = payload.token,
                                lastConnected = System.currentTimeMillis()
                            ) ?: PairedDevice(
                                id = UUID.randomUUID().toString(),
                                name = payload.name,
                                ip = payload.ip,
                                pin = "",
                                lastConnected = System.currentTimeMillis(),
                                accessToken = payload.token
                            )
                        )
                    } else {
                        pairingError = message ?: "Pairing token ditolak"
                    }
                }
            }
        }
    }

    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose { mainScope.cancel() }
    }

    LaunchedEffect(Unit) {
        isScanning = true
        devices = DeviceScanner.scanLocalDevices()
        Log.d("AddDeviceScreen", "Scan result: ${devices.size} devices")
        isScanning = false
    }

    pendingDevice?.let { device ->
        PinEntryDialog(
            deviceName = device.name,
            onDismiss = { pendingDevice = null },
            onConfirm = { pin ->
                pendingDevice = null
                isAwaitingApproval = true
                pairingError = null
                // Validate the PIN against the Target before saving it, mirroring the QR flow's
                // token check, so a wrong PIN is rejected immediately instead of being silently
                // stored and only failing later on the first real action.
                DeviceHttpClient(
                    targetIp = device.ip,
                    pin = pin,
                    controllerId = controllerIdentity.getOrCreateId(),
                    controllerName = controllerIdentity.displayName()
                ).verifyPin(pin) { valid, message ->
                    mainScope.launch(Dispatchers.Main) {
                        isAwaitingApproval = false
                        if (valid) {
                            onDeviceFound(device.copy(pin = pin))
                        } else {
                            pairingError = "PIN ditolak oleh ${device.name}: ${message ?: "tidak diketahui"}"
                        }
                    }
                }
            }
        )
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            AppToolbar(
                title = "Add Device",
                onBackClick = onBackClick
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text("Tambahkan perangkat", style = MaterialTheme.typography.headlineSmall)
                Text(
                    text = "Gunakan QR pairing untuk cara tercepat, atau pilih perangkat yang ditemukan di jaringan lokal.",
                    modifier = Modifier.padding(top = 4.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            item {
                ElevatedCard {
                    androidx.compose.foundation.layout.Column(modifier = Modifier.padding(16.dp)) {
                        Text("Pairing dengan QR", style = MaterialTheme.typography.titleLarge)
                        Text(
                            text = "Scan kode QR yang tampil di Target untuk menyimpan koneksi dengan aman.",
                            modifier = Modifier.padding(top = 6.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(
                            onClick = {
                                qrLauncher.launch(
                                    ScanOptions()
                                        .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                                        .setPrompt("Scan QR pairing dari Target")
                                        .setBeepEnabled(false)
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 16.dp)
                        ) {
                            Text("Scan QR pairing")
                        }
                    }
                }
            }
            pairingError?.let { error ->
                item {
                    ElevatedCard {
                        Text(
                            text = error,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            }
            if (isAwaitingApproval) {
                item {
                    ElevatedCard {
                        androidx.compose.foundation.layout.Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("Menunggu persetujuan di Target...", style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = "Buka layar Target dan ketuk Terima pada permintaan pairing.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            }
            if (isScanning) {
                item {
                    androidx.compose.foundation.layout.Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Mencari perangkat di jaringan...", style = MaterialTheme.typography.titleMedium)
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                }
            } else if (devices.isEmpty()) {
                item {
                    ElevatedCard {
                        Text(
                            "Belum ada perangkat ditemukan. Pastikan Target dan Controller berada di Wi-Fi yang sama.",
                            modifier = Modifier.padding(16.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                item {
                    Text("Perangkat ditemukan", style = MaterialTheme.typography.titleLarge)
                }
                items(devices) { device ->
                    val existing = pairedDevices.find { it.ip == device.ip }
                    DeviceCard(
                        name = if (existing != null) "${device.name} (sudah dipasangkan)" else device.name,
                        ip = device.ip,
                        onClick = {
                            if (existing != null) {
                                // Already paired at this IP: reconnect using the stored
                                // credentials instead of demanding a new PIN, and update its
                                // entry in place rather than adding a duplicate.
                                onDeviceFound(existing.copy(lastConnected = System.currentTimeMillis()))
                            } else {
                                pendingDevice = device
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun PinEntryDialog(
    deviceName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    PinDialog(
        title = "Masukkan PIN untuk $deviceName",
        confirmLabel = "Pair",
        onDismiss = onDismiss,
        onConfirm = onConfirm
    )
}
