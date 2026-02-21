@file:OptIn(ExperimentalForeignApi::class)

package tile

import Gdal.epsg3857
import OgrGeometry
import io.madrona.njord.geojson.Position
import kotlinx.cinterop.ExperimentalForeignApi
import tile.VectorTileDecoder.Companion.decodeGeometry
import kotlin.math.round


class VectorTileEncoder(
    private val extent: Int = 4096,
    clipBuffer: Int = 8,
    private val autoScale: Boolean = false,
    private val simplificationDistanceTolerance: Double = 0.1
) {
    private val layers: MutableMap<String, Layer> = LinkedHashMap<String, Layer>()

    private var autoincrement: Long = 1

    private val minimumLength: Double = if (autoScale) (256.0 / extent) else 1.0

    private val minimumArea: Double = minimumLength * minimumLength

    private val clipGeometry: OgrGeometry = createTileEnvelope(clipBuffer, if (autoScale) 256 else extent)

    private val clipEnvelope: OgrGeometry = clipGeometry.envelopeGeometry()

    private val clipGeometryPrepared: OgrGeometry = clipGeometry //.prepare()

    private var x = 0
    private var y = 0

    fun addFeature(
        layerName: String,
        attributes: Map<String, *>,
        geo: OgrGeometry?,
    ) {
        if (geo == null) {
            return
        } else {
            addFeature(layerName, attributes, geo, ++autoincrement)
        }
    }

    private fun addFeature(
        layerName: String,
        attributes: Map<String, *>,
        geo: OgrGeometry?,
        id: Long
    ) {
        if (geo == null) {
            return
        }
        var geometry = geo
        if (geometry.type == GeomType.MultiPolygon && geometry.area < minimumArea) {
            return
        }
        if (geometry.type == GeomType.Polygon && geometry.area < minimumArea) {
            return
        }

        if (geometry.type == GeomType.LineString && geometry.length < minimumLength) {
            return
        }

        if (geometry.type == GeomType.LinearRing && geometry.length < minimumLength) {
            return
        }

        if (geometry.type == GeomType.MultiLineString && geometry.length < minimumLength) {
            return
        }

        // special handling of GeometryCollection. subclasses are not handled here.
        if (geometry.type == GeomType.GeometryCollection) {
            for (i in 0..<geometry.numGeometries) {
                val subGeometry = geometry.getGeometryN(i)
                // keeping the id. any better suggestion?
                addFeature(layerName, attributes, subGeometry, id)
            }
            return
        }

        // About to simplify and clip. Looks like simplification before clipping is
        // faster than clipping before simplification

        // simplify non-points
        geometry = if (simplificationDistanceTolerance > 0.0) {
            when (geometry.type) {
                GeomType.LineString,
                GeomType.LinearRing,
                GeomType.MultiLineString -> {
                    geometry.simplifyPreserveTopology(simplificationDistanceTolerance)
                }

                GeomType.Polygon,
                GeomType.MultiPoint,
                GeomType.MultiPolygon,
                GeomType.GeometryCollection -> geometry.simplify(simplificationDistanceTolerance)

                GeomType.Unknown,
                GeomType.Point -> geometry
            }
        } else {
            geometry
        }

        // clip geometry
        if (geometry?.type == GeomType.Point) {
            if (!clipCovers(geometry)) {
                return
            }
        } else if (geometry != null) {
            geometry = clipGeometry(geometry)
        }

        // no need to add empty geometry
        if (geometry == null || geometry.isEmpty()) {
            return
        }

        // extra check for GeometryCollection after clipping as it can cause
        // GeometryCollection. Subclasses not handled here.
        if (geometry.type == GeomType.GeometryCollection) {
            for (i in 0..<geometry.numGeometries) {
                val subGeometry = geometry.getGeometryN(i)
                // keeping the id. any better suggestion?
                addFeature(layerName, attributes, subGeometry, id)
            }
            return
        }

        var layer = layers[layerName]
        if (layer == null) {
            layer = Layer()
            layers[layerName] = layer
        }

        val tags = mutableListOf<Int>()
        for (e in attributes.entries) {
            // skip attribute without value
            val value = e.value ?: continue

            tags.add(layer.key(e.key))
            tags.add(layer.value(value))
        }

        layer.features.add(
            Feature(
//                layerName = layerName,
//                extent = extent,
                geometry = geometry,
                tags = tags,
//                attributes = attributes,
                id = id
            )
        )
    }

    private fun clipCovers(geom: OgrGeometry): Boolean {
        if (geom.type == GeomType.Point) {
            return clipEnvelope.contains(geom)
        }
        return geom.envelopeGeometry().let { clipEnvelope.contains(it) }
    }

    private fun clipGeometry(geometry: OgrGeometry): OgrGeometry? {
        if (clipEnvelope.contains(geometry.envelopeGeometry())) {
            return geometry
        }

        val intersection = clipGeometry.intersection(geometry)

        // Sometimes an intersection is returned as an empty geometry.
        // going via wkb fixes the problem.
        if ((intersection == null || intersection.isEmpty()) && clipGeometryPrepared.intersects(geometry)) {
            val wkb = geometry.wkb
            return clipGeometry.intersection(OgrGeometry.fromWkb(wkb, epsg3857))
        }

        return intersection
    }

    private fun validateAndRepairCommands(commands: List<Int>, geometry: OgrGeometry): List<Int> {
        var geometry: OgrGeometry? = geometry
        if (commands.isEmpty()) {
            return commands
        }

        val geomType: Tile.GeomType? = geometry?.type?.tileType
        if (simplificationDistanceTolerance > 0.0 && geomType == Tile.GeomType.POLYGON) {
            val scale = if (autoScale) (extent / 256.0) else 1.0
            val decodedGeometry = decodeGeometry(geomType, commands, scale)
            if (decodedGeometry?.isValid != true) {
                // Invalid. Try more simplification and without preserving topology.
                geometry = geometry.simplify(simplificationDistanceTolerance * 2.0)
                if (geometry == null || geometry.isEmpty()) {
                    mutableListOf<Any?>()
                }
                x = 0
                y = 0
                return commands(geometry)
            }
        }

        return commands
    }

    /**
     * @return a byte array with the vector tile
     */
    fun encode(): ByteArray {
        return Tile.encodeTile(
            Tile(
                layers = layers.entries.map { entry ->
                    val layerName = entry.key
                    val layer = entry.value
                    val values = layer.values().map { value ->
                        val tileValue: Tile.Value
                        if (value is String) {
                            tileValue = Tile.Value(stringValue = value)
                        } else if (value is Int) {
                            tileValue = Tile.Value(intValue = value.toLong())
                        } else if (value is Long) {
                            tileValue = Tile.Value(sintValue = value)
                        } else if (value is Float) {
                            tileValue = Tile.Value(floatValue = value)
                        } else if (value is Double) {
                            tileValue = Tile.Value(doubleValue = value)
                        } else if (value is String) {
                            tileValue = Tile.Value(stringValue = value)
                        } else if (value is Number) {
                            tileValue = Tile.Value(doubleValue = value.toDouble())
                        } else if (value is Boolean) {
                            tileValue = Tile.Value(boolValue = value)
                        } else {
                            tileValue = Tile.Value(stringValue = value.toString())
                        }
                        tileValue
                    }

                    val features = layer.features.mapNotNull { feature ->
                        val geometry = feature.geometry
                        val geomType: Tile.GeomType = geometry.type.tileType
                        x = 0
                        y = 0
                        val commands = validateAndRepairCommands(commands(geometry), geometry)
                        // skip features with no geometry commands
                        if (commands.isEmpty()) {
                            null
                        } else {
                            Tile.Feature(
                                id = feature.id.toULong(),
                                tags = feature.tags.map { it.toUInt() },
                                type = geomType,
                                geometry = commands.map { it.toUInt() },
                            )
                        }

                    }
                    Tile.Layer(
                        version = 2,
                        name = layerName,
                        keys = layer.keys(),
                        features = features,
                        values = values,
                        extent = extent,
                    )
                }

            )
        )
    }

    fun commands(geometry: OgrGeometry?): List<Int> {
        return when (geometry?.type) {
            null,
            GeomType.Unknown -> emptyList()

            GeomType.Point,
            GeomType.LineString,
            GeomType.LinearRing -> commandsCoords(geometry.coordinateSequence(), shouldClosePath(geometry), false)

            GeomType.Polygon -> commandsPolygon(geometry)

            GeomType.MultiPoint -> commandsCoords(geometry.coordinateSequence(), shouldClosePath(geometry), true)
            GeomType.MultiLineString -> commandsMultiLineString(geometry)

            GeomType.MultiPolygon -> commandsMultiPolygon(geometry)
            GeomType.GeometryCollection -> throw IllegalStateException("commands not available for geometry collection")
        }
    }

    fun commandsMultiLineString(mls: OgrGeometry): List<Int> {
        return (0 until mls.numGeometries).flatMap { i ->
            val oldX = x
            val oldY = y
            val geomCommands =
                commandsCoords(mls.getGeometryN(i)?.coordinateSequence() ?: emptyList(), false)
            if (geomCommands.size > 3) {
                // if the geometry consists of all identical points (after Math.round()) commands
                // returns a single move_to command, which is not valid according to the vector tile
                // specifications.
                // (https://github.com/mapbox/vector-tile-spec/tree/master/2.1#4343-linestring-geometry-type)
                geomCommands
            } else {
                // reset x and y to the previous value
                x = oldX
                y = oldY
                emptyList()
            }
        }
    }

    fun commandsCoords(
        cs: List<Position>,
        closePathAtEnd: Boolean,
        multiPoint: Boolean = false
    ): List<Int> {
        if (cs.isEmpty()) {
            return emptyList()
        }

        val r: MutableList<Int> = mutableListOf()

        var lineToIndex = 0
        var lineToLength = 0

        val scale = if (autoScale) (extent / 256.0) else 1.0

        for (i in cs.indices) {
            val c = cs[i]

            if (i == 0) {
                r.add(commandAndLength(Command.MoveTo, if (multiPoint) cs.size else 1))
            }

            val _x = round(c.x * scale).toInt()
            val _y = round(c.y * scale).toInt()

            // prevent point equal to the previous
            if (i > 0 && _x == x && _y == y) {
                lineToLength--
                continue
            }

            // prevent double closing
            if (closePathAtEnd && cs.size > 1 && i == (cs.size - 1) && cs[0] == c) {
                lineToLength--
                continue
            }

            // delta, then zigzag
            r.add(zigZagEncode(_x - x))
            r.add(zigZagEncode(_y - y))

            x = _x
            y = _y

            if (i == 0 && cs.size > 1 && !multiPoint) {
                // can length be too long?
                lineToIndex = r.size
                lineToLength = cs.size - 1
                r.add(commandAndLength(Command.LineTo, lineToLength))
            }
        }

        // update LineTo length
        if (lineToIndex > 0) {
            if (lineToLength == 0) {
                // remove empty LineTo
                r.removeAt(lineToIndex)
            } else {
                // update LineTo with new length
                r[lineToIndex] = commandAndLength(Command.LineTo, lineToLength)
            }
        }

        if (closePathAtEnd) {
            r.add(commandAndLength(Command.ClosePath, 1))
        }

        return r
    }

    fun commandsMultiPolygon(mp: OgrGeometry): List<Int> {
        return (0 until mp.numGeometries).flatMap { i ->
            mp.getGeometryN(i)?.let {
                commands(it)
            } ?: emptyList()
        }
    }

    fun commandsPolygon(polygon: OgrGeometry): List<Int> {
        val commands: MutableList<Int> = ArrayList()

        // According to the vector tile specification, the exterior ring of a polygon
        // must be in clockwise order, while the interior ring in counter-clockwise order.
        // In the tile coordinate system, Y axis is positive down.
        //
        // However, in geographic coordinate system, Y axis is positive up.
        // Therefore, we must reverse the coordinates.
        // So, the code below will make sure that exterior ring is in counter-clockwise order
        // and interior ring in clockwise order.
        //
        // OGR_G_GetArea on a LinearRing returns fabs(signedArea), always non-negative,
        // so we compute signed area via the shoelace formula to detect winding order.
        var exteriorRing: OgrGeometry? = polygon.getExteriorRing()
        val exteriorCs = exteriorRing?.coordinateSequence() ?: emptyList()
        if (signedArea(exteriorCs) > 0.0) {
            exteriorRing = exteriorRing?.reverse()
        }
        commands.addAll(commandsCoords(exteriorRing?.coordinateSequence() ?: exteriorCs, true))

        for (i in 0..<polygon.numInteriorRings) {
            var interiorRing: OgrGeometry? = polygon.getInteriorRingN(i)
            val interiorCs = interiorRing?.coordinateSequence() ?: emptyList()
            if (signedArea(interiorCs) < 0.0) {
                interiorRing = interiorRing?.reverse()
            }
            commands.addAll(commandsCoords(interiorRing?.coordinateSequence() ?: interiorCs, true))
        }
        return commands
    }

    private data class Feature(val geometry: OgrGeometry, val tags: MutableList<Int>, val id: Long)

    private class Layer {
        val features: MutableList<Feature> = ArrayList()

        private val keys: MutableMap<String, Int> = LinkedHashMap()
        private val values: MutableMap<Any, Int> = LinkedHashMap()

        fun key(key: String): Int {
            var i = keys[key]
            if (i == null) {
                i = keys.size
                keys[key] = i
            }
            return i
        }

        fun keys(): MutableList<String> {
            return ArrayList<String>(keys.keys)
        }

        fun value(value: Any): Int {
            var i = values[value]
            if (i == null) {
                i = values.size
                values[value] = i
            }
            return i
        }

        fun values(): List<Any> {
            return values.keys.toList()
        }
    }

    companion object {
        private fun createTileEnvelope(buffer: Int, size: Int): OgrGeometry {
            val cap = Position((0 - buffer).toDouble(), (size + buffer).toDouble())
            val coords = arrayOf(
                cap,
                Position((size + buffer).toDouble(), (size + buffer).toDouble()),
                Position((size + buffer).toDouble(), (0 - buffer).toDouble()),
                Position((0 - buffer).toDouble(), (0 - buffer).toDouble()),
                cap,
            )
            return Gdal.createPolygon(*coords)
        }


        fun shouldClosePath(geometry: OgrGeometry): Boolean {
            return when (geometry.type) {
                GeomType.LineString,
                GeomType.LinearRing,
                GeomType.Polygon -> true

                else -> false

            }
        }

        fun commandAndLength(command: Int, repeat: Int): Int {
            return repeat shl 3 or command
        }

        fun zigZagEncode(n: Int): Int {
            return (n shl 1) xor (n shr 31)
        }

        // Shoelace formula. Positive = CCW in EPSG:3857 (Y-up) = CW in tile space (Y-down).
        fun signedArea(cs: List<Position>): Double {
            var sum = 0.0
            val n = cs.size
            for (i in 0 until n) {
                val c0 = cs[i]
                val c1 = cs[(i + 1) % n]
                sum += (c0.x * c1.y) - (c1.x * c0.y)
            }
            return sum / 2.0
        }
    }
}
