package io.madrona.njord

import io.madrona.njord.di.injector


fun main() {
    injector.nmeaServer.listenAndServeBlocking()
}
