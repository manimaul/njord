package io.madrona.njord.util

import File
import io.madrona.njord.resources
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.toKString
import platform.posix.free
import platform.posix.realpath


fun resourceAsString(name: String) : String? {
    return resourceFile(name)?.readContents()
}

fun resourceAsLines(name: String) : List<String> {
    return resourceAsString(name)?.split("\n")?.map { it.trim() } ?: emptyList()
}

fun resourceBytes(name: String) : ByteArray? {
    return resourceFile(name)?.readData()
}

fun resourceFile(name: String) : File? {
    val resDir = getRealPath(resources) ?: return null
    val resFile = getRealPath("$resources/$name") ?: return null
    return if (resFile.startsWith(resDir)) {
        File(resFile).takeIf { it.exists() && it.isFile() }
    } else {
        println("Attack attempt detected: $name")
        null
    }
}

/** The canonical path, or null when it does not exist (a missing resource is not an error). */
@OptIn(ExperimentalForeignApi::class)
private fun getRealPath(userInput: String): String? {
    val realPathPtr = realpath(userInput, null) ?: return null
    val realPath = realPathPtr.toKString()
    free(realPathPtr)
    return realPath
}