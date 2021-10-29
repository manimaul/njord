package io.madrona.njord.model

import io.madrona.njord.Singletons
import io.madrona.njord.resourceAsString
import kotlin.test.*
import com.fasterxml.jackson.module.kotlin.readValue

class S57SymbolTest {


    @Test
    fun foo() {
        val soa = resourceAsString("/paper_symbol_rules.json")?.let {
            Singletons.objectMapper.readValue<S57ObjAcronyms>(it)
        }
        assertNotNull(soa)
    }
}
