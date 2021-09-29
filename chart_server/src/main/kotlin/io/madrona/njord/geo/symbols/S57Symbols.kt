package io.madrona.njord.geo.symbols

typealias S57Prop = Map<String, Any>

inline fun <R : Any> S57Prop?.listFrom(key: String, transform: (Int) -> R?) : List<R> {
    return (this?.get(key) as? Array<*>)?.mapNotNull { it?.toString()?.toIntOrNull() }?.mapNotNull {
        transform(it)
    }?.toList() ?: emptyList()
}