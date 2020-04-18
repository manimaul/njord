package io.madrona.njord

import io.madrona.njord.NmeaConst.maxNmeaLength
import io.madrona.njord.NmeaConst.nmeaBeginDollar
import io.madrona.njord.NmeaConst.nmeaBeginExclam
import io.madrona.njord.NmeaConst.nmeaRangeEnd
import io.madrona.njord.NmeaConst.nmeaRangeStart
import java.io.DataInputStream
import java.lang.NumberFormatException
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.charset.StandardCharsets


fun main(args: Array<String>) {
    Thread().run {
        val socket = Socket()
        socket.tcpNoDelay = false
        val host = args.firstOrNull() ?: "127.0.0.1"
        val port = args.lastOrNull()?.let {
            try {
                it.toInt()
            } catch (e : NumberFormatException) {
                null
            }
        } ?: 10110
        println("connecting to $host:$port")
        socket.connect(InetSocketAddress(host, port))
        socket.getOutputStream()
        val checksum = NmeaChecksum()
        val buffer = SentenceBuffer()
        DataInputStream(socket.getInputStream()).use {
            while (socket.isConnected) {
                val byte: Byte = it.readByte()
                if (byte == nmeaBeginDollar || byte == nmeaBeginExclam) {
                    val line = buffer.toString()
                    if (checksum.isValid(line)) {
                        println("\uD83D\uDE00 $line \uD83D\uDE00")
                    } else {
                        println("\uD83D\uDCA9 $line")
                    }
                    buffer.clear()
                }
                buffer.append(byte)
            }
        }
    }
    while (true) {
        Thread.sleep(1000)
    }
}

class SentenceBuffer {
    private val sentenceBuffer = ByteArray(maxNmeaLength)
    private var pos = 0
    fun append(byte: Byte) {
        if (byte in nmeaRangeStart..nmeaRangeEnd) {
            sentenceBuffer[pos] = byte
            pos++
            if (pos >= maxNmeaLength) {
                println("\uD83D\uDC7A OVERFLOW - resetting buffer")
                pos = 0
            }
        } else {
            println("\uD83D\uDC7E received invalid byte <${byte}>")
        }
    }

    fun clear() {
        pos = 0
    }

    override fun toString(): String {
        return String(sentenceBuffer, 0, pos, StandardCharsets.US_ASCII)
    }
}
