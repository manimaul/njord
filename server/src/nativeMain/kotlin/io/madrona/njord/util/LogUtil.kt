package io.madrona.njord.util

import io.ktor.util.logging.Logger

inline fun <reified T:Any> T.logger(): Logger = Singletons.genLog
