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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
class DeviceHttpClientTest {
    private lateinit var server: MockWebServer
    private lateinit var client: DeviceHttpClient

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        client = DeviceHttpClient(
            targetIp = "127.0.0.1",
            targetPort = server.port,
            pin = "1234",
            accessToken = "access-token",
            controllerId = "controller-uuid",
            controllerName = "Pixel 3a"
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun checkStatus_sendsCredentialsAndReportsAuthorization() {
        server.enqueue(MockResponse().setResponseCode(200))
        var connected = false
        val callbackCompleted = CountDownLatch(1)

        client.checkStatus {
            connected = it
            callbackCompleted.countDown()
        }

        val request = server.takeRequest()
        assertTrue(callbackCompleted.await(2, TimeUnit.SECONDS))
        assertEquals("/status", request.path)
        assertEquals("1234", request.getHeader("X-Control-Pin"))
        assertEquals("access-token", request.getHeader("X-Control-Token"))
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
    fun sendAction_sendsCommandAndBothCredentials() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("OK"))
        var success = false
        val callbackCompleted = CountDownLatch(1)

        client.sendAction(DeviceAction.GLOBAL_HOME) {
            success = it
            callbackCompleted.countDown()
        }

        val request = server.takeRequest()
        assertTrue(callbackCompleted.await(2, TimeUnit.SECONDS))
        assertEquals("/action", request.path)
        assertEquals("global_home", request.body.readUtf8())
        assertEquals("1234", request.getHeader("X-Control-Pin"))
        assertEquals("access-token", request.getHeader("X-Control-Token"))
        assertTrue(success)
    }

    @Test
    fun sendGesture_sendsJsonPayload() {
        server.enqueue(MockResponse().setResponseCode(200))

        client.sendGesture(
            GestureRequest(GestureType.SWIPE, .1f, .2f, .8f, .9f, 350)
        )

        val request = server.takeRequest()
        assertEquals("/gesture", request.path)
        assertTrue(request.body.readUtf8().contains("\"type\":\"SWIPE\""))
        assertEquals("access-token", request.getHeader("X-Control-Token"))
    }

    @Test
    fun sendClipboard_sendsTextAndPasteFlag() {
        server.enqueue(MockResponse().setResponseCode(200))

        client.sendClipboard(ClipboardRequest("hello", paste = true))

        val request = server.takeRequest()
        assertEquals("/clipboard", request.path)
        val body = request.body.readUtf8()
        assertTrue(body.contains("\"text\":\"hello\""))
        assertTrue(body.contains("\"paste\":true"))
    }

    @Test
    fun verifyPairing_sendsTokenAndControllerIdentityHeaders() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("{\"name\":\"ControlDroid\"}"))
        var result: Pair<Boolean, String?>? = null
        val callbackCompleted = CountDownLatch(1)

        client.verifyPairing("qr-token") { success, message ->
            result = success to message
            callbackCompleted.countDown()
        }

        val request = server.takeRequest()
        assertTrue(callbackCompleted.await(2, TimeUnit.SECONDS))
        assertEquals("/pair", request.path)
        assertEquals("qr-token", request.getHeader("X-Control-Token"))
        assertEquals("controller-uuid", request.getHeader("X-Controller-Id"))
        assertEquals("Pixel 3a", request.getHeader("X-Controller-Name"))
        assertTrue(result?.first == true)
    }

    @Test
    fun verifyPin_sendsPinHeaderToPairEndpoint() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("{\"name\":\"ControlDroid\"}"))
        var result: Pair<Boolean, String?>? = null
        val callbackCompleted = CountDownLatch(1)

        client.verifyPin("4321") { success, message ->
            result = success to message
            callbackCompleted.countDown()
        }

        val request = server.takeRequest()
        assertTrue(callbackCompleted.await(2, TimeUnit.SECONDS))
        assertEquals("/pair", request.path)
        assertEquals("4321", request.getHeader("X-Control-Pin"))
        assertEquals("controller-uuid", request.getHeader("X-Controller-Id"))
        assertEquals("Pixel 3a", request.getHeader("X-Controller-Name"))
        assertTrue(result?.first == true)
    }

    @Test
    fun verifyPin_reportsFailureOnUnauthorized() {
        server.enqueue(MockResponse().setResponseCode(401).setBody("Unauthorized"))
        var result: Pair<Boolean, String?>? = null
        val callbackCompleted = CountDownLatch(1)

        client.verifyPin("0000") { success, message ->
            result = success to message
            callbackCompleted.countDown()
        }

        assertTrue(callbackCompleted.await(2, TimeUnit.SECONDS))
        assertEquals(false, result?.first)
        assertEquals("Unauthorized", result?.second)
    }

    @Test
    fun fetchScreenshot_returnsStatusAndBytes() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("png-data"))

        val result = client.fetchScreenshot()

        assertEquals(200, result.statusCode)
        assertEquals("png-data", result.bytes?.toString(Charsets.UTF_8))
        assertEquals("/screenshot", server.takeRequest().path)
    }
}
