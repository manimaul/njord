import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ZipWriterTest {

    private fun workDir(name: String): File = File("./build/tmp/test_data/$name").also {
        it.deleteRecursively()
        it.mkdirs()
    }

    private fun sourceFile(dir: File, name: String, contents: String): File =
        File(dir, name).also {
            it.parentFile()?.mkdirs()
            it.write(contents)
        }

    private fun readAll(entry: ZipFileEntry): String {
        val out = StringBuilder()
        entry.readFileChunked { buf, n -> out.append(buf.copyOf(n).decodeToString()) }
        return out.toString()
    }

    @Test
    fun `round trips nested entry names through ZipFile`() {
        val dir = workDir("zipwriter_roundtrip")
        val a = sourceFile(dir, "src/ENC_ROOT/US5WA22M/US5WA22M.000", "cell contents\n")
        val b = sourceFile(dir, "src/ENC_ROOT/US5WA22M/US5WA22A.TXT", "text contents\n")
        val archive = File(dir, "bundle.zip")

        ZipWriter.create(archive).use { w ->
            w.addFile("ENC_ROOT/US5WA22M/US5WA22M.000", a)
            w.addFile("ENC_ROOT/US5WA22M/US5WA22A.TXT", b)
            assertEquals(2, w.entryCount)
        }

        assertTrue(archive.exists())
        val entries = ZipFile(archive).entries().associate { it.name() to readAll(it) }
        assertEquals(
            setOf("ENC_ROOT/US5WA22M/US5WA22M.000", "ENC_ROOT/US5WA22M/US5WA22A.TXT"),
            entries.keys,
        )
        assertEquals("cell contents\n", entries["ENC_ROOT/US5WA22M/US5WA22M.000"])
        assertEquals("text contents\n", entries["ENC_ROOT/US5WA22M/US5WA22A.TXT"])
    }

    /**
     * Pins the semantics the bundling design depends on: `zip_source_file` is lazy, so sources
     * are read at close() time, not at addFile() time. Staging dirs must therefore outlive the
     * writer. If this ever starts passing silently, bundles could ship with missing entries.
     */
    @Test
    fun `close fails when a staged source disappeared before close`() {
        val dir = workDir("zipwriter_lazy_source")
        val src = sourceFile(dir, "vanishing.txt", "here for now\n")
        val archive = File(dir, "bundle.zip")

        val writer = ZipWriter.create(archive)
        writer.addFile("vanishing.txt", src)
        src.deleteRecursively()

        assertFailsWith<ZipWriteException> { writer.close() }
    }

    @Test
    fun `discard writes no archive`() {
        val dir = workDir("zipwriter_discard")
        val src = sourceFile(dir, "a.txt", "content\n")
        val archive = File(dir, "bundle.zip")

        ZipWriter.create(archive).let { w ->
            w.addFile("a.txt", src)
            w.discard()
        }

        assertTrue(!archive.exists(), "discard must not leave an archive behind")
    }

    @Test
    fun `Store and DeflateFast produce different sizes for compressible input`() {
        val dir = workDir("zipwriter_compression")
        val src = sourceFile(dir, "big.txt", "abcdefgh".repeat(8192))

        val stored = File(dir, "stored.zip")
        ZipWriter.create(stored).use { it.addFile("big.txt", src, ZipCompression.Store) }

        val deflated = File(dir, "deflated.zip")
        ZipWriter.create(deflated).use { it.addFile("big.txt", src, ZipCompression.DeflateFast) }

        val storedSize = stored.readData().size
        val deflatedSize = deflated.readData().size
        assertTrue(
            deflatedSize < storedSize,
            "deflate($deflatedSize) should beat store($storedSize) on repetitive text",
        )
        // Both must still read back identically.
        assertEquals(
            "abcdefgh".repeat(8192),
            readAll(ZipFile(deflated).entries().single()),
        )
    }

    @Test
    fun `adding a missing source fails fast at add time`() {
        val dir = workDir("zipwriter_missing_source")
        ZipWriter.create(File(dir, "bundle.zip")).let { w ->
            assertFailsWith<IllegalArgumentException> {
                w.addFile("nope.txt", File(dir, "does_not_exist.txt"))
            }
            w.discard()
        }
    }

    @Test
    fun `duplicate entry names overwrite rather than fail`() {
        val dir = workDir("zipwriter_duplicates")
        val first = sourceFile(dir, "first.txt", "first\n")
        val second = sourceFile(dir, "second.txt", "second\n")
        val archive = File(dir, "bundle.zip")

        ZipWriter.create(archive).use { w ->
            w.addFile("ENC_ROOT/README.TXT", first)
            w.addFile("ENC_ROOT/README.TXT", second)
        }

        val entries = ZipFile(archive).entries()
        assertEquals(1, entries.size, "ZIP_FL_OVERWRITE should collapse the duplicate")
        assertEquals("second\n", readAll(entries.single()))
    }

    @Test
    fun `use after close is rejected`() {
        val dir = workDir("zipwriter_use_after_close")
        val src = sourceFile(dir, "a.txt", "content\n")
        val w = ZipWriter.create(File(dir, "bundle.zip"))
        w.addFile("a.txt", src)
        w.close()
        assertFailsWith<IllegalStateException> { w.addFile("b.txt", src) }
    }
}
