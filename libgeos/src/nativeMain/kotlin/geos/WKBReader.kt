package geos

import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.*
import libgeos.GEOSWKBReader
import libgeos.GEOSWKBReader_destroy
import libgeos.GEOSWKBReader_read


@OptIn(ExperimentalForeignApi::class)
class WKBReader(val ptr: CPointer<GEOSWKBReader>) : AutoCloseable {

    fun read(wkb: ByteArray?): GeosGeometry? {
        return wkb?.toUByteArray()?.toCValues()?.let { values ->
            GEOSWKBReader_read(ptr, values, wkb.size.toULong())?.let {
                GeosGeometry(it, false)
            }
        }
    }

    override fun close() {
        GEOSWKBReader_destroy(ptr)
    }
}