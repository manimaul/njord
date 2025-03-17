package io.madrona.njord.util

import java.net.Inet4Address
import java.net.NetworkInterface

object NetworkUtil {
    fun guessExternalIP(): String {
        return NetworkInterface.getNetworkInterfaces().toList().filter { !it.isLoopback }.first().inetAddresses()
            .toList().filter { it is Inet4Address }.first().let {
                (it as Inet4Address).hostAddress
            }
    }
}