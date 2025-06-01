package com.mories.control_droid.core.control

import android.util.Log
import com.mories.control_droid.core.model.PairedDevice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.UUID
import java.util.concurrent.TimeUnit

object DeviceScanner {
    suspend fun scanLocalDevices(port: Int = 8080): List<PairedDevice> = coroutineScope {
        val localIp = getLocalIp()?.also {
            Log.d("DeviceScanner", "Local IP: $it")
        } ?: run {
            Log.e("DeviceScanner", "Gagal dapatkan IP lokal.")
            return@coroutineScope emptyList()
        }

        val subnet = localIp.substringBeforeLast('.')
        Log.d("DeviceScanner", "Scanning subnet: $subnet.0/24")

        val deferreds = (1..254).map { host ->
            async(Dispatchers.IO) {
                val ip = "$subnet.$host"
                val url = "http://$ip:$port/ping"
                try {
                    val client =
                        OkHttpClient.Builder().callTimeout(300, TimeUnit.MILLISECONDS).build()
                    val request = Request.Builder().url(url).build()
                    val response = client.newCall(request).execute()

                    if (response.isSuccessful) {
                        val body = response.body?.string() ?: ""
                        Log.d("DeviceScanner", "[$ip] Response: $body")
                        if ("ControlDroid" in body) {
                            return@async PairedDevice(
                                id = UUID.randomUUID().toString(),
                                name = "Device-$host",
                                ip = ip,
                                pin = "",
                                lastConnected = System.currentTimeMillis()
                            )
                        }
                    }
                } catch (e: Exception) {
                    Log.d("DeviceScanner", "[$ip] Error: ${e.message}")
                }
                null
            }
        }
        val result = deferreds.awaitAll().filterNotNull()
        Log.d("DeviceScanner", "Scan complete. Found: ${result.size} devices")
        result
    }

    private fun getLocalIp(): String? {
        return try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            interfaces.toList().flatMap { it.inetAddresses.toList() }
                .firstOrNull { it is Inet4Address && !it.isLoopbackAddress }?.hostAddress
        } catch (_: Exception) {
            null
        }
    }
}