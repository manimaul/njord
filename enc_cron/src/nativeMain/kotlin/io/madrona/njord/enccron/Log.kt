package io.madrona.njord.enccron

/**
 * Minimal stdout logging. This runs as a Kubernetes CronJob, so `kubectl logs` is the only
 * consumer and plain prefixed lines are all that is needed - no dependency on the server's
 * ktor-backed logger.
 */
object log {
    fun info(message: String) = println("INFO  $message")
    fun warn(message: String) = println("WARN  $message")
    fun error(message: String) = println("ERROR $message")

    fun error(message: String, cause: Throwable) {
        println("ERROR $message: ${cause.message}")
        cause.printStackTrace()
    }
}
