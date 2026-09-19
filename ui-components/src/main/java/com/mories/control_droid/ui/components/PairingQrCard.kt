package com.mories.control_droid.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import com.mories.control_droid.ui.theme.ControldroidTheme

@Composable
fun PairingQrCard(
    bitmap: ImageBitmap,
    onRegenerate: () -> Unit,
    modifier: Modifier = Modifier,
    contentColor: Color = Color.Unspecified
) {
    val resolvedColor = if (contentColor == Color.Unspecified) {
        LocalContentColor.current
    } else {
        contentColor
    }
    CompositionLocalProvider(LocalContentColor provides resolvedColor) {
        Column(modifier = modifier) {
            Image(
                bitmap = bitmap,
                contentDescription = "QR pairing",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
            )
            Text("Scan QR ini dari Controller")
            TextButton(onClick = onRegenerate) {
                Text("Regenerate pairing token")
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun PairingQrCardPreview() {
    val image = remember { ImageBitmap(240, 240) }

    ControldroidTheme(dynamicColor = false) {
        PairingQrCard(bitmap = image, onRegenerate = {})
    }
}
