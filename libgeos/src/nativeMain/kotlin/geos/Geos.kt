package geos

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.staticCFunction
import kotlinx.cinterop.toKString
import libgeos.GEOSGeom_createLinearRing
import libgeos.GEOSGeom_createPolygon
import libgeos.GEOSWKBReader_create
import libgeos.GEOSWKBWriter_create
import libgeos.initGEOS

@OptIn(ExperimentalForeignApi::class)
object Geos {

    val wkbReader: WKBReader
    val wkbWriter: WKBWriter

    init {
        println("init geos")
        val handler = staticCFunction<CPointer<ByteVar>, Unit> {
            val message = it.toKString()
            println("init geos message: $message")
        }
        initGEOS(handler, handler)

        wkbReader = WKBReader(checkNotNull(GEOSWKBReader_create()))
        wkbWriter = WKBWriter(checkNotNull(GEOSWKBWriter_create()))
    }

    fun createPolygon(vararg coordinate: Coordinate): GeosGeometry {
        return GeosCoordinateSequence.from(coordinate.toList()).use { coords ->
            val ring = GeosGeometry(checkNotNull(GEOSGeom_createLinearRing(coords.ptr)), true)
            coords.owned = true
            GeosGeometry(checkNotNull(GEOSGeom_createPolygon(ring.ptr, null, 0.toUInt())))
        }
    }

    fun createPolygon(): GeosGeometry {
        return GeosGeometry(checkNotNull(GEOSGeom_createPolygon(null, null, 0.toUInt())), false)
    }
}
