package com.mories.control_droid.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.mories.control_droid.ui.theme.ControldroidTheme

@Composable
fun RemotePreviewSurface(
    image: ImageBitmap,
    modifier: Modifier = Modifier,
    onTap: (x: Float, y: Float) -> Unit,
    onSwipe: (startX: Float, startY: Float, endX: Float, endY: Float) -> Unit
) {
    var dragStart by remember { mutableStateOf(Offset.Zero) }
    var dragEnd by remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier = modifier
            .pointerInput("tap") {
                detectTapGestures { offset ->
                    val width = size.width.coerceAtLeast(1)
                    val height = size.height.coerceAtLeast(1)
                    onTap(offset.x / width, offset.y / height)
                }
            }
            .pointerInput("drag") {
                detectDragGestures(
                    onDragStart = { offset ->
                        dragStart = offset
                        dragEnd = offset
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        dragEnd += dragAmount
                    },
                    onDragEnd = {
                        val width = size.width.coerceAtLeast(1)
                        val height = size.height.coerceAtLeast(1)
                        onSwipe(
                            dragStart.x / width,
                            dragStart.y / height,
                            dragEnd.x / width,
                            dragEnd.y / height
                        )
                    }
                )
            }
    ) {
        Image(
            bitmap = image,
            contentDescription = "Remote screen",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun RemotePreviewSurfacePreview() {
    val image = remember { ImageBitmap(240, 420) }

    ControldroidTheme(dynamicColor = false) {
        RemotePreviewSurface(
            image = image,
            modifier = Modifier.size(240.dp),
            onTap = { _, _ -> },
            onSwipe = { _, _, _, _ -> }
        )
    }
}
