package io.madrona.njord

import io.madrona.njord.NmeaConst.nmeaBeginDollar
import io.madrona.njord.NmeaConst.nmeaBeginExclam
import io.reactivex.Completable
import io.reactivex.Observable
import io.reactivex.ObservableSource
import io.reactivex.functions.Function
import io.reactivex.schedulers.Schedulers
import java.io.DataInputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class NmeaClient @Inject constructor(
        private val checksum: NmeaChecksum
) {
    fun nmeaOverTcp(socketAddress: InetSocketAddress,
                    validateChecksum: Boolean = false): Observable<String> {
        return nmeaOverTcp(socketAddress.hostName, socketAddress.port, validateChecksum)
    }

    private val log = logger()

    fun nmeaOverTcp(host: String,
                            port: Int,
                            validateChecksum: Boolean = false): Observable<String> {
        val socket = Socket()
        return Observable.create<String> { emitter ->
            val buffer = SentenceBuff()
            try {
                log.info("connecting to <$host:$port>")
                socket.connect(InetSocketAddress(host, port))
                socket.tcpNoDelay = false
                DataInputStream(socket.inputStream).use { dataStream ->
                    while (socket.isConnected && !emitter.isDisposed) {
                        val byte: Byte = dataStream.readByte()
                        if (byte == nmeaBeginDollar || byte == nmeaBeginExclam) {
                            val line = buffer.toString()
                            if (!validateChecksum || checksum.isValid(line)) {
                                emitter.safeOnNext(line)
                            }
                            buffer.clear()
                        }
                        buffer.append(byte)
                    }
                }
            } catch (e: Exception) {
                emitter.safeOnError(e)
            }
        }.subscribeOn(Schedulers.io())
                .doOnDispose {
                    log.info("dispose - closing socket <$host:$port>")
                    socket.close()
                }
                .doOnComplete {
                    log.info("complete - closing socket <$host:$port>")
                    socket.close()
                }
                .timeout(5, TimeUnit.SECONDS)
                .onErrorResumeNext(
                        Function<Throwable, ObservableSource<String>> {
                            log.info("error - closing socket <$host:$port>")
                            socket.close()
                            log.info("error - reconnecting to <$host:$port> in 3 seconds")
                            Completable.timer(3, TimeUnit.SECONDS).andThen(nmeaOverTcp(host, port))
                        }
                )
    }
}

private class SentenceBuff {
    private val sentenceBuffer = ByteArray(NmeaConst.maxNmeaLength)
    private var pos = 0
    private val log = logger()

    fun append(byte: Byte) {
        if (byte in NmeaConst.nmeaRangeStart..NmeaConst.nmeaRangeEnd) {
            sentenceBuffer[pos] = byte
            pos++
            if (pos >= NmeaConst.maxNmeaLength) {
                log.info("\uD83D\uDC7A OVERFLOW - resetting buffer")
                pos = 0
            }
        } else {
            log.info("\uD83D\uDC7E received invalid byte <${byte}>")
        }
    }

    fun clear() {
        pos = 0
    }

    override fun toString(): String {
        return String(sentenceBuffer, 0, pos, StandardCharsets.US_ASCII)
    }
}