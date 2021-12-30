package io.madrona.njord.ext

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
