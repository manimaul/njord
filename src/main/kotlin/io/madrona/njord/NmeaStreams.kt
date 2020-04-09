package io.madrona.njord

import io.reactivex.Observable
import io.reactivex.subjects.PublishSubject
import java.lang.Integer.min
import java.util.*
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NmeaStreams @Inject constructor(
        val njordConfig: NjordConfig
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
                    source.output(LinkedList(njordConfig.bauds))
                }
                .subscribe({
                    nmeaSubject.onNext(it.toByteArray())
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
                    NmeaSource(port)
                }
                .forEach { source: NmeaSource ->
                    connect(source, 1)
                }
    }
}