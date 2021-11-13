package io.madrona.njord.geo.symbols

import kotlin.test.*

class S57ObjectLibraryTest {

    @Test
    fun readS57Objects() {
        val subject = S57ObjectLibrary()
        val objects = subject.objects
        assertTrue(objects.isNotEmpty())
    }
}