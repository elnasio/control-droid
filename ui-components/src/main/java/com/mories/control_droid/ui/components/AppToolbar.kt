package com.mories.control_droid.ui.components

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.Icons
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.mories.control_droid.ui.theme.ControldroidTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppToolbar(
    title: String,
    modifier: Modifier = Modifier,
    onBackClick: (() -> Unit)? = null,
    backContentDescription: String = "Back",
    navigationIcon: @Composable (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    TopAppBar(
        title = { Text(title) },
        modifier = modifier,
        windowInsets = WindowInsets(0, 0, 0, 0),
        navigationIcon = {
            if (navigationIcon != null) {
                navigationIcon()
            } else if (onBackClick != null) {
                IconButton(onClick = onBackClick) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = backContentDescription
                    )
                }
            }
        },
        actions = actions
    )
}

@Preview(showBackground = true)
@Composable
private fun AppToolbarPreview() {
    ControldroidTheme(dynamicColor = false) {
        AppToolbar(title = "Paired Devices", onBackClick = {}) {
            TextButton(onClick = {}) { Text("Action") }
        }
    }
}
