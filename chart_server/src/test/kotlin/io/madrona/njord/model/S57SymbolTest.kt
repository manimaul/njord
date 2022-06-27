package io.madrona.njord.model

import io.madrona.njord.Singletons
import io.madrona.njord.util.resourceAsString
import kotlin.test.*
import com.fasterxml.jackson.module.kotlin.readValue
import io.madrona.njord.geo.symbols.S57Prop
import io.madrona.njord.geo.symbols.SymbolLayerLibrary
import org.junit.Before

class S57SymbolTest {

    private lateinit var subject: SymbolLayerLibrary

    @Before
    fun beforeEach() {
        subject = SymbolLayerLibrary(rules = "simplified_symbol_rules.yaml")
    }

    @Test
    fun testLndare() {
        assertEquals(emptySet(), subject.attKeys("LNDARE"))
        val props: S57Prop = mutableMapOf(
            "CONDTN" to listOf("1"),
            "STATUS" to listOf("1", "14")
        )
        val sy = subject.symbol("LNDARE", props)
        assertEquals("LNDARE01", sy)
    }

    @Test
    fun test_obstrn_foul_ground() {
        assertEquals("FOULGND1",
            subject.symbol(
                layer = "OBSTRN", props = mutableMapOf(
                    "CATOBS" to listOf("7"),
                    "VALSOU" to emptyList<String>(),
                )
            )
        )
        assertEquals("FOULGND1",
            subject.symbol(
                layer = "OBSTRN", props = mutableMapOf(
                    "CATOBS" to listOf("7"),
                )
            )
        )
    }

//    @Test
//    fun test_obstrn_() {
//        val subject = SymbolLayerLibrary(rules = "simplified_symbol_rules.yaml")
//        val props: S57Prop = mutableMapOf(
//            "CATOBS" to listOf("6"),
//        )
//        val sy = subject.symbol("OBSTRN", props)
//        assertEquals("FOULAR01", sy)
//    }
}
