package io.madrona.njord.util

import io.ktor.util.logging.LogLevel
import io.ktor.util.logging.Logger
import io.madrona.njord.Singletons

inline fun <reified T:Any> T.logger(): Logger = Singletons.genLog ?: object: Logger {
    override val level: LogLevel = LogLevel.DEBUG

    override fun error(message: String) {
        println("ERROR $message")
    }

    override fun error(message: String, cause: Throwable) {
        println("ERROR $message ${cause.message}")
        cause.printStackTrace()
    }

    override fun warn(message: String) {
        println("WARN $message")
    }

    override fun warn(message: String, cause: Throwable) {
        println("WARN $message ${cause.message}")
        cause.printStackTrace()
    }

    override fun info(message: String) {
        println("INFO $message")
    }

    override fun info(message: String, cause: Throwable) {
        println("INFO $message ${cause.message}")
        cause.printStackTrace()
    }

    override fun debug(message: String) {
        println("DEBUG $message")
    }

    override fun debug(message: String, cause: Throwable) {
        println("DEBUG $message ${cause.message}")
    }

    override fun trace(message: String) {
        println("TRACE $message")
    }

    override fun trace(message: String, cause: Throwable) {
        println("TRACE $message ${cause.message}")
    }

}
