package io.madrona.njord

import io.madrona.njord.di.injector
import io.reactivex.Observable
import java.net.InetSocketAddress
import javax.inject.Inject

class NmeaTcpSource(
        private val socketAddress: InetSocketAddress
) : NmeaSource {

    @Inject
    lateinit var client: NmeaClient

    init {
        injector.inject(this)
    }

    override fun output(): Observable<String> {
        return client.nmeaOverTcp(socketAddress)
    }
}