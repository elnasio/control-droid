package com.mories.control_droid.features.controller

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.mories.control_droid.core.model.ControlTransportMode
import com.mories.control_droid.core.model.DeviceAction
import com.mories.control_droid.core.model.GestureRequest
import com.mories.control_droid.core.model.GestureType
import com.mories.control_droid.core.model.PairedDevice
import com.mories.control_droid.core.networking.DeviceControlClient
import com.mories.control_droid.core.networking.DeviceHttpClient
import com.mories.control_droid.core.networking.InternetRelayClient
import com.mories.control_droid.features.viewmodel.RemotePreviewEvent
import com.mories.control_droid.features.viewmodel.RemotePreviewViewModel
import com.mories.control_droid.ui.components.AppToolbar
import com.mories.control_droid.ui.components.ConnectionIndicatorState
import com.mories.control_droid.ui.components.ConnectionStatusIcon
import com.mories.control_droid.ui.components.ControlActionButton
import com.mories.control_droid.ui.components.ControlPad
import com.mories.control_droid.ui.components.ControlPadDirection
import com.mories.control_droid.ui.components.RemotePreviewSurface
import com.mories.control_droid.ui.components.StatusBadge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun DeviceControlScreen(
    navController: NavController, device: PairedDevice
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val wifiClient = remember {
        DeviceHttpClient(device.ip, pin = device.pin, accessToken = device.accessToken.orEmpty())
    }
    val internetClient = remember {
        InternetRelayClient(deviceId = device.id, accessToken = device.accessToken.orEmpty())
    }
    // The relay never accepts the legacy PIN (see InternetRelayClient) — only an access token from
    // QR pairing is strong enough to be internet-facing — so a PIN-only paired device can't use it.
    val internetModeAvailable = !device.accessToken.isNullOrBlank()
    var transportMode by remember { mutableStateOf(ControlTransportMode.WIFI) }
    val activeClient: DeviceControlClient =
        if (transportMode == ControlTransportMode.INTERNET) internetClient else wifiClient
    var connected by remember { mutableStateOf(false) }
    var clipboardText by remember { mutableStateOf("") }
    var clipboardStatus by remember { mutableStateOf<String?>(null) }
    var showControlPad by remember { mutableStateOf(false) }
    var showLivePreview by remember { mutableStateOf(false) }
    val clipboardManager = LocalClipboardManager.current
    val previewViewModel: RemotePreviewViewModel = viewModel()
    val previewState by previewViewModel.uiState.collectAsState()
    val connectionIndicatorState = when {
        !connected -> ConnectionIndicatorState.DISCONNECTED
        transportMode == ControlTransportMode.INTERNET -> ConnectionIndicatorState.INTERNET
        else -> ConnectionIndicatorState.WIFI
    }

    fun sendNavAction(action: DeviceAction) {
        activeClient.sendAction(action) { success ->
            val message = if (success) {
                "${action.label} terkirim"
            } else {
                "Gagal mengirim ${action.label} — cek koneksi dan Accessibility di Target"
            }
            // sendAction's callback runs on OkHttp's background dispatcher thread; Toast must be
            // shown from the main thread.
            scope.launch(Dispatchers.Main) {
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    LaunchedEffect(transportMode) {
        connected = false
        activeClient.checkStatus { reachableAndAuthorized -> connected = reachableAndAuthorized }
    }

    LaunchedEffect(showLivePreview, transportMode) {
        if (showLivePreview) {
            activeClient.sendAction(DeviceAction.CAPTURE_SCREEN)
            previewViewModel.onEvent(RemotePreviewEvent.StartPolling(activeClient))
        } else {
            previewViewModel.onEvent(RemotePreviewEvent.StopPolling)
        }
    }

    DisposableEffect(Unit) {
        onDispose { previewViewModel.onEvent(RemotePreviewEvent.StopPolling) }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            AppToolbar(
                title = "Kontrol: ${device.name}",
                onBackClick = { navController.popBackStack() },
                actions = {
                    ConnectionStatusIcon(
                        state = connectionIndicatorState,
                        modifier = Modifier.padding(end = 12.dp)
                    )
                }
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp, shadowElevation = 8.dp) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("Aksi navigasi", style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ControlActionButton(
                            label = "Back",
                            modifier = Modifier.weight(1f),
                            enabled = connected,
                            onClick = { sendNavAction(DeviceAction.GLOBAL_BACK) }
                        )
                        ControlActionButton(
                            label = "Home",
                            modifier = Modifier.weight(1f),
                            enabled = connected,
                            onClick = { sendNavAction(DeviceAction.GLOBAL_HOME) }
                        )
                        ControlActionButton(
                            label = "Recent",
                            modifier = Modifier.weight(1f),
                            enabled = connected,
                            onClick = { sendNavAction(DeviceAction.GLOBAL_RECENT) }
                        )
                    }
                }
            }
        }) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Kontrol perangkat", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        text = "Kirim perintah ke ${device.name}.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            item {
                ElevatedCard {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Kontrol via Internet", style = MaterialTheme.typography.titleLarge)
                                Text(
                                    text = when {
                                        !internetModeAvailable ->
                                            "Device ini dipasangkan pakai PIN tanpa token — pairing ulang lewat QR untuk mengaktifkan mode Internet."
                                        transportMode == ControlTransportMode.INTERNET ->
                                            "Perintah dikirim lewat relay internet (backend belum aktif — siap disambungkan)."
                                        else -> "Perintah dikirim langsung lewat Wi-Fi lokal, seperti biasa."
                                    },
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = transportMode == ControlTransportMode.INTERNET,
                                enabled = internetModeAvailable,
                                onCheckedChange = {
                                    transportMode = if (it) {
                                        ControlTransportMode.INTERNET
                                    } else {
                                        ControlTransportMode.WIFI
                                    }
                                }
                            )
                        }
                    }
                }
            }
            item {
                ElevatedCard {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Control pad", style = MaterialTheme.typography.titleLarge)
                                Text(
                                    text = "Tampilkan tombol arah untuk mengirim swipe terarah ke Target.",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = showControlPad,
                                onCheckedChange = { showControlPad = it },
                                enabled = connected
                            )
                        }
                        if (showControlPad) {
                            ControlPad(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp),
                                enabled = connected,
                                onDirectionClick = { direction ->
                                    activeClient.sendGesture(direction.toGestureRequest())
                                }
                            )
                        }
                    }
                }
            }
            item {
                ElevatedCard {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Layar jarak jauh", style = MaterialTheme.typography.titleLarge)
                                Text(
                                    text = "Lihat layar Target dan kirim gesture secara langsung.",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = showLivePreview,
                                onCheckedChange = { showLivePreview = it },
                                enabled = connected
                            )
                        }
                        if (showLivePreview) {
                            StatusBadge(
                                label = when {
                                    previewState.isLoading -> "Memuat layar"
                                    previewState.bitmap != null -> "Live"
                                    previewState.error != null -> "Gagal memuat"
                                    else -> "Menunggu layar"
                                },
                                active = previewState.bitmap != null
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(360.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                when {
                                    previewState.isLoading -> {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            CircularProgressIndicator()
                                            Text(
                                                "Mengambil tangkapan layar...",
                                                modifier = Modifier.padding(top = 12.dp)
                                            )
                                        }
                                    }

                                    previewState.bitmap != null -> {
                                        RemotePreviewSurface(
                                            image = previewState.bitmap!!.asImageBitmap(),
                                            modifier = Modifier.fillMaxSize(),
                                            onTap = { x, y ->
                                                activeClient.sendGesture(
                                                    GestureRequest(
                                                        type = GestureType.TAP,
                                                        startX = x,
                                                        startY = y,
                                                        durationMs = 80
                                                    )
                                                )
                                            },
                                            onSwipe = { startX, startY, endX, endY ->
                                                activeClient.sendGesture(
                                                    GestureRequest(
                                                        type = GestureType.SWIPE,
                                                        startX = startX,
                                                        startY = startY,
                                                        endX = endX,
                                                        endY = endY,
                                                        durationMs = 350
                                                    )
                                                )
                                            }
                                        )
                                    }

                                    previewState.error != null -> {
                                        Text(
                                            text = "Gagal memuat layar:\n${previewState.error}",
                                            color = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.padding(24.dp)
                                        )
                                    }

                                    else -> Text("Menunggu tangkapan layar...")
                                }
                            }
                        }
                    }
                }
            }
            item {
                ElevatedCard {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("Clipboard", style = MaterialTheme.typography.titleLarge)
                        Text(
                            text = "Kirim teks ke Target atau langsung tempel ke kolom yang sedang aktif.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedTextField(
                            value = clipboardText,
                            onValueChange = { clipboardText = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Teks untuk Target") },
                            minLines = 3,
                            maxLines = 5,
                            trailingIcon = {
                                Row {
                                    IconButton(
                                        onClick = {
                                            clipboardManager.getText()?.text?.let { pasted ->
                                                clipboardText = pasted
                                            }
                                        }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ContentPaste,
                                            contentDescription = "Tempel dari clipboard perangkat ini"
                                        )
                                    }
                                    IconButton(
                                        onClick = { clipboardText = "" },
                                        enabled = clipboardText.isNotEmpty()
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Hapus teks"
                                        )
                                    }
                                }
                            }
                        )
                        Button(
                            onClick = {
                                activeClient.sendClipboard(
                                    com.mories.control_droid.core.model.ClipboardRequest(clipboardText)
                                ) { success -> clipboardStatus = if (success) "Teks tersalin ke Target" else "Gagal mengirim teks" }
                            },
                            enabled = connected && clipboardText.isNotBlank(),
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Kirim clipboard") }
                        Button(
                            onClick = {
                                activeClient.sendClipboard(
                                    com.mories.control_droid.core.model.ClipboardRequest(clipboardText, paste = true)
                                ) { success -> clipboardStatus = if (success) "Teks dikirim dan ditempel" else "Gagal menempelkan teks" }
                            },
                            enabled = connected && clipboardText.isNotBlank(),
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Kirim dan tempel") }
                        clipboardStatus?.let {
                            Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

private fun ControlPadDirection.toGestureRequest(): GestureRequest {
    val center = 0.5f
    val offset = 0.2f
    return when (this) {
        ControlPadDirection.UP -> GestureRequest(
            type = GestureType.SWIPE,
            startX = center,
            startY = center + offset / 2,
            endX = center,
            endY = center - offset / 2,
            durationMs = 300
        )
        ControlPadDirection.DOWN -> GestureRequest(
            type = GestureType.SWIPE,
            startX = center,
            startY = center - offset / 2,
            endX = center,
            endY = center + offset / 2,
            durationMs = 300
        )
        ControlPadDirection.LEFT -> GestureRequest(
            type = GestureType.SWIPE,
            startX = center + offset / 2,
            startY = center,
            endX = center - offset / 2,
            endY = center,
            durationMs = 300
        )
        ControlPadDirection.RIGHT -> GestureRequest(
            type = GestureType.SWIPE,
            startX = center - offset / 2,
            startY = center,
            endX = center + offset / 2,
            endY = center,
            durationMs = 300
        )
    }
}
