package io.madrona.njord

import com.willkamp.vial.api.ServerInitializer
import io.madrona.njord.di.injector
import javax.inject.Inject

internal class ServerApp {
    @Inject lateinit var nmeaServer: ServerInitializer

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
