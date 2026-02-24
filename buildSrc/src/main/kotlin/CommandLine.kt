import java.io.InputStreamReader
import java.util.concurrent.TimeUnit


object CommandLine {

    fun exec(cmd: String): String {
        val p = Runtime.getRuntime().exec(arrayOf("/bin/bash", "-c", cmd))
        val stdout = InputStreamReader(p.inputStream).use {
            it.readText()
        }
        p.waitFor(5, TimeUnit.SECONDS)
        return stdout.trim()
    }
}