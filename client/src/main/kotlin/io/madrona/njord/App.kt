package io.madrona.njord

import io.madrona.njord.di.injector


fun main(args: Array<String>) {
    val client = injector.nmeaClient()
    val checksum = injector.checksum()
    val host = args.firstOrNull() ?: "127.0.0.1"
    val port = args.lastOrNull()?.let {
        try {
            it.toInt()
        } catch (e : NumberFormatException) {
            null
        }
    } ?: 10110
    client.nmeaOverTcp(host, port, validateChecksum = false).subscribe {line ->
        if (checksum.isValid(line)) {
            println("\uD83D\uDE00 $line \uD83D\uDE00")
        } else {
            println("\uD83D\uDCA9 $line")
        }
    }
    while (true) {
        Thread.sleep(1000)
    }
}
