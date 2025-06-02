package com.mories.control_droid.features.controller

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.mories.control_droid.core.model.PairedDevice
import com.mories.control_droid.features.viewmodel.RemotePreviewEvent
import com.mories.control_droid.features.viewmodel.RemotePreviewViewModel

@Composable
fun RemotePreviewScreen(
    navController: NavController,
    device: PairedDevice,
    viewModel: RemotePreviewViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(device.ip) {
        viewModel.onEvent(RemotePreviewEvent.StartPolling(device.ip))
    }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.onEvent(RemotePreviewEvent.StopPolling)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(8.dp)
    ) {
        Text("Live Screen: ${device.name}")
        Spacer(modifier = Modifier.height(16.dp))

        when {
            state.isLoading -> {
                CircularProgressIndicator()
            }

            state.bitmap != null -> {
                Image(
                    bitmap = state.bitmap!!.asImageBitmap(),
                    contentDescription = "Remote screen",
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentScale = ContentScale.Fit
                )
            }

            state.error != null -> {
                Text("❌ Error: ${state.error}")
            }

            else -> {
                Text("Menunggu tangkapan layar...")
            }
        }
    }
}