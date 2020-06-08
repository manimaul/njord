package io.madrona.njord

fun Any.resourceString(path: String) : String? {
    return this::class.java.classLoader?.getResource(path)?.readText()
}