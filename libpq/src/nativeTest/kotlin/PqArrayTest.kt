import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PqArrayTest {

    @Test
    fun test0() {
        val array0 = """{"apple,banana,orange"}"""
        val parsedArray0 = parsePqArray(array0)
        assertEquals("apple,banana,orange", parsedArray0[0])
        assertEquals(1, parsedArray0.size)
    }

    @Test
    fun test1() {
        val array1 = """{apple,banana,"orange, juicy",NULL}"""
        val parsedArray1 = parsePqArray(array1)
        assertEquals(4, parsedArray1.size)
        assertEquals("apple", parsedArray1[0])
        assertEquals("banana", parsedArray1[1])
        assertEquals("orange, juicy", parsedArray1[2])
        assertNull(parsedArray1[3])
    }

    @Test
    fun test2() {
        val array2 = "{}"
        val parsedArray2 = parsePqArray(array2)
        assertEquals(0, parsedArray2.size)
    }

    @Test
    fun test3() {
        val array3 = """{"quoted string with \"escaped\" quote"}"""
        val parsedArray3 = parsePqArray(array3)
        assertEquals(1, parsedArray3.size)
        assertEquals("""quoted string with \"escaped\" quote""", parsedArray3[0])
    }

    @Test
    fun test4() {
        val array4 = """{"apple,banana,orange",food}"""
        val parsedArray4 = parsePqArray(array4)
        assertEquals(2, parsedArray4.size)
        assertEquals("""apple,banana,orange""", parsedArray4[0])
        assertEquals("""food""", parsedArray4[1])
    }
}