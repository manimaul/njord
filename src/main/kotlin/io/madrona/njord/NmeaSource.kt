package io.madrona.njord

import gnu.io.CommPortIdentifier
import gnu.io.SerialPort
import gnu.io.SerialPortEvent
import io.reactivex.Observable
import io.reactivex.ObservableEmitter
import io.reactivex.schedulers.Schedulers
import org.slf4j.LoggerFactory
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

class NmeaSource(
        private val name: String,
        private val baud: Int,
        private val nmeaChecksum: NmeaChecksum) {
    private val log = LoggerFactory.getLogger(NmeaSource::class.java)
    fun output(): Observable<String> {
        val meta = Meta()
        return Observable.create { emitter: ObservableEmitter<String> ->
                    try {
                        val portId = findPort(name)
                        meta.serialPort = portId?.open(this.javaClass.name, TIME_OUT) as SerialPort
                        meta.serialPort?.setSerialPortParams(
                                baud, SerialPort.DATABITS_8, SerialPort.STOPBITS_1, SerialPort.PARITY_NONE)
                        val input = BufferedReader(InputStreamReader(meta.serialPort?.inputStream))
                        meta.serialPort?.addEventListener { event: SerialPortEvent ->
                            if (event.eventType == SerialPortEvent.DATA_AVAILABLE) {
                                try {
                                    if (input.ready() && !emitter.isDisposed) {
                                        val line = input.readLine().strip()
                                        if (nmeaChecksum.isValid(line)) {
                                            emitter.onNext(line)
                                        } else {
                                            log.warn("invalid line checksum read from {}:{}", name, baud)
                                            log.warn("invalid line: {}", line)
                                        }
                                    }
                                } catch (e: Exception) {
                                    log.error("error serial event", e)
                                }
                            }
                        }
                        meta.serialPort?.notifyOnDataAvailable(true)
                    } catch (e: Exception) {
                        if (!emitter.isDisposed) {
                            emitter.onError(e)
                        }
                    }
                }
                .doOnDispose {
                    meta.serialPort?.close()
                    meta.input?.close()
                }
                .subscribeOn(Schedulers.newThread())
                .timeout(3, TimeUnit.SECONDS)
    }

    private fun findPort(name: String) : CommPortIdentifier? {
        return CommPortIdentifier.getPortIdentifiers().toList().mapNotNull {
            it as? CommPortIdentifier
        }.first {
            print("examining port name = ${it.name}")
            it.name == name
        }
    }

    private data class Meta(
        var serialPort: SerialPort? = null,
        var input: BufferedReader? = null
    )

    companion object {
        private const val TIME_OUT = 2000
    }
}
