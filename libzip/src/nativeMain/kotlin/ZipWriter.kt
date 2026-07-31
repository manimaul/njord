@file:OptIn(ExperimentalForeignApi::class)

import kotlinx.cinterop.*
import libzip.*

enum class ZipCompression {
    /** No compression. Only worth it when the inputs are already compressed. */
    Store,

    /** Deflate at level 1 - most of the ratio for a fraction of level 6's CPU. */
    DeflateFast,

    /** Deflate at libzip's default level. */
    DeflateDefault,
}

class ZipWriteException(message: String) : RuntimeException(message)

/**
 * Creates zip archives. Complements the read-only [ZipFile]; the two are independent.
 *
 * ```
 * ZipWriter.create(File(dir, "bundle.zip")).use { w ->
 *     w.addFile("ENC_ROOT/US5WA22M/US5WA22M.000", src)
 * }   // <-- sources are read HERE
 * ```
 *
 * **The sources are read lazily, during [close].** `zip_source_file` only records the path;
 * libzip opens and reads each file while writing the archive out. Every file passed to [addFile]
 * must therefore still exist, be readable, and be unmodified when [close] is called. Staging
 * directories must be deleted *after* [close] returns, never between adds.
 *
 * Deliberately not using `createCleaner` the way [ZipFile] does: `zip_close` is where all the
 * real work happens and where it can fail, and a cleaner would both swallow that error and risk
 * touching an already freed archive.
 *
 * Future optimisation, not implemented: `zip_source_zip` can copy an entry's raw deflate stream
 * from one archive to another with no decompress/recompress and no staging to disk. It is
 * avoided here because its behaviour has shifted across the libzip versions this project builds
 * against (1.7 dev boxes, 1.9 in the Debian container, 1.10 for the deb, 1.11 on brew).
 */
class ZipWriter private constructor(
    private val archive: CPointer<zip_t>,
    private val path: String,
) : AutoCloseable {

    companion object {
        /** Creates (or truncates) the archive at [file]. Parent directories must already exist. */
        fun create(file: File): ZipWriter {
            val path = file.getAbsolutePath().toString()
            val archive = memScoped {
                val errp = alloc<IntVar>()
                zip_open(path, ZIP_CREATE or ZIP_TRUNCATE, errp.ptr)
                    ?: throw ZipWriteException("cannot create zip '$path': ${errorText(errp.value)}")
            }
            return ZipWriter(archive, path)
        }

        /** `zip_open` reports failure as a bare int; turn it into something an operator can read. */
        private fun errorText(code: Int): String = memScoped {
            val err = alloc<zip_error_t>()
            zip_error_init_with_code(err.ptr, code)
            val text = zip_error_strerror(err.ptr)?.toKString() ?: "libzip error $code"
            zip_error_fini(err.ptr)
            text
        }
    }

    private var closed = false
    private var added = 0

    /** Number of entries staged so far. They are only written to disk by [close]. */
    val entryCount: Int
        get() = added

    /**
     * Stages [source] to be stored in the archive as [entryName].
     *
     * [source] must still exist and be unmodified when [close] runs - see the class docs.
     * Returns the entry index.
     */
    fun addFile(
        entryName: String,
        source: File,
        compression: ZipCompression = ZipCompression.DeflateFast,
    ): Long {
        check(!closed) { "ZipWriter already closed: $path" }
        require(source.exists()) { "source does not exist: ${source.path}" }

        // start = 0, len = -1 -> the whole file
        val src = zip_source_file(archive, source.getAbsolutePath().toString(), 0u, -1L)
            ?: throw ZipWriteException("zip_source_file('${source.path}') failed: ${lastError()}")

        val index = zip_file_add(archive, entryName, src, ZIP_FL_ENC_UTF_8 or ZIP_FL_OVERWRITE)
        if (index < 0) {
            // On failure ownership stays with us. On success libzip owns the source and freeing
            // it here would be a double free.
            zip_source_free(src)
            throw ZipWriteException("zip_file_add('$entryName') failed: ${lastError()}")
        }

        when (compression) {
            ZipCompression.Store ->
                zip_set_file_compression(archive, index.toULong(), ZIP_CM_STORE, 0u)

            ZipCompression.DeflateFast ->
                zip_set_file_compression(archive, index.toULong(), ZIP_CM_DEFLATE, 1u)

            ZipCompression.DeflateDefault -> Unit
        }

        added++
        return index
    }

    /**
     * Adds an explicit directory entry. Rarely needed - readers create parent directories from
     * file entry paths, and NOAA's own ENC archives contain no directory entries at all.
     */
    fun addDirectory(entryName: String) {
        check(!closed) { "ZipWriter already closed: $path" }
        if (zip_dir_add(archive, entryName.trimEnd('/'), ZIP_FL_ENC_UTF_8) < 0) {
            throw ZipWriteException("zip_dir_add('$entryName') failed: ${lastError()}")
        }
    }

    /** Throws away everything staged and frees the archive without writing it. */
    fun discard() {
        if (closed) return
        closed = true
        zip_discard(archive)
    }

    /**
     * Writes the archive out. This is where every staged source file is actually read, so this is
     * where out-of-space, permission and vanished-source errors surface.
     */
    override fun close() {
        if (closed) return
        closed = true
        if (zip_close(archive) != 0) {
            val message = lastError()
            // zip_close failed, so the archive is still live and still ours to free.
            zip_discard(archive)
            throw ZipWriteException("zip_close('$path') failed: $message")
        }
        // zip_close succeeded: `archive` is now dangling and must never be touched again.
    }

    private fun lastError(): String =
        zip_error_strerror(zip_get_error(archive))?.toKString() ?: "unknown libzip error"
}
