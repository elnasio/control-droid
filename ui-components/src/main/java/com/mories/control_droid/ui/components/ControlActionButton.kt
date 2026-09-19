package com.mories.control_droid.ui.components

import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.mories.control_droid.ui.theme.ControldroidTheme

@Composable
fun ControlActionButton(
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    FilledTonalButton(onClick = onClick, enabled = enabled, modifier = modifier) {
        Text(label)
    }
}

@Preview(showBackground = true)
@Composable
private fun ControlActionButtonPreview() {
    ControldroidTheme(dynamicColor = false) {
        ControlActionButton(label = "Home", onClick = {})
    }
}
