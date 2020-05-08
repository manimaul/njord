package io.madrona.njord

import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import javax.inject.Inject

class LocalMachineIps @Inject constructor(private val njordConfig: NjordConfig) {

    val localSockets = findLocalSockets()

    private fun findLocalSockets(): Set<InetSocketAddress> {
        val local: Sequence<InetSocketAddress>
        val port = njordConfig.port
        val address = njordConfig.address
        local = if ("0.0.0.0" == address) {
            interfacesIpv4().map { InetSocketAddress(it.hostAddress, port) }
        } else {
            sequenceOf(InetSocketAddress(address, port))
        }
        return (local + sequenceOf(InetSocketAddress("localhost", port))).toSet()
    }

    private fun interfacesIpv4(): Sequence<InetAddress> {
        return NetworkInterface.getNetworkInterfaces().asSequence()
                .flatMap {
                    it.inetAddresses.asSequence()
                }
                .mapNotNull {
                    it.takeIf {
                        !it.hostAddress.contains(":")
                    }
                }
    }

}