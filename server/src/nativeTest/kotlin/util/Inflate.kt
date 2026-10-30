@file:OptIn(ExperimentalForeignApi::class)

package util

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import platform.zlib.Z_BUF_ERROR
import platform.zlib.Z_NO_FLUSH
import platform.zlib.Z_OK
import platform.zlib.Z_STREAM_END
import platform.zlib.inflate
import platform.zlib.inflateEnd
import platform.zlib.inflateInit2
import platform.zlib.z_stream

/** Test-only gunzip of unknown output size - the inverse of `gzipCompress`, for reading archive tiles back. */
fun gunzip(data: ByteArray): ByteArray = memScoped {
    val strm = alloc<z_stream>()
    strm.zalloc = null
    strm.zfree = null
    strm.opaque = null
    check(inflateInit2(strm.ptr, 15 + 16) == Z_OK) { "inflateInit2 failed" }
    try {
        val out = ArrayList<Byte>(data.size * 4)
        val chunk = ByteArray(64 * 1024)
        data.usePinned { inPinned ->
            strm.next_in = inPinned.addressOf(0).reinterpret()
            strm.avail_in = data.size.convert()
            while (true) {
                val rc = chunk.usePinned { outPinned ->
                    strm.next_out = outPinned.addressOf(0).reinterpret()
                    strm.avail_out = chunk.size.convert()
                    val rc = inflate(strm.ptr, Z_NO_FLUSH)
                    val produced = chunk.size - strm.avail_out.toInt()
                    for (i in 0 until produced) out.add(chunk[i])
                    rc
                }
                if (rc == Z_STREAM_END) break
                check(rc == Z_OK || rc == Z_BUF_ERROR) { "inflate failed ($rc)" }
                check(rc != Z_BUF_ERROR || strm.avail_in.toInt() > 0) { "truncated gzip stream" }
            }
        }
        out.toByteArray()
    } finally {
        inflateEnd(strm.ptr)
    }
}
