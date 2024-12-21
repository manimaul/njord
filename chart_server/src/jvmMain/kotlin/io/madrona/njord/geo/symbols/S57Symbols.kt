package io.madrona.njord.geo.symbols

import kotlinx.serialization.json.*


typealias S57Prop = MutableMap<String, JsonElement>

fun S57Prop.stringValue(key: String): String? = get(key)?.jsonPrimitive?.contentOrNull
fun S57Prop.intValue(key: String): Int? = get(key)?.jsonPrimitive?.contentOrNull?.toIntOrNull()
fun S57Prop.stringValues(key: String): List<String>? = get(key)?.jsonArray?.map { it.jsonPrimitive.content }
fun S57Prop.intValues(key: String): List<Int> = stringValues(key)?.mapNotNull { it.toIntOrNull() } ?: emptyList()
fun S57Prop.floatValue(key: String): Float? = get(key)?.jsonPrimitive?.contentOrNull?.toFloatOrNull()
fun S57Prop.doubleValue(key: String): Double? = get(key)?.jsonPrimitive?.contentOrNull?.toDoubleOrNull()

fun JsonElement.toAny(): Any {
    return when (this) {
        is JsonPrimitive -> {
            val value = if (isString) {
                content
            } else if (content.contains(".")) {
                content.toDoubleOrNull()
            } else if ("true" == content) {
                true
            } else if ("false" == content) {
                false
            } else {
                content.toLongOrNull()
            }
            value ?: content
        }

        is JsonArray -> map { it.toAny() }
        is JsonObject -> mapValues { it.value.toAny() }
        JsonNull -> "null"
        else -> this
    }
}
