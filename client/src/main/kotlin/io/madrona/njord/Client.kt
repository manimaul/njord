package io.madrona.njord

import java.io.DataInputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.charset.StandardCharsets

// Messages have a maximum length of 82 characters, including the $ or ! starting
// All transmitted data are printable ASCII characters between 0x20 (space) to 0x7e (~)
// https://en.wikipedia.org/wiki/NMEA_0183
private const val maxNmeaLength = 82
private const val nmeaBeginDollar = '$'.toByte() //0x/24
private const val nmeaBeginExclam = '!'.toByte() // 0x21
private const val nmeaRangeStart = ' '.toByte() // 0x20
private const val nmeaRangeEnd = '~'.toByte() // 0x7e


fun main() {
    Thread().run {
        val socket = Socket()
        socket.tcpNoDelay = false
        socket.connect(InetSocketAddress("192.168.86.31", 10110))
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
