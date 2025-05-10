package io.madrona.njord.ext

inline fun <T, R> Iterable<T>.whenAny(predicate: (T) -> Boolean, perform: () -> R): R? {
    return if (any(predicate)) {
        perform()
    } else {
        null
    }
}
