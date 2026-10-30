import io.madrona.njord.Singletons
import io.madrona.njord.db.BaseFeatureDao
import io.madrona.njord.db.ChartDao
import io.madrona.njord.db.FeatureDao
import io.madrona.njord.geo.TileEncoder
import io.madrona.njord.model.ChartFeatureInfo
import io.madrona.njord.testdata.fixtures
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import tile.decodeMvtToJson

@Serializable
data class TileXyz(val z: Int, val x: Int, val y: Int)

/**
 * The decoded MVT view with everything that is legitimately unstable removed: feature ids (an
 * autoincrement in insertion order, which follows PostGIS row order), layer order (encounter
 * order) and feature order within a layer. Layers sort by name, features by `LNAM`, then by their
 * sorted props, then by geometry. Geometry stays as tile-pixel paths, so a one-pixel change in the
 * clipping or projection maths is a visible diff.
 */
fun canonicalTile(view: JsonObject): JsonObject = buildJsonObject {
    val layers = view["layers"]!!.jsonArray.map { it.jsonObject }.sortedBy { it["name"]!!.jsonPrimitive.content }
    put("layers", JsonArray(layers.map { layer ->
        buildJsonObject {
            put("name", layer["name"]!!)
            put("extent", layer["extent"]!!)
            val features = layer["features"]!!.jsonArray.map { f ->
                f.jsonObject.let {
                    buildJsonObject {
                        put("type", it["type"]!!)
                        put("props", it["props"]!!.jsonObject.sortedKeys())
                        put("geometry", it["geometry"]!!)
                    }
                }
            }
            put("features", JsonArray(features.sortedWith(featureOrder)))
        }
    }))
}

/** `?info=true` output, canonicalised the same way: sorted by layer, LNAM, props, geometry. */
fun canonicalInfo(info: List<ChartFeatureInfo>): JsonArray {
    val encoded = fixtures.json.encodeToJsonElement(ListSerializer(ChartFeatureInfo.serializer()), info).jsonArray
    val normalised = encoded.map { e ->
        e.jsonObject.let {
            buildJsonObject {
                put("layer", it["layer"]!!)
                put("props", it["props"]!!.jsonObject.sortedKeys())
                put("geom", it["geom"] ?: JsonObject(emptyMap()))
            }
        }
    }
    return JsonArray(normalised.sortedWith(
        compareBy<JsonObject>({ it["layer"].toString() }, { it["props"]!!.jsonObject["LNAM"]?.toString() ?: "" },
            { it["props"].toString() }, { it["geom"].toString() })
    ))
}

private val featureOrder = compareBy<JsonObject>(
    { it["props"]!!.jsonObject["LNAM"]?.toString() ?: "" },
    { it["props"].toString() },
    { it["geometry"].toString() },
    { it["type"].toString() },
)

fun JsonObject.sortedKeys(): JsonObject = JsonObject(entries.sortedBy { it.key }.associate { it.key to it.value })

/** A TileEncoder on the test database, otherwise configured exactly as TileDao builds it. */
fun testTileEncoder(t: TileXyz): TileEncoder = TileEncoder(
    x = t.x, y = t.y, z = t.z,
    chartDao = ChartDao(TestEnv.ds, FeatureDao(TestEnv.ds)),
    baseFeatureDao = BaseFeatureDao(TestEnv.ds),
)

suspend fun encodeCanonicalTile(t: TileXyz): JsonObject {
    val encoder = testTileEncoder(t)
    encoder.addCharts(false)
    return canonicalTile(decodeMvtToJson(encoder.encode()))
}

suspend fun encodeCanonicalInfo(t: TileXyz): JsonArray {
    val encoder = testTileEncoder(t)
    encoder.addCharts(true)
    return canonicalInfo(encoder.infoJson())
}

/** The header `source` every DB-backed golden fixture carries: data, GDAL and the thresholds that drive symbology. */
fun goldenSource(): String {
    val c = Singletons.config
    return "fixtures/US5WA22M (edition 40 update 4), fixtures/synthetic/US4ZZ01M+US5ZZ02M, libgdal ne_110_extract coastline at 110m; " +
            "GDAL ${Gdal.gdalVersion()}; shallow/safety/deep = ${c.shallowDepth}/${c.safetyDepth}/${c.deepDepth}"
}

fun JsonObject.layerNames(): List<String> = this["layers"]!!.jsonArray.map { it.jsonObject["name"]!!.jsonPrimitive.content }

fun JsonObject.features(layer: String): List<JsonObject> =
    this["layers"]!!.jsonArray.map { it.jsonObject }.firstOrNull { it["name"]!!.jsonPrimitive.content == layer }
        ?.get("features")?.jsonArray?.map { it.jsonObject } ?: emptyList()

fun JsonObject.prop(name: String): JsonElement? = this["props"]!!.jsonObject[name]
