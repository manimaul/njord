package io.madrona.njord.geo.symbols

import io.madrona.njord.*
import io.madrona.njord.util.resourceAsString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

class S57ObjectLibrary {

    val expectedInput: Map<String, List<S57ExpectedInput>> by lazy {
        Json.decodeFromString(resourceAsString("s57expectedinput.json")!!)
    }

    val attributes: Map<String, S57Attribute> by lazy {
        Json.decodeFromString(resourceAsString("s57attributes.json")!!)
    }

    val objects: Map<String, S57Object> by lazy {
        Json.decodeFromString(resourceAsString("s57objectclasses.json")!!)
    }
}
