package io.madrona.njord

import io.reactivex.Completable
import io.reactivex.Observable
import io.reactivex.ObservableEmitter
import io.reactivex.ObservableSource
import io.reactivex.functions.Function
import io.reactivex.schedulers.Schedulers
import java.io.BufferedReader
import java.io.FileReader
import java.lang.RuntimeException
import java.util.concurrent.TimeUnit


class FakeNmeaSource(
        private val name: String
) : NmeaSource {
    private val log = logger()

    override fun output(): Observable<String> {
        return Observable.create { emitter: ObservableEmitter<String> ->
            javaClass.classLoader.getResource(name)?.file?.let { nmeaTxt ->
                try {
                    BufferedReader(FileReader(nmeaTxt)).use { reader ->
                        var line = reader.readLine()
                        while (line != null && !emitter.isDisposed) {
                            Thread.sleep(200)
                            log.info("nmea line = {}", line)
                            emitter.onNext(line)
                            line = reader.readLine()
                        }
                    }
                } catch (e: Exception) {
                    if (!emitter.isDisposed) {
                        emitter.onError(e)
                    }
                }
                if (!emitter.isDisposed) {
                    emitter.onError(RuntimeException("error flow control"))
                }
            }
        }.subscribeOn(Schedulers.io())
                .onErrorResumeNext(Function<Throwable, ObservableSource<String>> {
                    Completable.timer(3, TimeUnit.SECONDS).andThen(output())
                })
    }
}