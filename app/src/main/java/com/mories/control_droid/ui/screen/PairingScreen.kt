package com.mories.control_droid.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.mories.control_droid.core.model.PairedDevice
import com.mories.control_droid.core.storage.PairedDeviceStore
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PairingScreen(navController: NavController) {
    val context = LocalContext.current
    val store = remember { PairedDeviceStore(context) }

    var name by remember { mutableStateOf("") }
    var ip by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Pair Device") })
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Device Name") },
                singleLine = true
            )

            OutlinedTextField(
                value = ip,
                onValueChange = { ip = it },
                label = { Text("Device IP") },
                singleLine = true
            )

            OutlinedTextField(
                value = pin,
                onValueChange = { pin = it },
                label = { Text("PIN") },
                singleLine = true
            )

            Button(
                onClick = {
                    if (name.isNotBlank() && ip.isNotBlank() && pin.isNotBlank()) {
                        val newDevice = PairedDevice(
                            id = UUID.randomUUID().toString(),
                            name = name.trim(),
                            ip = ip.trim(),
                            pin = pin.trim(),
                            lastConnected = System.currentTimeMillis()
                        )
                        store.saveDevice(newDevice)
                        navController.popBackStack()
                    }
                },
                enabled = name.isNotBlank() && ip.isNotBlank() && pin.isNotBlank()
            ) {
                Text("Simpan dan Pair")
            }
        }
    }
}