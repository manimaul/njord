package io.madrona.njord

import io.madrona.njord.util.File
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.runBlocking

@OptIn(ExperimentalForeignApi::class)
fun main(args: Array<String>) {
    Gdal.initialize()
    args.firstOrNull()?.let {
        File(it)
    }?.takeIf { it.exists() }?.let {
        resources = it.path.toString()
        runBlocking {
            Singletons.ds.connection()
        }
        ChartServerApp().serve()
    } ?: run {
        println("Path to resources directory was not supplied")
    }
}
