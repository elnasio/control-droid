package com.mories.control_droid.core.networking

import com.mories.control_droid.core.model.ClipboardRequest
import com.mories.control_droid.core.model.DeviceAction
import com.mories.control_droid.core.model.GestureRequest
import com.mories.control_droid.core.model.GestureType
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
class InternetRelayClientTest {
    private lateinit var server: MockWebServer
    private lateinit var client: InternetRelayClient

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        client = InternetRelayClient(
            deviceId = "device-uuid",
            accessToken = "access-token",
            baseUrl = server.url("").toString().trimEnd('/')
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun checkStatus_routesThroughDevicePathWithTokenOnly() {
        server.enqueue(MockResponse().setResponseCode(200))
        var connected = false
        val callbackCompleted = CountDownLatch(1)

        client.checkStatus {
            connected = it
            callbackCompleted.countDown()
        }

        val request = server.takeRequest()
        assertTrue(callbackCompleted.await(2, TimeUnit.SECONDS))
        assertEquals("/v1/devices/device-uuid/status", request.path)
        assertEquals("Bearer access-token", request.getHeader("Authorization"))
        // The relay never accepts the legacy PIN — only an access token is strong enough to be
        // internet-facing (see docs/backend-requirements.md §1.1) — so no PIN header is ever sent.
        assertNull(request.getHeader("X-Control-Pin"))
        assertTrue(connected)
    }

    @Test
    fun checkStatus_reportsDisconnectedOnUnauthorized() {
        server.enqueue(MockResponse().setResponseCode(401))
        var connected = true
        val callbackCompleted = CountDownLatch(1)

        client.checkStatus {
            connected = it
            callbackCompleted.countDown()
        }

        assertTrue(callbackCompleted.await(2, TimeUnit.SECONDS))
        assertFalse(connected)
    }

    @Test
    fun checkStatus_reportsDisconnectedWhenRelayUnreachable() {
        server.shutdown()
        var connected = true
        val callbackCompleted = CountDownLatch(1)

        client.checkStatus {
            connected = it
            callbackCompleted.countDown()
        }

        assertTrue(callbackCompleted.await(2, TimeUnit.SECONDS))
        assertFalse(connected)
    }

    @Test
    fun sendAction_postsJsonCommandBody() {
        server.enqueue(MockResponse().setResponseCode(200))
        var success = false
        val callbackCompleted = CountDownLatch(1)

        client.sendAction(DeviceAction.GLOBAL_HOME) {
            success = it
            callbackCompleted.countDown()
        }

        val request = server.takeRequest()
        assertTrue(callbackCompleted.await(2, TimeUnit.SECONDS))
        assertEquals("/v1/devices/device-uuid/action", request.path)
        assertTrue(request.body.readUtf8().contains("\"command\":\"global_home\""))
        assertTrue(success)
    }

    @Test
    fun sendGesture_postsJsonPayload() {
        server.enqueue(MockResponse().setResponseCode(200))

        client.sendGesture(GestureRequest(GestureType.SWIPE, .1f, .2f, .8f, .9f, 350))

        val request = server.takeRequest()
        assertEquals("/v1/devices/device-uuid/gesture", request.path)
        assertTrue(request.body.readUtf8().contains("\"type\":\"SWIPE\""))
    }

    @Test
    fun sendClipboard_postsTextAndPasteFlag() {
        server.enqueue(MockResponse().setResponseCode(200))

        client.sendClipboard(ClipboardRequest("hello", paste = true))

        val request = server.takeRequest()
        assertEquals("/v1/devices/device-uuid/clipboard", request.path)
        val body = request.body.readUtf8()
        assertTrue(body.contains("\"text\":\"hello\""))
        assertTrue(body.contains("\"paste\":true"))
    }

    @Test
    fun fetchScreenshot_returnsStatusAndBytes() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("png-data"))

        val result = client.fetchScreenshot()

        assertEquals(200, result.statusCode)
        assertEquals("png-data", result.bytes?.toString(Charsets.UTF_8))
        assertEquals("/v1/devices/device-uuid/screenshot", server.takeRequest().path)
    }
}
