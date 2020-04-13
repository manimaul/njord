package io.madrona.njord

import io.reactivex.Observable

interface NmeaSource {
    fun output(): Observable<String>
}
