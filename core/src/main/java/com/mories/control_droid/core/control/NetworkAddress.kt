package com.mories.control_droid.core.control

import java.net.Inet4Address
import java.net.NetworkInterface

object NetworkAddress {
    fun localIpv4(): String? = runCatching {
        NetworkInterface.getNetworkInterfaces().toList()
            .flatMap { it.inetAddresses.toList() }
            .firstOrNull { it is Inet4Address && !it.isLoopbackAddress }
            ?.hostAddress
    }.getOrNull()
}
