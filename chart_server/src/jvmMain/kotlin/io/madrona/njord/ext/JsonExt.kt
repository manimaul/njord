package io.madrona.njord.ext

import io.madrona.njord.geojson.Geometry
import io.madrona.njord.model.Color
import io.madrona.njord.model.IconRotationAlignment
import io.madrona.njord.model.Sprite
import io.madrona.njord.model.Theme
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json.Default.encodeToJsonElement
import kotlinx.serialization.json.Json.Default.encodeToString
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive

inline val Boolean.json: JsonElement
    get() = JsonPrimitive(this)

inline val String.json: JsonElement
    get() = JsonPrimitive(this)

inline val Int.json: JsonElement
    get() = JsonPrimitive(this)

inline val Long.json: JsonElement
    get() = JsonPrimitive(this)

inline val Float.json: JsonElement
    get() = JsonPrimitive(this)

inline val Double.json: JsonElement
    get() = JsonPrimitive(this)

inline val Byte.json: JsonElement
    get() = JsonPrimitive(this)


inline val List<JsonElement>.json: JsonElement
    @JvmName("list_JsonElement_j")
    get() = JsonArray(this)

inline val Color.json: JsonElement
    get() = encodeToJsonElement(Color.serializer(), this)

inline val Theme.json: JsonElement
    get() = encodeToJsonElement(Theme.serializer(), this)

inline val IconRotationAlignment.json: JsonElement
    get() = encodeToJsonElement(IconRotationAlignment.serializer(), this)

inline val Sprite.json: JsonElement
    get() = encodeToJsonElement(Sprite.serializer(), this)

inline val List<Int>.json: JsonElement
    @JvmName("list_Int_j")
    get() = JsonArray(this.map { it.json })

inline val List<Float>.json: JsonElement
    @JvmName("list_Float_j")
    get() = JsonArray(this.map { it.json })

inline val List<Long>.json: JsonElement
    @JvmName("list_Long_j")
    get() = JsonArray(this.map { it.json })

inline val IntArray.json: JsonElement
    get() = JsonArray(this.map { it.json })

inline val DoubleArray.json: JsonElement
    get() = JsonArray(this.map { it.json })

inline val Array<String>.json: JsonElement
    get() = JsonArray(this.map { it.json })

inline val ByteArray.json: JsonElement
    get() = JsonArray(this.map { it.json })

inline val List<Double>.json: JsonElement
    @JvmName("list_Double_j")
    get() = JsonArray(this.map { it.json })

inline val List<String>.json: JsonElement
    @JvmName("list_String_j")
    get() = JsonArray(this.map { it.json })

inline val List<Any>.json: JsonElement
    @JvmName("list_Any_j")
    get() = anyListToJsonElement(this)

fun anyListToJsonElement(list: List<*>): JsonElement {
    return JsonArray(list.map {
        when (it) {
            is JsonElement -> it
            is String -> it.json
            is List<*> -> anyListToJsonElement(it)
            is Double -> it.json
            is Float -> it.json
            is Int -> it.json
            is Boolean -> it.json
            is Color -> it.json
            is Sprite -> it.json
            is IconRotationAlignment -> it.json
            else -> JsonNull
        }
    })
}

fun Geometry.jsonStr(): String {
    return encodeToString(Geometry.serializer(), this)
}

@JvmName("map_sje_j")
fun Map<String, JsonElement>.jsonStr(): String {
    return encodeToString(MapSerializer(String.serializer(), JsonElement.serializer()), this)
}

@JvmName("map_ss_j")
fun Map<String, String>.jsonStr(): String {
    return encodeToString(MapSerializer(String.serializer(), String.serializer()), this)
}
