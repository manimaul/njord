package io.madrona.njord

import io.reactivex.ObservableEmitter

fun <T> ObservableEmitter<T>.safeOnNext(value: T) {
    if (!isDisposed) {
        onNext(value)
    }
}

fun <T> ObservableEmitter<T>.safeOnError(err: Throwable) {
    if (!isDisposed) {
        onError(err)
    }
}