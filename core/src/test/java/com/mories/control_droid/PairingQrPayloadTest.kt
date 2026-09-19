package com.mories.control_droid

import com.mories.control_droid.core.model.PairingQrPayload
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class PairingQrPayloadTest {
    @Test
    fun encodeAndDecode_preservesPairingData() {
        val original = PairingQrPayload(
            name = "Target Living Room",
            ip = "192.168.1.42",
            port = 8080,
            token = "token-with-special_value"
        )

        val decoded = PairingQrPayload.decode(PairingQrPayload.encode(original))

        assertNotNull(decoded)
        assertEquals(original, decoded)
    }

    @Test
    fun decode_rejectsUnknownPayload() {
        assertEquals(null, PairingQrPayload.decode("not-a-control-droid-pairing-code"))
    }
}
