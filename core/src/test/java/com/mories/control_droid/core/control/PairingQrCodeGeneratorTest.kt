package com.mories.control_droid.core.control

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PairingQrCodeGeneratorTest {
    @Test
    fun create_returnsBitmapWithRequestedSizeAndQrContent() {
        val bitmap = PairingQrCodeGenerator.create("control-droid-pairing", size = 64)

        assertEquals(64, bitmap.width)
        assertEquals(64, bitmap.height)
        assertTrue(bitmap.pixels().toSet().size > 1)
    }

    private fun android.graphics.Bitmap.pixels(): IntArray {
        return IntArray(width * height).also { getPixels(it, 0, width, 0, 0, width, height) }
    }
}
