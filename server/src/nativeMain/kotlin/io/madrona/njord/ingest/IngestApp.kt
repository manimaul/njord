@file:OptIn(ExperimentalForeignApi::class)

package io.madrona.njord.ingest

import File
import io.madrona.njord.Singletons
import io.madrona.njord.resources
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.staticCFunction
import kotlinx.coroutines.runBlocking
import platform.posix.SIGINT
import platform.posix.SIGTERM
import platform.posix.signal
import kotlin.system.exitProcess

fun ingestMain(args: Array<String>) {
    Gdal.initialize()
    args.firstOrNull()?.let {
        File(it)
    }?.takeIf { it.exists() }?.let {
        resources = it.path.toString()
        runBlocking {
            Singletons.ds.connection()
        }
        signal(SIGINT, staticCFunction { _ -> exitProcess(0) })
        signal(SIGTERM, staticCFunction { _ -> exitProcess(0) })
        runBlocking {
            ChartIngestWorker().run()
        }
    } ?: run {
        println("Path to resources directory was not supplied")
    }
}
