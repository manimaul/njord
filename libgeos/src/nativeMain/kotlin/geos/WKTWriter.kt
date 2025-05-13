package geos

import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.toKString
import libgeos.GEOSWKTWriter
import libgeos.GEOSWKTWriter_destroy
import libgeos.GEOSWKTWriter_write

@OptIn(ExperimentalForeignApi::class)
class WKTWriter(private val ptr: CPointer<GEOSWKTWriter>) : AutoCloseable {

    fun write(geometry: GeosGeometry) : String {
        return memScoped {
            GEOSWKTWriter_write(ptr, geometry.ptr)?.toKString()
        } ?: ""
    }

    override fun close() {
        GEOSWKTWriter_destroy(ptr)
    }
}