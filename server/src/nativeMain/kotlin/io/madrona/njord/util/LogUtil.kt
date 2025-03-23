package io.madrona.njord.util

import io.ktor.util.logging.LogLevel
import io.ktor.util.logging.Logger
import io.madrona.njord.Singletons

inline fun <reified T:Any> T.logger(): Logger = Singletons.genLog ?: object: Logger {
    override val level: LogLevel = LogLevel.DEBUG

    override fun error(message: String) {
    }

    override fun error(message: String, cause: Throwable) {
    }

    override fun warn(message: String) {
    }

    override fun warn(message: String, cause: Throwable) {
    }

    override fun info(message: String) {
    }

    override fun info(message: String, cause: Throwable) {
    }

    override fun debug(message: String) {
    }

    override fun debug(message: String, cause: Throwable) {
    }

    override fun trace(message: String) {
    }

    override fun trace(message: String, cause: Throwable) {
    }

}
