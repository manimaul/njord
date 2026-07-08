@file:OptIn(ExperimentalForeignApi::class)

package io.madrona.njord.util

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import platform.zlib.Z_DEFAULT_COMPRESSION
import platform.zlib.Z_DEFAULT_STRATEGY
import platform.zlib.Z_DEFLATED
import platform.zlib.Z_FINISH
import platform.zlib.Z_OK
import platform.zlib.Z_STREAM_END
import platform.zlib.compressBound
import platform.zlib.deflate
import platform.zlib.deflateEnd
import platform.zlib.deflateInit2
import platform.zlib.z_stream

/**
 * Compresses [data] into gzip format (RFC 1952) via zlib's deflate in a single in-memory
 * pass. windowBits = 15 + 16 tells zlib to emit a gzip header/trailer (magic bytes 0x1f 0x8b)
 * rather than a raw zlib-wrapped deflate stream, which is what MVT/MBTiles consumers expect
 * for transparent auto-decompression.
 */
fun gzipCompress(data: ByteArray, level: Int = Z_DEFAULT_COMPRESSION): ByteArray = memScoped {
    val strm = alloc<z_stream>()
    strm.zalloc = null
    strm.zfree = null
    strm.opaque = null

    val windowBits = 15 + 16
    val memLevel = 8
    var rc = deflateInit2(strm.ptr, level, Z_DEFLATED, windowBits, memLevel, Z_DEFAULT_STRATEGY)
    check(rc == Z_OK) { "deflateInit2 failed ($rc)" }

    try {
        // compressBound() only accounts for raw-deflate/zlib framing overhead; gzip adds its
        // own ~18 bytes (10-byte header + 8-byte trailer) on top, which matters for tiny/empty
        // inputs where compressBound()'s slack is otherwise smaller than that.
        val bound = compressBound(data.size.convert()).toInt() + 18
        val output = ByteArray(bound)
        val written = data.usePinned { inPinned ->
            output.usePinned { outPinned ->
                strm.next_in = if (data.isEmpty()) null else inPinned.addressOf(0).reinterpret()
                strm.avail_in = data.size.convert()
                strm.next_out = outPinned.addressOf(0).reinterpret()
                strm.avail_out = bound.convert()
                rc = deflate(strm.ptr, Z_FINISH)
                check(rc == Z_STREAM_END) { "deflate did not finish in a single pass ($rc)" }
                bound - strm.avail_out.toInt()
            }
        }
        output.copyOf(written)
    } finally {
        deflateEnd(strm.ptr)
    }
}
