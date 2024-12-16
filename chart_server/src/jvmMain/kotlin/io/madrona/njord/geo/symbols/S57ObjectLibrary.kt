package io.madrona.njord.geo.symbols

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import io.madrona.njord.S57Attribute
import io.madrona.njord.S57ExpectedInput
import io.madrona.njord.S57Object
import io.madrona.njord.Singletons
import io.madrona.njord.util.resourceAsString

class S57ObjectLibrary(
    private val objectMapper: ObjectMapper = Singletons.objectMapper,
) {

    val expectedInput: Map<String, List<S57ExpectedInput>> by lazy {
        objectMapper.readValue(resourceAsString("s57expectedinput.json")!!, object: TypeReference<Map<String, List<S57ExpectedInput>>>() {})
    }

    val attributes: Map<String, S57Attribute> by lazy {
        objectMapper.readValue(resourceAsString("s57attributes.json")!!, object: TypeReference<Map<String, S57Attribute>>() {})
    }

    val objects: Map<String, S57Object> by lazy {
        objectMapper.readValue(resourceAsString("s57objectclasses.json")!!, object: TypeReference<Map<String, S57Object>>() {})
    }
}


