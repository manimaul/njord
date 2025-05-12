package geos

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.staticCFunction
import kotlinx.cinterop.toKString
import libgeos.GEOSGeom_createLinearRing
import libgeos.GEOSGeom_createPolygon
import libgeos.initGEOS

@OptIn(ExperimentalForeignApi::class)
object Geos {
    init {
        println("init geos")
        val handler = staticCFunction<CPointer<ByteVar>, Unit> {
            val message = it.toKString()
            println("init geos message: $message")
        }
        initGEOS(handler, handler)
    }

    fun createPolygon(vararg coordinate: Coordinate) : GeosGeometry? {
        return GeosCoordinateSequence.from(coordinate.toList())?.use {
            GEOSGeom_createLinearRing(it.ptr)?.let {
                GeosGeometry(it).use {
                    GEOSGeom_createPolygon(it.ptr, null, 0.toUInt())?.let {
                        GeosGeometry(it)
                    }
                }
            }

        }
    }

    fun createPolygon() : GeosGeometry {
        return GEOSGeom_createPolygon(null, null, 0.toUInt())?.let {
            GeosGeometry(it)
        } ?: throw IllegalStateException("")
    }
}