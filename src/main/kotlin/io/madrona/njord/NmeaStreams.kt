package io.madrona.njord

import io.reactivex.Observable
import io.reactivex.subjects.PublishSubject
import java.lang.Integer.min
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NmeaStreams @Inject constructor(
        njordConfig: NjordConfig,
        private val nmeaChecksum: NmeaChecksum
) {
    private val log = logger()
    private val nmeaSubject = PublishSubject.create<ByteArray>()

    fun nmeaData(): Observable<ByteArray> {
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
                    nmeaSubject.onNext(it.toByteArray())
                }, {
                    log.error("nmea source {} error {}", source, it)
                    connect(source, sec * 2)
                }, {
                    log.info("nmea source {} complete", source)
                })
    }

    init {
        njordConfig.commPorts
                .stream()
                .flatMap { port: String ->
                    njordConfig.bauds
                            .stream()
                            .map { baud: Int ->
                                NmeaSource(port, baud, nmeaChecksum)
                            }
                }
                .forEach { source: NmeaSource ->
                    connect(source, 1)
                }
    }
}