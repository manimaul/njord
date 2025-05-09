import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.toKString
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import platform.posix.fclose
import platform.posix.fgets
import platform.posix.fopen

@OptIn(ExperimentalForeignApi::class)
class File(path: String) {
    val path = SystemFileSystem.resolve(Path(path))

    fun exists() : Boolean {
        return SystemFileSystem.exists(path)
    }

    fun readContents() : String {
        val sb = StringBuilder()
        val file = fopen(path.toString(), "r") ?:
        throw IllegalArgumentException("Cannot open input file $path")
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