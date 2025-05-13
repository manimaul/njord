package geos

import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import libgeos.GEOSWKTReader
import libgeos.GEOSWKTReader_destroy
import libgeos.GEOSWKTReader_read

@OptIn(ExperimentalForeignApi::class)
class WKTReader(private val ptr: CPointer<GEOSWKTReader>) : AutoCloseable {

    fun read(wkt: String) : GeosGeometry {
        val ptr = checkNotNull(GEOSWKTReader_read(ptr, wkt))
        return GeosGeometry(ptr)
    }

    override fun close() {
        GEOSWKTReader_destroy(ptr)
    }
}