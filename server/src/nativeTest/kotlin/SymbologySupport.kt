import io.madrona.njord.Singletons
import io.madrona.njord.geo.symbols.S57ObjectLibrary
import io.madrona.njord.model.ChartFeature
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Shared pieces of the symbology characterization tests (P-SY-1 … P-SY-6).
 */

/** The input side of a `preTileEncode` case: the S-57 props as GDAL maps them, the geometry primitive and the TOPMAR association. */
@Serializable
data class SymbologyInput(
    val props: JsonObject,
    val geometry_type: String = "POINT",
    val assoc: List<String> = emptyList(),
)

val GEOMETRY_TYPES = listOf("POINT", "LINESTRING", "POLYGON")

fun SymbologyInput.toFeature(layer: String): ChartFeature = ChartFeature(
    layer = layer,
    geomWKB = null,
    props = props.toMutableMap(),
    associatedLayerNames = assoc,
)

fun props(vararg pairs: Pair<String, Any?>): JsonObject = JsonObject(pairs.filter { it.second != null }.associate { (k, v) -> k to v!!.toJson() })

fun Any.toJson(): JsonElement = when (this) {
    is JsonElement -> this
    is Int -> JsonPrimitive(this)
    is Long -> JsonPrimitive(this)
    is Double -> JsonPrimitive(this)
    is Float -> JsonPrimitive(this)
    is Boolean -> JsonPrimitive(this)
    is String -> JsonPrimitive(this)
    is List<*> -> JsonArray(map { it!!.toJson() })
    else -> error("no JSON form for ${this::class.simpleName}")
}

/**
 * A props map that records which keys a `preTileEncode` *reads* before the layer itself writes
 * them, so the input sweep of P-SY-1 can be derived from the attributes a layer actually consults
 * instead of being guessed from its source. Writes (`SY`, `AC`, `METERS_W`, …) are not reads.
 */
class RecordingProps(
    private val backing: MutableMap<String, JsonElement> = mutableMapOf(),
) : MutableMap<String, JsonElement> by backing {
    val reads = mutableSetOf<String>()
    private val written = mutableSetOf<String>()

    override fun get(key: String): JsonElement? {
        if (key !in written) reads += key
        return backing[key]
    }

    override fun containsKey(key: String): Boolean {
        if (key !in written) reads += key
        return backing.containsKey(key)
    }

    override fun put(key: String, value: JsonElement): JsonElement? {
        written += key
        return backing.put(key, value)
    }
}

/**
 * Candidate values for an S-57 attribute, from its type in the object catalogue: every expected
 * input of an enumerated (`E`) or list (`L`) attribute plus `0` and `max + 1` for the unknown
 * value - list values as arrays of *strings*, which is how GDAL's S-57 driver emits them (see the
 * P-SY-2 cell fixtures: `"COLOUR": ["6"]`); the configured depth thresholds and their neighbours for floats (the shipped
 * `shallowDepth`/`safetyDepth`/`deepDepth` of 3/6/9 m); a few magnitudes for integers; a few
 * strings for text. Unknown keys (`METERS`, `_x`) are treated as floats.
 */
class AttributeCandidates(private val lib: S57ObjectLibrary = Singletons.s57ObjectLibrary) {

    private val floats = listOf(-1.5, 0.0, 0.5, 2.9, 3.0, 3.1, 5.9, 6.0, 6.1, 8.9, 9.0, 9.1, 10.0, 15.0, 100.0)
    private val floatsReduced = listOf(0.0, 3.0, 6.0, 10.0)
    private val ints = listOf(0, 1, 5, 10, 45_000)
    private val intsReduced = listOf(0, 10)
    private val strings = listOf("", "(1)", "(2)", "Test")
    private val stringsReduced = listOf("", "Test")

    fun type(key: String): String = lib.attributes[key]?.attributeType ?: "F"

    /** The catalogue's expected-input IDs for an enumerated or list attribute, ascending. */
    fun enumIds(key: String): List<Int> {
        val code = lib.attributes[key]?.code ?: return emptyList()
        return lib.expectedInput[code.toString()]?.map { it.id }?.sorted() ?: emptyList()
    }

    /** Every value worth trying on its own. */
    fun full(key: String): List<JsonElement> = when (type(key)) {
        "E" -> enumValues(key).map { JsonPrimitive(it) }
        "L" -> enumValues(key).map { JsonArray(listOf(JsonPrimitive(it.toString()))) } + listPairs(key)
        "I" -> ints.map { JsonPrimitive(it) }
        "A", "S" -> strings.map { JsonPrimitive(it) }
        else -> floats.map { JsonPrimitive(it) }
    }

    /** A small subset, for pairwise combinations. */
    fun reduced(key: String): List<JsonElement> = when (type(key)) {
        "E" -> (enumIds(key).take(3) + unknown(key)).map { JsonPrimitive(it) }
        "L" -> (enumIds(key).take(3) + unknown(key)).map { JsonArray(listOf(JsonPrimitive(it.toString()))) }
        "I" -> intsReduced.map { JsonPrimitive(it) }
        "A", "S" -> stringsReduced.map { JsonPrimitive(it) }
        else -> floatsReduced.map { JsonPrimitive(it) }
    }

    private fun unknown(key: String) = (enumIds(key).maxOrNull() ?: 0) + 1

    private fun enumValues(key: String): List<Int> = (listOf(0) + enumIds(key) + unknown(key)).distinct()

    /** An empty list and the first two IDs together, since list attributes are often `any {}` / `firstOrNull {}` decisions. */
    private fun listPairs(key: String): List<JsonElement> {
        val ids = enumIds(key)
        return listOf(JsonArray(emptyList())) +
                if (ids.size >= 2) listOf(JsonArray(listOf(JsonPrimitive(ids[0].toString()), JsonPrimitive(ids[1].toString())))) else emptyList()
    }
}
