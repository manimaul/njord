@file:OptIn(ExperimentalForeignApi::class)

import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.refTo
import kotlinx.cinterop.toKString
import platform.posix.FILE
import platform.posix.fgets
import platform.posix.pclose
import platform.posix.popen
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.fail

class FileTest {

    @AfterTest
    fun cleanup() {
        println(executeCommand("rm -rfv /tmp/njord"))
    }

    @Test
    fun testListFiles() {
        File("/tmp/njord/test/foo/bar").apply {
            mkdirs()
            assertTrue(exists())
            assertTrue(isDirectory())
        }
        File("/tmp/njord/test/a.txt").apply {
            touch()
            assertTrue(exists())
            assertFalse(isDirectory())
        }
        File("/tmp/njord/test/b.txt").apply {
            touch()
            assertTrue(exists())
            assertFalse(isDirectory())
        }
        File("/tmp/njord/test/foo/c.txt").apply {
            touch()
            assertTrue(exists())
            assertFalse(isDirectory())
        }
        File("/tmp/njord/test").apply {
            val listA = listFiles(true).map {
                it.name
            }.sorted()
            assertEquals(listOf("a.txt", "b.txt", "c.txt"), listA)
            val listB = listFiles(false).map {
                it.name
            }.sorted()
            assertEquals(listOf("a.txt", "b.txt"), listB)
        }

    }

    @Test
    fun testCwd() {
        File.getCurrentDirectory()?.let { cwd ->
            println("cwd = $cwd")
            assertTrue(cwd.isDirectory())
            assertFalse(cwd.isFile())
        } ?: run {
            fail("cws was null")
        }
    }

    @Test
    fun testMkdirs() {
        val dir = File("/tmp/njord/test/foo/bar").apply {
            assertFalse(exists())
            assertFalse(isFile())
            assertFalse(isDirectory())

            assertTrue(mkdirs())
            assertTrue(exists())
            assertTrue(isDirectory())
            assertFalse(isFile())
        }

        File(dir, "test.txt").apply {
            assertFalse(exists())
            assertFalse(isFile())
            assertFalse(isDirectory())

            write("hello text file")
            assertEquals("hello text file", readContents())
            assertTrue(exists())
            assertTrue(isFile())
            assertFalse(isDirectory())
        }
    }

    fun executeCommand(command: String): String {
    val fp: CPointer<FILE>? = popen(command, "r")
    val buffer = ByteArray(4096)
    val returnString = StringBuilder()

    if (fp == null) {
        throw RuntimeException("Failed to run command: $command")
    }

    try {
        memScoped {
            var scan = fgets(buffer.refTo(0), buffer.size, fp)
            while (scan != null) {
                returnString.append(scan.toKString())
                scan = fgets(buffer.refTo(0), buffer.size, fp)
            }
        }
    } finally {
        pclose(fp)
    }
    return returnString.trim().toString()
}
}