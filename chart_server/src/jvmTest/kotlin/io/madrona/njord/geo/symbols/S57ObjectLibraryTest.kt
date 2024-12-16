package io.madrona.njord.geo.symbols

import kotlin.test.*

class S57ObjectLibraryTest {

    @Test
    fun readS57Objects() {
        val subject = S57ObjectLibrary()
        val objects = subject.objects
        assertTrue(objects.isNotEmpty())

        val classSet = objects.values.fold(mutableSetOf<String>()) { acc, item ->
            acc.addAll(item.primitives)
            acc
        }
        assertTrue(classSet.isNotEmpty())
    }

    @Test
    fun readS57Attributes() {
        val subject = S57ObjectLibrary()
        val attributes = subject.attributes
        assertTrue(attributes.isNotEmpty())

        val classSet = attributes.values.fold(mutableSetOf<String>()) { acc, item ->
            acc.add(item.attribute)
            acc
        }
        assertTrue(classSet.isNotEmpty())
    }
}