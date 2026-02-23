import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ZipFileTest {

    @Test
    fun testZipFile() {
        val f = File("./src/nativeTest/resources/")
        val outDir = File("./src/nativeTest/resources/out")
        outDir.deleteRecursively()
        f.listFiles(false).filter { it.name.endsWith(".zip") }.forEach {
            val zf = ZipFile(it)
            println("zip file <${it.name}> size is ${zf.size()}")
            zf.entries().forEach {
                println("entry name <${it.name()}>")
                it.unzipToPath(outDir)
                println(it.name())
            }
        }
        assertTrue(File(outDir, "foo").isDirectory())
        assertTrue(File(outDir, "foo/bar").isDirectory())
        assertTrue(File(outDir, "foo/baz").isDirectory())
        assertEquals("hello\n", File(outDir, "foo/bar/data.txt").readContents())
    }
}