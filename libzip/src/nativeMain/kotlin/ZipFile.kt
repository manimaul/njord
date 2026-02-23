@file:OptIn(ExperimentalForeignApi::class, ExperimentalNativeApi::class)

import kotlinx.cinterop.*
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.ref.createCleaner
import libzip.*
import platform.posix.fclose
import platform.posix.fopen
import platform.posix.fwrite

class ZipFile(file: File) {
    private val archive: CPointer<zip_t> = memScoped {
        val errp = alloc<IntVar>()
        zip_open(file.path.toString(), ZIP_RDONLY, errp.ptr)
            ?: error("Cannot open zip '${file.path}' (error code: ${errp.value})")
    }

    @Suppress("unused")
    private val cleaner = createCleaner(archive) { zip_close(it) }

    fun size(): Long = zip_get_num_entries(archive, 0u)

    fun entries(): List<ZipFileEntry> {
        return (0L until size()).map { i ->
            ZipFileEntry(archive, i.toULong())
        }
    }
}

class ZipFileEntry(
    private val archive: CPointer<zip_t>,
    private val index: ULong,
) {

    fun isDirectory(): Boolean = name().endsWith("/")

    fun name(): String {
        return zip_get_name(archive, index, 0u)?.toKString()
            ?: error("Cannot get name for zip entry $index")
    }

    fun readFileChunked(chunkSize: Int = 8192, block: (ByteArray, Int) -> Unit) {
        val zipFile = zip_fopen_index(archive, index, 0u)
            ?: error("Cannot open zip entry $index (${name()})")
        try {
            val buffer = ByteArray(chunkSize)
            buffer.usePinned { pinned ->
                while (true) {
                    val n = zip_fread(zipFile, pinned.addressOf(0), chunkSize.toULong())
                    if (n <= 0L) break
                    block(buffer, n.toInt())
                }
            }
        } finally {
            zip_fclose(zipFile)
        }
    }

    fun unzipToPath(dir: File) {
        val name = name()
        if (isDirectory()) {
            File(dir, name).mkdirs()
        } else {
            val f = File(dir, name)
            f.parentFile()?.mkdirs()
            val path = f.getAbsolutePath().toString()
            val fp = fopen(path, "wb") ?: error("Cannot open destination: $path")
            try {
                readFileChunked { buffer, count ->
                    buffer.usePinned { pinned ->
                        fwrite(pinned.addressOf(0), 1u, count.toULong(), fp)
                    }
                }
            } finally {
                fclose(fp)
            }
        }
    }
}
