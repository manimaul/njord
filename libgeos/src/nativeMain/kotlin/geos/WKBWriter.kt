package geos

import kotlinx.cinterop.CPointer
import kotlinx.cinterop.DoubleVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ULongVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.nativeHeap.alloc
import kotlinx.cinterop.ptr
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.value
import libgeos.GEOSFree
import libgeos.GEOSWKBWriter
import libgeos.GEOSWKBWriter_destroy
import libgeos.GEOSWKBWriter_write

@OptIn(ExperimentalForeignApi::class)
class WKBWriter(private val ptr: CPointer<GEOSWKBWriter>) : AutoCloseable {

    fun write(geometry: GeosGeometry) : ByteArray {
        return memScoped {
            val x = alloc<ULongVar>()
            x.value = writeSize.toULong()
            val arr = GEOSWKBWriter_write(ptr, geometry.ptr, x.ptr)
            arr?.readBytes(writeSize).also {
                GEOSFree(arr)
            }
        } ?: ByteArray(0)
    }

    override fun close() {
        GEOSWKBWriter_destroy(ptr)
    }

    companion object {
        val writeSize = 8192 //large enough for tile size geometry
    }
}