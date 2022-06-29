package io.madrona.njord.model

import kotlin.test.*
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
    fun testObstrn() {
        assertEquals("ISODGR51",
            subject.symbol(
                layer = "OBSTRN", props = mutableMapOf()
            )
        )
        assertEquals("ISODGR51",
            subject.symbol(
                layer = "OBSTRN", props = mutableMapOf(
                    "CATOBS" to listOf("1"),
                )
            )
        )
        assertEquals("FOULAR01",
            subject.symbol(
                layer = "OBSTRN", props = mutableMapOf(
                    "CATOBS" to listOf("6"),
                )
            )
        )
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

        assertEquals("FLTHAZ02",
            subject.symbol(
                layer = "OBSTRN", props = mutableMapOf(
                    "CATOBS" to listOf("8"),
                )
            )
        )
        assertEquals("ACHARE02",
            subject.symbol(
                layer = "OBSTRN", props = mutableMapOf(
                    "CATOBS" to listOf("9"),
                )
            )
        )
        assertEquals("FLTHAZ02",
            subject.symbol(
                layer = "OBSTRN", props = mutableMapOf(
                    "CATOBS" to listOf("10"),
                )
            )
        )
        assertEquals("FLTHAZ02",
            subject.symbol(
                layer = "OBSTRN", props = mutableMapOf(
                    "WATLEV" to listOf("7"),
                )
            )
        )
    }
}
