package io.madrona.njord.geo.symbols

import com.fasterxml.jackson.databind.ObjectMapper
import io.madrona.njord.Singletons
import io.madrona.njord.model.S57ObjAcronyms
import io.madrona.njord.resourceAsString
import com.fasterxml.jackson.module.kotlin.readValue
import io.madrona.njord.model.S57Symbol

class SymbolLayerLibrary(
    objectMapper: ObjectMapper = Singletons.objectMapper
) {

    private val objAcronyms: S57ObjAcronyms = objectMapper.readValue<S57ObjAcronyms>(resourceAsString("/paper_symbol_rules.json")!!).apply {
        values.forEach { symbols ->
            symbols.sortByDescending {
                it.attributes.size
            }
        }
    }

    fun attKeys(layer: String): Set<String> {
        return objAcronyms[layer]?.map { symbol ->
            symbol.attributes.flatMap {
                it.keys
            }
        }?.flatten()?.toSet() ?: emptySet()
    }

    fun symbol(layer: String, props: S57Prop): String? {
        return objAcronyms[layer]?.firstOrNull {
            props.matches(it)
        }?.symbol
    }

    private fun S57Prop.matches(symbol: S57Symbol) : Boolean {
        symbol.attributes.forEach { att ->
            att.forEach { (key, values) ->
                if (stringValues(key) != values) {
                    return false
                }
            }
        }
        return true
    }
}