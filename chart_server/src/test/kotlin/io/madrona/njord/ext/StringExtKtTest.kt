package io.madrona.njord.ext

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

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
}