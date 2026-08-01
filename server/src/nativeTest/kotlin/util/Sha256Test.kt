package util

import File
import io.madrona.njord.util.sha256File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class Sha256Test {

    private val dir = File("/tmp/njord/sha256test")

    @AfterTest
    fun cleanup() {
        dir.deleteRecursively()
    }

    private fun fileWith(name: String, data: ByteArray): File {
        dir.mkdirs()
        return File(dir, name).apply { writeBytes(data) }
    }

    @Test
    fun `empty file hashes to the empty-input digest`() {
        val file = fileWith("empty", ByteArray(0))
        assertEquals(
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            sha256File(file),
        )
    }

    @Test
    fun `known test vector`() {
        val file = fileWith("abc", "abc".encodeToByteArray())
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            sha256File(file),
        )
    }

    @Test
    fun `file spanning multiple read chunks hashes correctly`() {
        // 3 MiB + 17 bytes: exercises several 1 MiB chunks plus a partial final read
        val file = fileWith("big", ByteArray(3 * 1024 * 1024 + 17) { 'a'.code.toByte() })
        assertEquals(
            "8cb91458835f2fefeb12cf01e67a7ea18523a0567d4835a974dc04c2ddbb955b",
            sha256File(file),
        )
    }

    @Test
    fun `missing file returns null`() {
        assertNull(sha256File(File(dir, "does-not-exist")))
    }
}
