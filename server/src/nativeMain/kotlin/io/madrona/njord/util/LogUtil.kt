package io.madrona.njord.util

import io.ktor.util.logging.Logger
import io.madrona.njord.Singletons

inline fun <reified T:Any> T.logger(): Logger = Singletons.genLog
