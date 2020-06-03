package io.madrona.njord

import io.madrona.njord.di.injector
import javax.inject.Inject

internal class ServerApp {
    @Inject lateinit var nmeaServer: NmeaServer

    init {
        injector.inject(this)
    }

    fun run() {
        nmeaServer.listenAndServeBlocking()
    }
}

fun main() {
    ServerApp().run()
}
