package com.mories.control_droid.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mories.control_droid.core.model.PairedDevice
import com.mories.control_droid.ui.components.AppToolbar
import com.mories.control_droid.ui.components.DeviceCard

@Composable
fun HomeScreen(
    devices: List<PairedDevice>,
    onDeviceClick: (PairedDevice) -> Unit,
    onAddClick: () -> Unit = {},
    onMacrosClick: () -> Unit = {},
    onBroadcastHome: () -> Unit = {}
) {
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            AppToolbar(title = "ControlDroid") {
                IconButton(onClick = onMacrosClick) {
                    Icon(Icons.AutoMirrored.Filled.List, contentDescription = "Open macros")
                }
                IconButton(onClick = onAddClick) {
                    Icon(Icons.Default.Add, contentDescription = "Add Device")
                }
            }
        }) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Column {
                    Text("Perangkat Anda", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        text = if (devices.isEmpty()) {
                            "Tambahkan perangkat Target untuk mulai mengontrolnya."
                        } else {
                            "Pilih perangkat untuk membuka panel kontrol."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (devices.isEmpty()) {
                item {
                    Card {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Text("Belum ada perangkat", style = MaterialTheme.typography.titleLarge)
                            Text(
                                text = "Scan QR pairing atau cari perangkat di jaringan lokal.",
                                modifier = Modifier.padding(top = 6.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Button(
                                onClick = onAddClick,
                                modifier = Modifier.padding(top = 16.dp)
                            ) {
                                Text("Tambah perangkat")
                            }
                        }
                    }
                }
            } else {
                item {
                    Card {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Aksi cepat", style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = "Kirim tombol Home ke semua perangkat yang tersimpan.",
                                modifier = Modifier.padding(top = 4.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            FilledTonalButton(
                                onClick = onBroadcastHome,
                                modifier = Modifier.padding(top = 12.dp)
                            ) {
                                Text("Home semua perangkat")
                            }
                        }
                    }
                }
                items(devices) { device ->
                    DeviceCard(
                        name = device.name,
                        ip = device.ip,
                        onClick = { onDeviceClick(device) }
                    )
                }
            }
        }
    }
}
