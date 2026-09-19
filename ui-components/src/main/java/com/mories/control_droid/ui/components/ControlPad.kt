package com.mories.control_droid.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.mories.control_droid.ui.theme.ControldroidTheme

enum class ControlPadDirection {
    UP,
    DOWN,
    LEFT,
    RIGHT
}

@Composable
fun ControlPad(
    onDirectionClick: (ControlPadDirection) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        DirectionButton(
            direction = ControlPadDirection.UP,
            enabled = enabled,
            onClick = onDirectionClick
        )
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            DirectionButton(
                direction = ControlPadDirection.LEFT,
                enabled = enabled,
                onClick = onDirectionClick
            )
            Surface(
                modifier = Modifier.size(56.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Spacer(modifier = Modifier.size(56.dp))
            }
            DirectionButton(
                direction = ControlPadDirection.RIGHT,
                enabled = enabled,
                onClick = onDirectionClick
            )
        }
        DirectionButton(
            direction = ControlPadDirection.DOWN,
            enabled = enabled,
            onClick = onDirectionClick
        )
    }
}

@Composable
private fun DirectionButton(
    direction: ControlPadDirection,
    enabled: Boolean,
    onClick: (ControlPadDirection) -> Unit
) {
    val (icon, description) = when (direction) {
        ControlPadDirection.UP -> Icons.Default.KeyboardArrowUp to "Move up"
        ControlPadDirection.DOWN -> Icons.Default.KeyboardArrowDown to "Move down"
        ControlPadDirection.LEFT -> Icons.AutoMirrored.Filled.KeyboardArrowLeft to "Move left"
        ControlPadDirection.RIGHT -> Icons.AutoMirrored.Filled.KeyboardArrowRight to "Move right"
    }

    IconButton(
        onClick = { onClick(direction) },
        enabled = enabled,
        modifier = Modifier.size(56.dp)
    ) {
        Icon(imageVector = icon, contentDescription = description)
    }
}

@Preview(showBackground = true)
@Composable
private fun ControlPadPreview() {
    ControldroidTheme(dynamicColor = false) {
        ControlPad(onDirectionClick = {})
    }
}
