package io.madrona.njord.util

import org.slf4j.Logger
import org.slf4j.LoggerFactory

inline fun <reified T:Any> T.logger(): Logger = LoggerFactory.getLogger(T::class.java)
inline fun <reified T> logger(): Logger = LoggerFactory.getLogger(T::class.java)
