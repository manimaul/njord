package io.madrona.njord

inline fun <T, R> Iterable<T>.whenAny(predicate: (T) -> Boolean, perform: () -> R): R? {
    return if (any(predicate)) {
        perform()
    } else {
        null
    }
}
