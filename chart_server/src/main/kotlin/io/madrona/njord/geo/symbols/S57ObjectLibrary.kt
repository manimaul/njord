package io.madrona.njord.geo.symbols

import com.fasterxml.jackson.databind.ObjectMapper
import io.madrona.njord.*
import io.madrona.njord.ext.decodeJson
import io.madrona.njord.util.resourceAsString

class S57ObjectLibrary(
    private val objectMapper: ObjectMapper = Singletons.objectMapper,
) {

    val expectedInput: Map<String, List<S57ExpectedInput>> by lazy {
        resourceAsString("s57expectedinput.json")!!.decodeJson(objectMapper)
    }

    val attributes: Map<String, S57Attribute> by lazy {
        resourceAsString("s57attributes.json")!!.decodeJson(objectMapper)
    }

    val objects: Map<String, S57Object> by lazy {
        resourceAsString("s57objectclasses.json")!!.decodeJson(objectMapper)
    }
}


