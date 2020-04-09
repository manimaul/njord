package io.madrona.njord

import org.slf4j.LoggerFactory

inline fun <reified T:Any> T.logger() = LoggerFactory.getLogger(T::class.java)
