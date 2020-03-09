package io.madrona.njord

import io.reactivex.Observable
import io.reactivex.ObservableEmitter
import io.reactivex.schedulers.Schedulers
import org.slf4j.LoggerFactory
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import javax.inject.Inject

class FakeNmeaSource @Inject constructor() {
    private val log = LoggerFactory.getLogger(FakeNmeaSource::class.java)

    fun fakeNmeaData(): Observable<String> {
        return Observable.create { emitter: ObservableEmitter<String> ->
                    val nmeaTxt = File(javaClass.classLoader.getResource("nmea.txt").file)
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
                        emitter.onComplete()
                    }
                }
                .subscribeOn(Schedulers.io())
    }
}