package io.madrona

import io.madrona.VectorTileDecoder.Companion.decodeGeometry
import org.locationtech.jts.algorithm.Area
import org.locationtech.jts.geom.*
import org.locationtech.jts.geom.prep.PreparedGeometry
import org.locationtech.jts.geom.prep.PreparedGeometryFactory
import org.locationtech.jts.io.ParseException
import org.locationtech.jts.io.WKTReader
import org.locationtech.jts.simplify.DouglasPeuckerSimplifier
import org.locationtech.jts.simplify.TopologyPreservingSimplifier
import vector_tile.VectorTile
import vector_tile.VectorTile.Tile.GeomType
import java.math.BigDecimal
import java.util.*
import kotlin.math.max

class VectorTileEncoder @JvmOverloads constructor(
    private val extent: Int = 4096,
    clipBuffer: Int = 8,
    private val autoScale: Boolean = true,
    private val autoincrementIds: Boolean = false,
    private val simplificationDistanceTolerance: Double = -1.0
) {
    private val layers: MutableMap<String?, Layer?> = LinkedHashMap<String?, Layer?>()

    private val minimumLength: Double

    private val minimumArea: Double

    protected val clipGeometry: Geometry

    protected val clipEnvelope: Envelope

    protected val clipGeometryPrepared: PreparedGeometry

    private var autoincrement: Long = 1

    private val gf = GeometryFactory()

    /**
     * Add a feature with layer name (typically feature type name), some attributes
     * and a Geometry. The Geometry must be in "pixel" space 0,0 upper left and
     * 256,256 lower right.
     *
     *
     * For optimization, geometries will be clipped and simplified. Features with
     * geometries outside of the tile will be skipped.
     *
     * @param layerName a [String] with the vector tile layer name.
     * @param attributes a [Map] with the vector tile feature attributes.
     * @param geometry a [Geometry] for the vector tile feature.
     * @param id a long with the vector tile feature id field.
     */
    @JvmOverloads
    fun addFeature(
        layerName: String?,
        attributes: MutableMap<String?, *>,
        geometry: Geometry?,
        id: Long = if (this.autoincrementIds) this.autoincrement++ else -1
    ) {
        // skip small Polygon/LineString.

        var geometry = geometry
        if (geometry is MultiPolygon && geometry.getArea() < minimumArea) {
            return
        }
        if (geometry is Polygon && geometry.getArea() < minimumArea) {
            return
        }
        if (geometry is LineString && geometry.getLength() < minimumLength) {
            return
        }

        // special handling of GeometryCollection. subclasses are not handled here.
        if (geometry!!.javaClass == GeometryCollection::class.java) {
            for (i in 0..<geometry.getNumGeometries()) {
                val subGeometry = geometry.getGeometryN(i)
                // keeping the id. any better suggestion?
                addFeature(layerName, attributes, subGeometry, id)
            }
            return
        }

        // About to simplify and clip. Looks like simplification before clipping is
        // faster than clipping before simplification

        // simplify non-points
        if (simplificationDistanceTolerance > 0.0 && geometry !is Point) {
            if (geometry is LineString || geometry is MultiLineString) {
                geometry = DouglasPeuckerSimplifier.simplify(geometry, simplificationDistanceTolerance)
            } else if (geometry is Polygon || geometry is MultiPolygon) {
                val simplified = DouglasPeuckerSimplifier.simplify(geometry, simplificationDistanceTolerance)
                // extra check to prevent polygon converted to line
                if (simplified is Polygon || simplified is MultiPolygon) {
                    geometry = simplified
                } else {
                    geometry = TopologyPreservingSimplifier.simplify(geometry, simplificationDistanceTolerance)
                }
            } else {
                geometry = TopologyPreservingSimplifier.simplify(geometry, simplificationDistanceTolerance)
            }
        }

        // clip geometry
        if (geometry is Point) {
            if (!clipCovers(geometry)) {
                return
            }
        } else {
            geometry = clipGeometry(geometry!!)
        }

        // no need to add empty geometry
        if (geometry == null || geometry.isEmpty()) {
            return
        }

        // extra check for GeometryCollection after clipping as it can cause
        // GeometryCollection. Subclasses not handled here.
        if (geometry.javaClass == GeometryCollection::class.java) {
            for (i in 0..<geometry.getNumGeometries()) {
                val subGeometry = geometry.getGeometryN(i)
                // keeping the id. any better suggestion?
                addFeature(layerName, attributes, subGeometry, id)
            }
            return
        }

        var layer = layers.get(layerName)
        if (layer == null) {
            layer = Layer()
            layers.put(layerName, layer)
        }

        val feature = Feature()
        feature.geometry = geometry
        feature.id = id
        this.autoincrement = max(this.autoincrement, id + 1)

        for (e in attributes.entries) {
            // skip attribute without value
            if (e.value == null) {
                continue
            }
            feature.tags.add(layer.key(e.key))
            feature.tags.add(layer.value(e.value))
        }

        layer.features.add(feature)
    }

    /**
     * A short circuit clip to the tile extent (tile boundary + buffer) for
     * points to improve performance. This method can be overridden to change
     * clipping behavior. See also [.clipGeometry].
     *
     * @param geom a [Geometry] to check for "covers"
     * @return a boolean true when the current clip geometry covers the given geom.
     */
    protected fun clipCovers(geom: Geometry): Boolean {
        if (geom is Point) {
            val p = geom
            return clipGeometry.getEnvelopeInternal().covers(p.getCoordinate())
        }
        return clipEnvelope.covers(geom.getEnvelopeInternal())
    }

    /**
     * Clip geometry according to buffer given at construct time. This method
     * can be overridden to change clipping behavior. See also
     * [.clipCovers].
     *
     * @param geometry a [Geometry] to check for intersection with the current clip geometry
     * @return a boolean true when current clip geometry intersects with the given geometry.
     */
    protected fun clipGeometry(geometry: Geometry): Geometry {
        var geometry = geometry
        try {
            if (clipEnvelope.contains(geometry.getEnvelopeInternal())) {
                return geometry
            }

            val original = geometry
            geometry = clipGeometry.intersection(original)

            // some times a intersection is returned as an empty geometry.
            // going via wkt fixes the problem.
            if (geometry.isEmpty() && clipGeometryPrepared.intersects(original)) {
                val originalViaWkt = WKTReader().read(original.toText())
                geometry = clipGeometry.intersection(originalViaWkt)
            }

            return geometry
        } catch (e: TopologyException) {
            // could not intersect. original geometry will be used instead.
            return geometry
        } catch (e1: ParseException) {
            // could not encode/decode WKT. original geometry will be used
            // instead.
            return geometry
        }
    }

    /**
     * Validate and potentially repair the given [List] of commands for the
     * given [Geometry]. Will return a [List] of the validated and/or
     * repaired commands.
     *
     *
     * This can be overridden to change behavior. By returning just the incoming
     * [List] of commands instead, the encoding will be faster, but
     * potentially less safe.
     *
     * @param commands
     * @param geometry
     * @return
     */
    protected fun validateAndRepairCommands(commands: MutableList<Int?>, geometry: Geometry): MutableList<Int?> {
        var geometry = geometry
        if (commands.isEmpty()) {
            return commands
        }

        var geomType: GeomType = toGeomType(geometry)
        if (simplificationDistanceTolerance > 0.0 && geomType == GeomType.POLYGON) {
            val scale = if (autoScale) (extent / 256.0) else 1.0
            val decodedGeometry = decodeGeometry(gf, geomType, commands, scale)
            if (!Companion.isValid(decodedGeometry!!)) {
                // Invalid. Try more simplification and without preserving topology.
                geometry = DouglasPeuckerSimplifier.simplify(geometry, simplificationDistanceTolerance * 2.0)
                if (geometry.isEmpty()) {
                    mutableListOf<Any?>()
                }
                geomType = toGeomType(geometry)
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
    fun encode(): ByteArray? {
        val tile = VectorTile.Tile.newBuilder()

        for (e in layers.entries) {
            val layerName: String = e.key!!
            val layer: Layer = e.value!!

            val tileLayer = VectorTile.Tile.Layer.newBuilder()

            tileLayer.setVersion(2)
            tileLayer.setName(layerName)

            tileLayer.addAllKeys(layer.keys())

            for (value in layer.values()) {
                val tileValue = VectorTile.Tile.Value.newBuilder()
                if (value is String) {
                    tileValue.setStringValue(value)
                } else if (value is Int) {
                    tileValue.setSintValue(value.toLong())
                } else if (value is Long) {
                    tileValue.setSintValue(value)
                } else if (value is Float) {
                    tileValue.setFloatValue(value)
                } else if (value is Double) {
                    tileValue.setDoubleValue(value)
                } else if (value is BigDecimal) {
                    tileValue.setStringValue(value.toString())
                } else if (value is Number) {
                    tileValue.setDoubleValue(value.toDouble())
                } else if (value is Boolean) {
                    tileValue.setBoolValue(value)
                } else {
                    tileValue.setStringValue(value.toString())
                }
                tileLayer.addValues(tileValue.build())
            }

            tileLayer.setExtent(extent)

            for (feature in layer.features) {
                val geometry = feature.geometry

                val featureBuilder = VectorTile.Tile.Feature.newBuilder()

                featureBuilder.addAllTags(feature.tags)
                if (feature.id >= 0) {
                    featureBuilder.setId(feature.id)
                }

                val geomType: GeomType = toGeomType(geometry)
                x = 0
                y = 0
                var commands = commands(geometry!!)

                // Extra step to parse and check validity and try to repair.
                commands = validateAndRepairCommands(commands, geometry)

                // skip features with no geometry commands
                if (commands.isEmpty()) {
                    continue
                }

                featureBuilder.setType(geomType)
                featureBuilder.addAllGeometry(commands)

                tileLayer.addFeatures(featureBuilder.build())
            }

            tile.addLayers(tileLayer.build())
        }

        return tile.build().toByteArray()
    }

    fun commands(geometry: Geometry): MutableList<Int?> {
        if (geometry is MultiLineString) {
            return commands(geometry)
        }
        if (geometry is Polygon) {
            return commands(geometry)
        }
        if (geometry is MultiPolygon) {
            return commands(geometry)
        }

        return commands(geometry.getCoordinates(), shouldClosePath(geometry), geometry is MultiPoint)
    }

    fun commands(mls: MultiLineString): MutableList<Int?> {
        val commands: MutableList<Int?> = ArrayList<Int?>()
        for (i in 0..<mls.getNumGeometries()) {
            val oldX = x
            val oldY = y
            val geomCommands =
                commands(mls.getGeometryN(i).getCoordinates(), false)
            if (geomCommands.size > 3) {
                // if the geometry consists of all identical points (after Math.round()) commands
                // returns a single move_to command, which is not valid according to the vector tile
                // specifications.
                // (https://github.com/mapbox/vector-tile-spec/tree/master/2.1#4343-linestring-geometry-type)
                commands.addAll(geomCommands)
            } else {
                // reset x and y to the previous value
                x = oldX
                y = oldY
            }
        }
        return commands
    }

    fun commands(mp: MultiPolygon): MutableList<Int?> {
        val commands: MutableList<Int?> = ArrayList<Int?>()
        for (i in 0..<mp.getNumGeometries()) {
            val polygon = mp.getGeometryN(i) as Polygon
            commands.addAll(commands(polygon))
        }
        return commands
    }

    fun commands(polygon: Polygon): MutableList<Int?> {
        val commands: MutableList<Int?> = ArrayList<Int?>()

        // According to the vector tile specification, the exterior ring of a polygon
        // must be in clockwise order, while the interior ring in counter-clockwise order.
        // In the tile coordinate system, Y axis is positive down.
        //
        // However, in geographic coordinate system, Y axis is positive up.
        // Therefore, we must reverse the coordinates.
        // So, the code below will make sure that exterior ring is in counter-clockwise order
        // and interior ring in clockwise order.
        var exteriorRing: LineString = polygon.getExteriorRing()
        if (Area.ofRingSigned(exteriorRing.getCoordinates()) > 0) {
            exteriorRing = exteriorRing.reverse()
        }
        commands.addAll(commands(exteriorRing.getCoordinates(), true))

        for (i in 0..<polygon.getNumInteriorRing()) {
            var interiorRing: LineString = polygon.getInteriorRingN(i)
            if (Area.ofRingSigned(interiorRing.getCoordinates()) < 0) {
                interiorRing = interiorRing.reverse()
            }
            commands.addAll(commands(interiorRing.getCoordinates(), true))
        }
        return commands
    }

    private var x = 0
    private var y = 0

    /**
     * Create a [VectorTileEncoder] with the given extent value.
     *
     *
     * The extent value control how detailed the coordinates are encoded in the
     * vector tile. 4096 is a good default, 256 can be used to reduce density.
     *
     *
     * The clip buffer value control how large the clipping area is outside of the
     * tile for geometries. 0 means that the clipping is done at the tile border. 8
     * is a good default.
     *
     * @param extent
     * a int with extent value. 4096 is a good value.
     * @param clipBuffer
     * a int with clip buffer size for geometries. 8 is a good value.
     * @param autoScale
     * when true, the encoder expects coordinates in the 0..255 range and
     * will scale them automatically to the 0..extent-1 range before
     * encoding. when false, the encoder expects coordinates in the
     * 0..extent-1 range.
     * @param autoincrementIds
     * when true the vector tile feature id is auto incremented when using
     * [.addFeature]
     * @param simplificationDistanceTolerance
     * a positive double representing the distance tolerance to be used
     * for non-points before (optional) scaling and encoding. A value
     * &lt;=0 will prevent simplifying geometry. 0.1 seems to be a good
     * value when `autoScale` is turned on.
     */
    /**
     * Create a [VectorTileEncoder] with the default extent of 4096 and
     * clip buffer of 8.
     */
    /**
     * Create a [VectorTileEncoder] with the given extent and a clip
     * buffer of 8.
     *
     * @param extent a int to specify vector tile extent. 4096 is a good value.
     */
    init {
        this.minimumLength = if (autoScale) (256.0 / extent) else 1.0
        this.minimumArea = this.minimumLength * this.minimumLength

        val size = if (autoScale) 256 else extent
        clipGeometry = createTileEnvelope(clipBuffer, size)
        clipEnvelope = clipGeometry.getEnvelopeInternal()
        clipGeometryPrepared = PreparedGeometryFactory.prepare(clipGeometry)
    }

    /**
     * // // // Ex.: MoveTo(3, 6), LineTo(8, 12), LineTo(20, 34), ClosePath //
     * Encoded as: [ 9 3 6 18 5 6 12 22 15 ] // == command type 7 (ClosePath),
     * length 1 // ===== relative LineTo(+12, +22) == LineTo(20, 34) // ===
     * relative LineTo(+5, +6) == LineTo(8, 12) // == [00010 010] = command type
     * 2 (LineTo), length 2 // === relative MoveTo(+3, +6) // == [00001 001] =
     * command type 1 (MoveTo), length 1 // Commands are encoded as uint32
     * varints, vertex parameters are // encoded as sint32 varints (zigzag).
     * Vertex parameters are // also encoded as deltas to the previous position.
     * The original // position is (0,0)
     *
     * @param cs
     * @return
     */
    @JvmOverloads
    fun commands(cs: Array<Coordinate>, closePathAtEnd: Boolean, multiPoint: Boolean = false): MutableList<Int?> {
        if (cs.size == 0) {
            return mutableListOf<Int?>()
        }

        val r: MutableList<Int?> = ArrayList<Int?>()

        var lineToIndex = 0
        var lineToLength = 0

        val scale = if (autoScale) (extent / 256.0) else 1.0

        for (i in cs.indices) {
            val c = cs[i]

            if (i == 0) {
                r.add(commandAndLength(Command.MoveTo, if (multiPoint) cs.size else 1))
            }

            val _x = Math.round(c.x * scale).toInt()
            val _y = Math.round(c.y * scale).toInt()

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
                r.set(lineToIndex, commandAndLength(Command.LineTo, lineToLength))
            }
        }

        if (closePathAtEnd) {
            r.add(commandAndLength(Command.ClosePath, 1))
        }

        return r
    }

    private class Layer {
        val features: MutableList<Feature> = ArrayList<Feature>()

        private val keys: MutableMap<String?, Int?> = LinkedHashMap<String?, Int?>()
        private val values: MutableMap<Any?, Int?> = LinkedHashMap<Any?, Int?>()

        fun key(key: String?): Int {
            var i = keys.get(key)
            if (i == null) {
                i = keys.size
                keys.put(key, i)
            }
            return i
        }

        fun keys(): MutableList<String?> {
            return ArrayList<String?>(keys.keys)
        }

        fun value(value: Any?): Int {
            var i = values.get(value)
            if (i == null) {
                i = values.size
                values.put(value, i)
            }
            return i
        }

        fun values(): MutableList<Any> {
            return Collections.unmodifiableList<Any?>(ArrayList<Any?>(values.keys))
        }
    }

    private class Feature {
        var id: Long = 0
        var geometry: Geometry? = null
        val tags: MutableList<Int?> = ArrayList<Int?>()
    }

    companion object {
        private fun createTileEnvelope(buffer: Int, size: Int): Geometry {
            val coords = arrayOfNulls<Coordinate>(5)
            coords[0] = Coordinate((0 - buffer).toDouble(), (size + buffer).toDouble())
            coords[1] = Coordinate((size + buffer).toDouble(), (size + buffer).toDouble())
            coords[2] = Coordinate((size + buffer).toDouble(), (0 - buffer).toDouble())
            coords[3] = Coordinate((0 - buffer).toDouble(), (0 - buffer).toDouble())
            coords[4] = coords[0]
            return GeometryFactory().createPolygon(coords)
        }

        private fun isValid(geometry: Geometry): Boolean {
            try {
                return geometry.isValid()
            } catch (e: RuntimeException) {
                return false
            }
        }

        fun toGeomType(geometry: Geometry?): GeomType {
            if (geometry is Point) {
                return GeomType.POINT
            }
            if (geometry is MultiPoint) {
                return GeomType.POINT
            }
            if (geometry is LineString) {
                return GeomType.LINESTRING
            }
            if (geometry is MultiLineString) {
                return GeomType.LINESTRING
            }
            if (geometry is Polygon) {
                return GeomType.POLYGON
            }
            if (geometry is MultiPolygon) {
                return GeomType.POLYGON
            }
            return GeomType.UNKNOWN
        }

        fun shouldClosePath(geometry: Geometry?): Boolean {
            return (geometry is Polygon) || (geometry is LinearRing)
        }

        fun commandAndLength(command: Int, repeat: Int): Int {
            return repeat shl 3 or command
        }

        fun zigZagEncode(n: Int): Int {
            // https://developers.google.com/protocol-buffers/docs/encoding#types
            return (n shl 1) xor (n shr 31)
        }
    }
}
