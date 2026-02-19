package io.madrona.njord.util

import File
import io.madrona.njord.resources
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.toKString
import libgdal.realpath
import platform.posix.free


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
    val resDir = getRealPath(resources)
    val resFile = getRealPath("$resources/$name")
    return if (resFile.startsWith(resDir)) {
        File(resFile).takeIf { it.exists() && it.isFile() }
    } else {
        println("Attack attempt detected: $name")
        null
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun getRealPath(userInput: String): String {
    val realPathPtr = requireNotNull(realpath(userInput, null))
    val realPath = realPathPtr.toKString()
    free(realPathPtr)
    return realPath
}