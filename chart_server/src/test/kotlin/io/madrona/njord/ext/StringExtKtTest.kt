package io.madrona.njord.ext

import kotlin.test.*

internal class StringExtKtTest {

    @Test
    fun testPath() {
        val path = "sprites/rastersymbols-day.png"
        val token = path.extToken()
        assertEquals("png", token)
        val mt = path.mimeType()
        assertEquals("image/png", mt)
    }

    @Test
    fun testPbf() {
        val path = "fonts/Roboto%20Bold/0-255.pbf"
        val token = path.extToken()
        assertEquals("pbf", token)
        val mt = path.mimeType()
        assertEquals("application/x-protobuf", mt)
    }

    @Test
    fun testRange() {
        assertEquals(0..1, "[0,1]".intRange())
        assertEquals(999..-1, "[999,-1]".intRange())
        assertEquals(IntRange.EMPTY, "[".intRange())
        assertEquals(IntRange.EMPTY, "]".intRange())
        assertEquals(IntRange.EMPTY, "".intRange())
    }
}