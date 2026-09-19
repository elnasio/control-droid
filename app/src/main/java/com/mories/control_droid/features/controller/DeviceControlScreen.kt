package com.mories.control_droid.features.controller

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.mories.control_droid.core.model.DeviceAction
import com.mories.control_droid.core.model.PairedDevice
import com.mories.control_droid.core.networking.DeviceHttpClient
import com.mories.control_droid.ui.NavigationTarget
import com.mories.control_droid.ui.components.AppToolbar
import com.mories.control_droid.ui.components.ControlActionButton
import com.mories.control_droid.ui.components.StatusBadge

@Composable
fun DeviceControlScreen(
    navController: NavController, device: PairedDevice
) {
    val client = remember {
        DeviceHttpClient(device.ip, pin = device.pin, accessToken = device.accessToken.orEmpty())
    }
    var connected by remember { mutableStateOf(false) }
    var clipboardText by remember { mutableStateOf("") }
    var clipboardStatus by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        client.ping { reachable -> connected = reachable }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            AppToolbar(
                title = "Kontrol: ${device.name}",
                onBackClick = { navController.popBackStack() }
            )
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
                    StatusBadge(
                        label = if (connected) "Terhubung" else "Tidak terhubung",
                        active = connected
                    )
                }
            }
            item {
                ElevatedCard {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("Aksi navigasi", style = MaterialTheme.typography.titleLarge)
                        ControlActionButton(
                            label = "Back",
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { client.sendAction(DeviceAction.GLOBAL_BACK) }
                        )
                        ControlActionButton(
                            label = "Home",
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { client.sendAction(DeviceAction.GLOBAL_HOME) }
                        )
                        ControlActionButton(
                            label = "Recent apps",
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { client.sendAction(DeviceAction.GLOBAL_RECENT) }
                        )
                    }
                }
            }
            item {
                ElevatedCard {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("Layar jarak jauh", style = MaterialTheme.typography.titleLarge)
                        Text(
                            text = "Lihat layar Target dan kirim gesture secara langsung.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(
                            onClick = {
                                client.sendAction(DeviceAction.CAPTURE_SCREEN)
                                navController.navigate(NavigationTarget.Preview.withArg(device.id))
                            },
                            enabled = connected,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Buka live preview")
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
                            maxLines = 5
                        )
                        Button(
                            onClick = {
                                client.sendClipboard(
                                    com.mories.control_droid.core.model.ClipboardRequest(clipboardText)
                                ) { success -> clipboardStatus = if (success) "Teks tersalin ke Target" else "Gagal mengirim teks" }
                            },
                            enabled = connected && clipboardText.isNotBlank(),
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Kirim clipboard") }
                        Button(
                            onClick = {
                                client.sendClipboard(
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
