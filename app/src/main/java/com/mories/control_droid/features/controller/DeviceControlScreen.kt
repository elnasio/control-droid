package com.mories.control_droid.features.controller

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import com.mories.control_droid.core.networking.LocalWebSocketClient
import com.mories.control_droid.ui.NavigationTarget

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceControlScreen(
    navController: NavController, device: PairedDevice
) {
    val client = remember { LocalWebSocketClient(device.ip, pin = device.pin) }
    var connected by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        client.connect {
            // Optional: handle response
        }
        connected = true
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Kontrol: ${device.name}") }, navigationIcon = {
                IconButton(onClick = { navController.popBackStack() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            })
        }) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Aksi Navigasi")

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = { client.sendAction(DeviceAction.GLOBAL_BACK) }) { Text("⬅️ Back") }
                Button(onClick = { client.sendAction(DeviceAction.GLOBAL_HOME) }) { Text("🏠 Home") }
                Button(onClick = { client.sendAction(DeviceAction.GLOBAL_RECENT) }) { Text("📋 Recent") }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            Text("Layar Jarak Jauh")

            Button(
                onClick = {
                    client.sendAction(DeviceAction.CAPTURE_SCREEN)
                    navController.navigate(NavigationTarget.Preview.withArg(device.id))
                }, enabled = connected
            ) {
                Text("👀 Lihat Layar")
            }
            // Tambahan fitur to-do lain ke depannya
            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
            Text("Menu lainnya akan segera hadir...")
        }
    }
}