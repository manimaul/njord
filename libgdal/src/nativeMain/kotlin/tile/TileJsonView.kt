package tile

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * A readable view of an encoded MVT, for golden fixtures and tests (RUST_PORT_PLAN.md §5.2):
 *
 * ```
 * { "layers": [ { "name", "version", "extent",
 *                 "features": [ { "id", "type", "props": {…}, "geometry": [ [ [x, y], … ], … ] } ] } ] }
 * ```
 *
 * `geometry` is a list of paths in tile pixels: one path per point/line/ring, decoded from the
 * MoveTo/LineTo/ClosePath command stream. A ClosePath repeats the ring's first vertex so a closed
 * ring reads as one. Keys and layers keep their encoded order; the Rust port sorts both sides
 * before comparing, since two correct MVT encoders may legally differ there.
 */
fun Tile.toJsonView(): JsonObject = buildJsonObject {
    put("layers", JsonArray(layers.map { it.toJsonView() }))
}

fun decodeMvtToJson(bytes: ByteArray): JsonObject = Tile.decodeTile(bytes).toJsonView()

private fun Tile.Layer.toJsonView(): JsonObject = buildJsonObject {
    put("name", name)
    put("version", version)
    put("extent", extent)
    put("features", JsonArray(features.map { feature ->
        buildJsonObject {
            put("id", feature.id.toLong())
            put("type", feature.type.name)
            put("props", buildJsonObject {
                feature.tags.chunked(2).forEach { (k, v) ->
                    put(keys[k.toInt()], values[v.toInt()].toJson())
                }
            })
            put("geometry", decodeGeometry(feature.geometry))
        }
    }))
}

private fun Tile.Value.toJson(): JsonElement = when {
    stringValue != null -> JsonPrimitive(stringValue)
    floatValue != null -> JsonPrimitive(floatValue)
    doubleValue != null -> JsonPrimitive(doubleValue)
    intValue != null -> JsonPrimitive(intValue)
    uintValue != null -> JsonPrimitive(uintValue.toLong())
    sintValue != null -> JsonPrimitive(sintValue)
    boolValue != null -> JsonPrimitive(boolValue)
    else -> JsonNull
}

private fun zigZagDecode(n: Int): Int = (n ushr 1) xor -(n and 1)

/** Inverse of `VectorTileEncoder.commands*`: the command stream as paths of absolute tile pixels. */
fun decodeGeometry(commands: List<UInt>): JsonArray = buildJsonArray {
    var x = 0
    var y = 0
    var i = 0
    var path = mutableListOf<JsonElement>()
    fun flush() {
        if (path.isNotEmpty()) add(JsonArray(path))
        path = mutableListOf()
    }
    while (i < commands.size) {
        val word = commands[i++].toInt()
        val id = word and 0x7
        val count = word ushr 3
        when (id) {
            Command.MoveTo, Command.LineTo -> repeat(count) {
                if (id == Command.MoveTo) flush()
                x += zigZagDecode(commands[i++].toInt())
                y += zigZagDecode(commands[i++].toInt())
                path.add(JsonArray(listOf(JsonPrimitive(x), JsonPrimitive(y))))
            }

            Command.ClosePath -> {
                path.firstOrNull()?.let { path.add(it) }
                flush()
            }

            else -> error("unknown MVT command $id")
        }
    }
    flush()
}
