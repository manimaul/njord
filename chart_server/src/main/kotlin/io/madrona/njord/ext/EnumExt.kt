package io.madrona.njord.ext

inline fun <reified T: Enum<T>> fromString(value: String?) : T? {
    return enumValues<T>().firstOrNull {
        value?.equals(it.name, ignoreCase = true) ?: false
    }
}

inline fun <A, B, R> letTwo(v1: A?, v2: B?, block: (A, B) -> R) : R? {
    return if (v1 != null && v2 != null) {
        block(v1, v2)
    } else {
        null
    }
}

inline fun <A, B, C, R> letThree(v1: A?, v2: B?, v3: C?, block: (A, B, C) -> R) : R? {
    return if (v1 != null && v2 != null && v3 != null) {
        block(v1, v2, v3)
    } else {
        null
    }
}

inline fun <reified A: Enum<A>, reified B: Enum<B>, T> letFromStrings(v1: String?, v2: String?, block: ((A, B) -> T)) : T? {
    return fromString<A>(v1)?.let { v1t ->
        fromString<B>(v2)?.let { v2r ->
            block(v1t, v2r)
        }
    }
}