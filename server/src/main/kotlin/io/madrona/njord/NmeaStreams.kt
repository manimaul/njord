package io.madrona.njord

import io.reactivex.Observable
import io.reactivex.subjects.PublishSubject
import java.lang.Integer.min
import java.net.InetSocketAddress
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

private const val resPrefix = "resource/"

@Singleton
class NmeaStreams @Inject constructor(
        njordConfig: NjordConfig
) {
    private val log = logger()
    private val nmeaSubject = PublishSubject.create<String>()

    fun nmeaData(): Observable<String> {
        return nmeaSubject.hide()
    }

    private fun connect(source: NmeaSource, seconds: Int) {
        val sec = min(15, seconds)
        log.info("connecting to nmea source {} in {} seconds", source, sec)
        Observable.timer(sec.toLong(), TimeUnit.SECONDS)
                .flatMap {
                    source.output()
                }
                .subscribe({
                    nmeaSubject.onNext(it)
                }, {
                    log.error("nmea source error {}", source)
                    connect(source, sec * 2)
                }, {
                    log.info("nmea source {} complete", source)
                })
    }

    init {
        njordConfig.commPorts
                .stream()
                .map { port: String ->
                    if (port.startsWith(resPrefix)) {
                        FakeNmeaSource(port.substring(resPrefix.length))
                    } else {
                        NmeaSerialSource(port)
                    }
                }
                .forEach { source: NmeaSource ->
                    connect(source, 1)
                }

        njordConfig.tcpNmeaRelay
                .stream()
                .map { address: InetSocketAddress ->
                    NmeaTcpSource(address)
                }.forEach { source: NmeaSource ->
                    connect(source, 1)
                }
    }
}