package io.madrona.njord.util

import io.madrona.njord.resources


fun Any.resourceAsString(name: String) : String? {
    return File("$resources/$name").takeIf { it.exists() }?.readContents()
}

fun Any.resourceAsLines(name: String) : List<String> {
    return resourceAsString(name)?.split("\n")?.map { it.trim() } ?: emptyList()
}

fun Any.resourceBytes(name: String) : ByteArray? {
    return File("$resources/$name").takeIf { it.exists() }?.readData()
}
