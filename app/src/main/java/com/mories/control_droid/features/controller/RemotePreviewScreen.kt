package com.mories.control_droid.features.controller

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.mories.control_droid.core.model.PairedDevice
import com.mories.control_droid.core.model.GestureRequest
import com.mories.control_droid.core.model.GestureType
import com.mories.control_droid.core.networking.DeviceHttpClient
import com.mories.control_droid.ui.components.RemotePreviewSurface
import com.mories.control_droid.ui.components.AppToolbar
import com.mories.control_droid.ui.components.StatusBadge
import com.mories.control_droid.features.viewmodel.RemotePreviewEvent
import com.mories.control_droid.features.viewmodel.RemotePreviewViewModel

@Composable
fun RemotePreviewScreen(
    navController: NavController,
    device: PairedDevice,
    viewModel: RemotePreviewViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val client = remember {
        DeviceHttpClient(device.ip, pin = device.pin, accessToken = device.accessToken.orEmpty())
    }

    LaunchedEffect(device.ip) {
        viewModel.onEvent(
            RemotePreviewEvent.StartPolling(device.ip, device.pin, device.accessToken.orEmpty())
        )
    }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.onEvent(RemotePreviewEvent.StopPolling)
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            AppToolbar(
                title = "Live preview",
                onBackClick = { navController.popBackStack() }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Text(device.name, style = MaterialTheme.typography.headlineSmall)
            Text(
                text = "Ketuk untuk tap atau geser untuk swipe.",
                modifier = Modifier.padding(top = 4.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            StatusBadge(
                label = when {
                    state.isLoading -> "Memuat layar"
                    state.bitmap != null -> "Live"
                    state.error != null -> "Gagal memuat"
                    else -> "Menunggu layar"
                },
                active = state.bitmap != null,
                modifier = Modifier.padding(top = 10.dp)
            )

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(top = 16.dp)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = androidx.compose.ui.Alignment.Center
                ) {
                    when {
                        state.isLoading -> {
                            Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                                CircularProgressIndicator()
                                Text(
                                    "Mengambil tangkapan layar...",
                                    modifier = Modifier.padding(top = 12.dp)
                                )
                            }
                        }

                        state.bitmap != null -> {
                            RemotePreviewSurface(
                                image = state.bitmap!!.asImageBitmap(),
                                modifier = Modifier.fillMaxSize().padding(8.dp),
                                onTap = { x, y ->
                                    client.sendGesture(
                                        GestureRequest(
                                            type = GestureType.TAP,
                                            startX = x,
                                            startY = y,
                                            durationMs = 80
                                        )
                                    )
                                },
                                onSwipe = { startX, startY, endX, endY ->
                                    client.sendGesture(
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

                        state.error != null -> {
                            Text(
                                text = "Gagal memuat layar:\n${state.error}",
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(24.dp)
                            )
                        }

                        else -> {
                            Text("Menunggu tangkapan layar...")
                        }
                    }
                }
            }
        }
    }
}
