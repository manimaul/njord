package io.madrona.njord

import com.fasterxml.jackson.module.kotlin.jsonMapper
import com.fasterxml.jackson.module.kotlin.kotlinModule
import io.madrona.njord.model.ColorLibrary
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

object Singletons {

    val objectMapper = jsonMapper {
        addModule(kotlinModule())
    }

    val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val config = ChartsConfig()

    val colorLibrary: ColorLibrary = ColorLibrary()
}