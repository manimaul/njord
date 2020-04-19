package io.madrona.njord

import gnu.io.CommPortIdentifier
import gnu.io.SerialPort
import gnu.io.SerialPortEvent
import io.madrona.njord.di.injector
import io.reactivex.Observable
import io.reactivex.ObservableEmitter
import io.reactivex.ObservableSource
import io.reactivex.functions.Function
import io.reactivex.schedulers.Schedulers
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.*
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class NmeaSerialSource(
        private val name: String
) : NmeaSource {

    @Inject
    lateinit var nmeaChecksum: NmeaChecksum

    @Inject
    lateinit var njordConfig: NjordConfig

    init {
        injector.inject(this)
    }

    private val log = logger()

    override fun output(): Observable<String> {
        return output(LinkedList(njordConfig.bauds))
    }

    /**
     * @param tryBauds serial port buad rates to attempt to use.
     */
    private fun output(tryBauds: Queue<Int>): Observable<String> {
        return if (tryBauds.isNotEmpty()) {
            output(tryBauds.poll()).onErrorResumeNext(Function<Throwable, ObservableSource<String>> {
                output(tryBauds)
            })
        } else {
            log.info("exhausted all configured bauds for source=${name}")
            Observable.error(RuntimeException())
        }
    }

    private fun output(baud: Int): Observable<String> {
        val meta = Meta()
        log.info("attempting to read nmea data from port name=${name} at baud=${baud}")
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
                                val line = input.readLine().trim()
                                if (nmeaChecksum.isValid(line)) {
                                    emitter.safeOnNext(line)
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
        }.doOnDispose {
            meta.serialPort?.close()
            meta.input?.close()
        }.subscribeOn(Schedulers.newThread())
                .timeout(3, TimeUnit.SECONDS)
    }

    private fun findPort(name: String): CommPortIdentifier? {
        return CommPortIdentifier.getPortIdentifiers().toList().mapNotNull {
            it as? CommPortIdentifier
        }.first {
            print("examining port name = ${it.name}")
            it.name == name
        }
    }

    override fun toString(): String {
        return "NmeaSource(name='$name')"
    }

    private data class Meta(
            var serialPort: SerialPort? = null,
            var input: BufferedReader? = null
    )

    companion object {
        private const val TIME_OUT = 2000
    }
}
