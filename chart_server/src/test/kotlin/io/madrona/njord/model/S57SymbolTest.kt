package io.madrona.njord.model

import io.madrona.njord.Singletons
import io.madrona.njord.resourceAsString
import kotlin.test.*
import com.fasterxml.jackson.module.kotlin.readValue
import io.madrona.njord.geo.symbols.S57Prop

class S57SymbolTest {

    @Test
    fun testDeserialize() {
        val soa = resourceAsString("paper_symbol_rules.yaml")?.let {
            Singletons.yamlMapper.readValue<S57ObjAcronyms>(it)
        }
        assertNotNull(soa)
    }

    @Test
    fun testBcnCar() {
        val subject = Singletons.symbolLayerLibrary
        assertEquals(setOf("COLOUR", "BCNSHP"), subject.attKeys("BCNCAR"))
        val props: S57Prop = mutableMapOf(
            "COLOUR" to listOf("2", "6"),
            "BCNSHP" to listOf("3")
        )
        val sy = subject.symbol("BCNCAR", props)
        assertEquals("BCNTOW68", sy)
    }

    @Test
    fun testBcnCarFallback_NoMatchingColor() {
        val subject = Singletons.symbolLayerLibrary
        val props: S57Prop = mutableMapOf(
            "COLOUR" to listOf("2", "6", "2", "6", "2", "6"),
            "BCNSHP" to listOf("3")
        )
        val sy = subject.symbol("BCNCAR", props)
        assertEquals("BCNTOW01", sy)
    }

    @Test
    fun testBcnCarFallback_NoMatching() {
        val subject = Singletons.symbolLayerLibrary
        val props: S57Prop = mutableMapOf(
            "COLOUR" to listOf("2", "6", "2", "6", "2", "6"),
            "BCNSHP" to listOf("9")
        )
        val sy = subject.symbol("BCNCAR", props)
        assertEquals("BCNGEN03", sy)
    }

    @Test
    fun testDaymar() {
        val subject = Singletons.symbolLayerLibrary
        val props: S57Prop = mutableMapOf(
            "COLOUR" to listOf("1", "2", "1", "1", "2"),
            "COLPAT" to listOf("6", "4"),
            "TOPSHP" to listOf("12")
        )
        val sy = subject.symbol("DAYMAR", props)
        assertEquals("BCNTOW01", sy)
    }

    @Test
    fun testBoyspp() {
        val subject = Singletons.symbolLayerLibrary
        val props: S57Prop = mutableMapOf(
            "COLOUR" to listOf("3"),
            "CATSPM" to listOf("8")
        )
        val sy = subject.symbol("BOYSPP", props)
        assertEquals("BOYCAN60", sy)
    }
}
