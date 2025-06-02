package com.mories.control_droid.features.controller

import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mories.control_droid.core.control.DeviceScanner
import com.mories.control_droid.core.model.PairedDevice

@Composable
fun AddDeviceScreen(
    onDeviceFound: (PairedDevice) -> Unit
) {
    var devices by remember { mutableStateOf<List<PairedDevice>>(emptyList()) }
    var isScanning by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        isScanning = true
        devices = DeviceScanner.scanLocalDevices()
        Log.d("AddDeviceScreen", "Scan result: ${devices.size} devices")
        isScanning = false
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text("Scanning devices on network...", style = MaterialTheme.typography.titleMedium)
        if (isScanning) {
            CircularProgressIndicator(modifier = Modifier.padding(top = 16.dp))
        } else if (devices.isEmpty()) {
            Text("No devices found", modifier = Modifier.padding(top = 16.dp))
        } else {
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(devices) { device ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clickable { onDeviceFound(device) }) {
                        Column(Modifier.padding(16.dp)) {
                            Text(device.name, style = MaterialTheme.typography.titleMedium)
                            Text("IP: ${device.ip}")
                        }
                    }
                }
            }
        }
    }
}