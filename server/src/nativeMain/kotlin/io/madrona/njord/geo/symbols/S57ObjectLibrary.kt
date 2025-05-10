package io.madrona.njord.geo.symbols

import io.madrona.njord.model.S57Attribute
import io.madrona.njord.model.S57ExpectedInput
import io.madrona.njord.model.S57Object
import io.madrona.njord.util.resourceAsString
import kotlinx.serialization.json.Json.Default.decodeFromString

class S57ObjectLibrary {

    val expectedInput: Map<String, List<S57ExpectedInput>> by lazy {
        decodeFromString(resourceAsString("s57expectedinput.json")!!)
    }

    val attributes: Map<String, S57Attribute> by lazy {
        decodeFromString(resourceAsString("s57attributes.json")!!)
    }

    val objects: Map<String, S57Object> by lazy {
        decodeFromString(resourceAsString("s57objectclasses.json")!!)
    }
}


