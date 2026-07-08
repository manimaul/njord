@file:OptIn(ExperimentalForeignApi::class)

package util

import io.madrona.njord.util.gzipCompress
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import platform.zlib.Z_OK
import platform.zlib.Z_STREAM_END
import platform.zlib.inflate
import platform.zlib.inflateEnd
import platform.zlib.inflateInit2
import platform.zlib.z_stream
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Test-only inflate, mirroring gzipCompress's deflate, to verify round-trip correctness. */
private fun gzipDecompress(data: ByteArray, expectedSize: Int): ByteArray = memScoped {
    val strm = alloc<z_stream>()
    strm.zalloc = null
    strm.zfree = null
    strm.opaque = null

    val rc = inflateInit2(strm.ptr, 15 + 16)
    check(rc == Z_OK) { "inflateInit2 failed ($rc)" }

    try {
        // allocate at least 1 byte so addressOf(0) is always valid, even though avail_out
        // below is set to the real (possibly zero) expected size
        val output = ByteArray(maxOf(expectedSize, 1))
        data.usePinned { inPinned ->
            output.usePinned { outPinned ->
                strm.next_in = if (data.isEmpty()) null else inPinned.addressOf(0).reinterpret()
                strm.avail_in = data.size.convert()
                strm.next_out = outPinned.addressOf(0).reinterpret()
                strm.avail_out = expectedSize.convert()
                val inflateRc = inflate(strm.ptr, 0)
                check(inflateRc == Z_STREAM_END) { "inflate did not finish in a single pass ($inflateRc)" }
            }
        }
        output.copyOf(expectedSize)
    } finally {
        inflateEnd(strm.ptr)
    }
}

class GzipTest {

    @Test
    fun `compressed output round-trips to original bytes`() {
        val original = "the quick brown fox jumps over the lazy dog ".repeat(50).encodeToByteArray()
        val compressed = gzipCompress(original)
        val decompressed = gzipDecompress(compressed, original.size)
        assertContentEquals(original, decompressed)
    }

    @Test
    fun `compressed output starts with gzip magic bytes`() {
        val compressed = gzipCompress("hello world".encodeToByteArray())
        assertEquals(0x1f, compressed[0].toInt() and 0xff)
        assertEquals(0x8b.toByte(), compressed[1])
    }

    @Test
    fun `repetitive input compresses smaller than original`() {
        val original = "a".repeat(10_000).encodeToByteArray()
        val compressed = gzipCompress(original)
        assertTrue(compressed.size < original.size)
    }

    @Test
    fun `empty input compresses without error`() {
        val compressed = gzipCompress(ByteArray(0))
        val decompressed = gzipDecompress(compressed, 0)
        assertEquals(0, decompressed.size)
    }
}
