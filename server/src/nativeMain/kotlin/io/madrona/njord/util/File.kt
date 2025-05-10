package io.madrona.njord.util

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.refTo
import kotlinx.cinterop.toKString
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import platform.posix.SEEK_END
import platform.posix.SEEK_SET
import platform.posix.fclose
import platform.posix.fgets
import platform.posix.fopen
import platform.posix.fread
import platform.posix.fseek
import platform.posix.ftell

@OptIn(ExperimentalForeignApi::class)
class File(val path: Path) {

    constructor(path: String) : this(SystemFileSystem.resolve(Path(path)))
    constructor(parent: String, path: String) : this(SystemFileSystem.resolve(Path(parent, path)))
    constructor(parent: File, path: String) : this(SystemFileSystem.resolve(Path(parent.path, path)))

    fun exists(): Boolean {
        return SystemFileSystem.exists(path)
    }

    val name: String = TODO()
    fun listFiles() : List<File> {
       TODO()
    }

    fun mkdirs() : Boolean {
        TODO()
    }

    fun writeBytes(data: ByteArray) {
        TODO()
    }

    fun deleteRecursively() {

    }

    fun readData(): ByteArray {
        val file = fopen(path.toString(), "rb") ?: throw IllegalArgumentException("Cannot open input file $path")
        var buffer: ByteArray? = null
        try {
            memScoped {
                if (fseek(file, 0, SEEK_END) == 0) {
                    val size = ftell(file)
                    if (size > 0) {
                        if (fseek(file, 0, SEEK_SET) == 0) {
                            buffer = ByteArray(size.toInt())
                            if (fread(buffer.refTo(0), 1.toULong(), size.toULong(), file) != size.toULong()) {
                                buffer = null
                            }
                        }
                    }
                }
            }
        } finally {
            fclose(file)
        }
        return buffer ?: ByteArray(0)
    }

    fun readContents(): String {
        val sb = StringBuilder()
        val file = fopen(path.toString(), "r") ?: throw IllegalArgumentException("Cannot open input file $path")
        try {
            memScoped {
                val readLength = 1024
                val buffer = allocArray<ByteVar>(readLength)
                var line = fgets(buffer, readLength, file)?.toKString()
                while (line != null) {
                    sb.append(line)
                    line = fgets(buffer, readLength, file)?.toKString()
                }
            }
        } finally {
            fclose(file)
        }
        return sb.toString()
    }

    override fun toString(): String {
        return path.toString()
    }
}