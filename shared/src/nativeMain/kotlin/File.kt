import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.refTo
import kotlinx.cinterop.toCValues
import kotlinx.cinterop.toKString
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import platform.posix.PATH_MAX
import platform.posix.SEEK_END
import platform.posix.SEEK_SET
import platform.posix.S_IFDIR
import platform.posix.S_IFMT
import platform.posix.S_IFREG
import platform.posix.S_IRWXU
import platform.posix.fclose
import platform.posix.fopen
import platform.posix.fread
import platform.posix.fseek
import platform.posix.ftell
import platform.posix.fwrite
import platform.posix.getcwd
import platform.posix.mkdir
import platform.posix.realpath
import platform.posix.remove
import platform.posix.rename
import platform.posix.rmdir
import platform.posix.stat

enum class Charset {
    US_ASCII,
    ISO_8859_1,
    UTF_8
}

@OptIn(ExperimentalForeignApi::class)
class File(val path: Path) {

    constructor(path: String) : this(Path(path))
    constructor(parent: String, path: String) : this(Path(parent, path))
    constructor(parent: File, path: String) : this(Path(parent.path, path))

    companion object {
        private const val SEPARATOR = "/"
        private const val MAX_PATH_LENGTH = PATH_MAX

        /**
         * Get the current working directory
         */
        fun getCurrentDirectory(): File? {
            return memScoped {
                val buffer = allocArray<ByteVar>(MAX_PATH_LENGTH)
                val result = getcwd(buffer, MAX_PATH_LENGTH.toULong())
                result?.toKString()?.let { File(it) }
            }
        }
    }

    fun parentFile(): File? = path.parent?.let { File(it) }

    /**
     * Get the absolute path of this file
     * Resolves relative paths and normalizes the path
     */
    fun getAbsolutePath(): Path {
        return if (exists()) {
            SystemFileSystem.resolve(path)
        } else {
            realpath(path.toString(), null)?.toKString()?.let { Path(it) }
        } ?: normalizePath()
    }

    /**
     * Get just the filename (last component of the path)
     */
    fun getName(): String {
        val lastSeparator = path.toString().lastIndexOf(SEPARATOR)
        return if (lastSeparator >= 0) {
            path.toString().substring(lastSeparator + 1)
        } else {
            path.toString()
        }
    }


    /**
     * Check if this represents a directory
     */
    fun isDirectory(): Boolean {
        return memScoped {
            val stats = alloc<stat>()
            if (stat(getAbsolutePath().toString(), stats.ptr) == 0) {
                (stats.st_mode.toInt() and S_IFMT) == S_IFDIR
            } else {
                false
            }
        }
    }

    /**
     * Check if this represents a regular file
     */
    fun isFile(): Boolean {
        return memScoped {
            val stats = alloc<stat>()
            if (stat(getAbsolutePath().toString(), stats.ptr) == 0) {
                (stats.st_mode.toInt() and S_IFMT) == S_IFREG
            } else {
                false
            }
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is File) return false
        return getAbsolutePath() == other.getAbsolutePath()
    }

    override fun hashCode(): Int {
        return getAbsolutePath().hashCode()
    }

    /**
     * Check if the file exists
     */
    fun exists(): Boolean {
        return SystemFileSystem.exists(path)
    }

    val name: String = path.name

    fun listFiles(recursive: Boolean): List<File> {
        if (isDirectory()) {
            return SystemFileSystem.list(path).flatMap {
                val f = File(it)
                if (f.isDirectory() && f.exists() && recursive) {
                    f.listFiles(recursive) + listOf(f)
                } else if (f.isFile()) {
                    listOf(f)
                } else {
                    emptyList()
                }
            }
        }
        return emptyList()
    }

    fun listDirs(): List<File> {
        if (isDirectory()) {
            return SystemFileSystem.list(path).mapNotNull {
                val f = File(it)
                if (f.isDirectory()) f else null
            }
        }
        return emptyList()
    }

    fun normalizePath(): Path {
        val p = path.toString()
        val isAbsolute = p.startsWith(SEPARATOR)
        val components = p.split(SEPARATOR).filter { it.isNotEmpty() }
        val stack = mutableListOf<String>()
        for (component in components) {
            when (component) {
                "." -> continue
                ".." -> {
                    if (stack.isNotEmpty()) {
                        stack.removeLast()
                    } else if (!isAbsolute) {
                        stack.add("..")
                    }
                }

                else -> stack.add(component)
            }
        }
        return if (isAbsolute) {
            Path(SEPARATOR + stack.joinToString(SEPARATOR))
        } else {
            getCurrentDirectory()?.let { cwd ->
                Path(cwd.path, stack.joinToString(SEPARATOR))
            } ?: throw IllegalStateException("could not normalize path")
        }
    }

    fun isEmpty() : Boolean {
        return listFiles(false).isEmpty()
    }

    fun mkdirs(): Boolean {
        if (isDirectory()) {
            return true
        }
        var p = ""
        normalizePath().toString().split(SEPARATOR).filter { it.isNotBlank() }.forEach {
            p += "/$it"
            if (p.isNotEmpty() && !SystemFileSystem.exists(Path(p))) {
                if (mkdir(p, S_IRWXU.toUInt()) != 0) {
                    return false
                }
            }
        }
        return true
    }

    fun write(data: String): Boolean {
        return writeBytes(data.encodeToByteArray())
    }

    fun touch() {
        writeBytes(ByteArray(0))
    }

    fun writeBytes(data: ByteArray): Boolean {
        val fp = fopen(getAbsolutePath().toString(), "w") ?: throw IllegalStateException("cannot open file for writing")
        return try {
            val cString = data.toCValues()
            fwrite(cString, 1u, data.size.toULong(), fp) == 0.toULong()
        } finally {
            fclose(fp)
        }
    }

    private fun <T> Result<T>.onFailure(block: (Throwable) -> Unit) : Result<T> {
        if (isFailure) {
            exceptionOrNull()?.let { block(it) }
        }
        return this
    }

    fun renameTo(newPath: String) : File? {
        return runCatching {
            rename(getAbsolutePath().toString(), newPath).takeIf { it == 0 }?.let {
                File(newPath)
            }
        }.onFailure {
            println("error renaming file $path to $newPath ${it.message}")
        }.getOrNull()
    }

    fun deleteRecursively(): Boolean {
        return if (isDirectory()) {
            val files = listFiles(true)
            val childrenDeleted = files.all { it.deleteRecursively() }
            if (childrenDeleted) {
                val absPath = getAbsolutePath().toString()
                rmdir(absPath) == 0
            } else {
                false
            }
        } else if (isFile()) {
            remove(getAbsolutePath().toString()) == 0
        } else {
            true
        }
    }

    fun readData(): ByteArray {
        if (isDirectory()) {
            return ByteArray(0)
        }
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

    fun readContents(charset: Charset = Charset.UTF_8): String {
        val bytes = readData()
        return when (charset) {
            Charset.UTF_8 -> bytes.decodeToString()
            Charset.US_ASCII -> bytes.decodeToString()
            Charset.ISO_8859_1 -> CharArray(bytes.size) { bytes[it].toInt().and(0xFF).toChar() }.concatToString()
        }
    }

    override fun toString(): String {
        return path.toString()
    }
}