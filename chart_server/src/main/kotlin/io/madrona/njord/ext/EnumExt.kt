package io.madrona.njord.ext

inline fun <reified T: Enum<T>> fromString(value: String?) : T? {
    return enumValues<T>().firstOrNull {
        value?.equals(it.name, ignoreCase = true) ?: false
    }
}

inline fun <reified A: Enum<A>, reified B: Enum<B>, T> letFromStrings(v1: String?, v2: String?, block: ((A, B) -> T)) : T? {
    return fromString<A>(v1)?.let { v1t ->
        fromString<B>(v2)?.let { v2r ->
            block(v1t, v2r)
        }
    }
}
