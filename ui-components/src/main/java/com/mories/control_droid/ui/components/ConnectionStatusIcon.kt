package com.mories.control_droid.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import com.mories.control_droid.ui.theme.ControldroidTheme

/** Which transport a control screen's toolbar indicator should reflect right now. */
enum class ConnectionIndicatorState { WIFI, INTERNET, DISCONNECTED }

private val WifiGreen = Color(0xFF2E7D32)
private val InternetYellow = Color(0xFFF9A825)
private val DisconnectedRed = Color(0xFFC62828)

/**
 * Toolbar status icon: green Wi-Fi icon when connected locally, yellow cloud icon when connected
 * through the Internet relay, red disconnected-cloud icon when neither is reachable. Shape
 * differs per state (not just color) so the status also reads for colorblind users.
 */
@Composable
fun ConnectionStatusIcon(state: ConnectionIndicatorState, modifier: Modifier = Modifier) {
    val (icon, color, description) = when (state) {
        ConnectionIndicatorState.WIFI -> Triple(Icons.Default.Wifi, WifiGreen, "Terhubung via Wi-Fi")
        ConnectionIndicatorState.INTERNET -> Triple(Icons.Default.Cloud, InternetYellow, "Terhubung via Internet")
        ConnectionIndicatorState.DISCONNECTED -> Triple(Icons.Default.CloudOff, DisconnectedRed, "Tidak terhubung")
    }
    Icon(
        imageVector = icon,
        contentDescription = description,
        tint = color,
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
private fun ConnectionStatusIconWifiPreview() {
    ControldroidTheme(dynamicColor = false) {
        ConnectionStatusIcon(state = ConnectionIndicatorState.WIFI)
    }
}

@Preview(showBackground = true)
@Composable
private fun ConnectionStatusIconInternetPreview() {
    ControldroidTheme(dynamicColor = false) {
        ConnectionStatusIcon(state = ConnectionIndicatorState.INTERNET)
    }
}

@Preview(showBackground = true)
@Composable
private fun ConnectionStatusIconDisconnectedPreview() {
    ControldroidTheme(dynamicColor = false) {
        ConnectionStatusIcon(state = ConnectionIndicatorState.DISCONNECTED)
    }
}
