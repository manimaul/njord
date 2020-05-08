package io.madrona.njord

import io.reactivex.Completable
import io.reactivex.Observable
import io.reactivex.ObservableEmitter
import io.reactivex.ObservableSource
import io.reactivex.functions.Function
import io.reactivex.schedulers.Schedulers
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit


class FakeNmeaSource(
        private val name: String
) : NmeaSource {
    private val log = logger()

    override fun output(): Observable<String> {
        return Observable.create { emitter: ObservableEmitter<String> ->
            log.info("connecting to fake nmea resource: $name")
            javaClass.classLoader.getResourceAsStream(name)?.let { nmeaTxt ->
                try {
                    BufferedReader(InputStreamReader(nmeaTxt)).use { reader ->
                        var line = reader.readLine()
                        while (line != null && !emitter.isDisposed) {
                            Thread.sleep(90)
                            log.debug("nmea line = {}", line)
                            emitter.safeOnNext(line)
                            line = reader.readLine()
                        }
                    }
                } catch (e: Exception) {
                    emitter.safeOnError(e)
                }
                emitter.safeOnError(RuntimeException("error flow control"))
            }
        }.subscribeOn(Schedulers.io())
                .onErrorResumeNext(Function<Throwable, ObservableSource<String>> {
                    //log.error("error connecting to fake nmea resoure: $name", it)
                    Completable.timer(3, TimeUnit.SECONDS).andThen(output())
                })
    }
}
