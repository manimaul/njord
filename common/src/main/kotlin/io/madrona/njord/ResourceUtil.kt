package io.madrona.njord

fun Any.resourceAsString(name: String) : String? {
    return javaClass.getResourceAsStream(name)?.let {
        it.bufferedReader(Charsets.UTF_8).use { reader ->
            reader.readText()
        }
    }
}
