/*****************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package io.madrona

import org.locationtech.jts.algorithm.Area
import org.locationtech.jts.geom.*
import vector_tile.VectorTile
import vector_tile.VectorTile.Tile.GeomType
import java.io.IOException
import java.util.*

class VectorTileDecoder {
    /**
     * Get the autoScale setting.
     *
     * @return autoScale
     */
    /**
     * Set the autoScale setting.
     *
     * @param this.isAutoScale
     * when true, the encoder automatically scale and return all coordinates in the 0..255 range.
     * when false, the encoder returns all coordinates in the 0..extent-1 range as they are encoded.
     */
    var isAutoScale: Boolean = true

    @Throws(IOException::class)
    fun decode(data: ByteArray?, layerName: String): FeatureIterable {
        return decode(data, Filter.Single(layerName))
    }

    @Throws(IOException::class)
    fun decode(data: ByteArray?, layerNames: MutableSet<String?>): FeatureIterable {
        return decode(data, Filter.Any(layerNames))
    }

    @JvmOverloads
    @Throws(IOException::class)
    fun decode(data: ByteArray?, filter: Filter = Filter.ALL): FeatureIterable {
        val tile = VectorTile.Tile.parseFrom(data)
        return FeatureIterable(tile, filter, this.isAutoScale)
    }

    class FeatureIterable(
        private val tile: VectorTile.Tile,
        private val filter: Filter,
        private val autoScale: Boolean
    ) : Iterable<Feature?> {
        override fun iterator(): MutableIterator<Feature?> {
            return FeatureIterator(tile, filter, autoScale)
        }

        fun asList(): MutableList<Feature?> {
            val features: MutableList<Feature?> = ArrayList<Feature?>()
            for (feature in this) {
                features.add(feature)
            }
            return features
        }

        val layerNames: MutableCollection<String?>
            get() {
                val layerNames: MutableSet<String?> = HashSet<String?>()
                for (layer in tile.getLayersList()) {
                    layerNames.add(layer.getName())
                }
                return Collections.unmodifiableSet<String?>(layerNames)
            }
    }

    private class FeatureIterator(tile: VectorTile.Tile, private val filter: Filter, private val autoScale: Boolean) :
        MutableIterator<Feature?> {
        private val gf = GeometryFactory()

        private val layerIterator: MutableIterator<VectorTile.Tile.Layer>

        private var featureIterator: MutableIterator<VectorTile.Tile.Feature?>? = null

        private var extent = 0
        private var layerName: String? = null
        private var scale = 0.0

        private val keys: MutableList<String?> = ArrayList<String?>()
        private val values: MutableList<Any?> = ArrayList<Any?>()

        private var next: Feature? = null

        init {
            layerIterator = tile.getLayersList().iterator()
        }

        override fun hasNext(): Boolean {
            findNext()
            return next != null
        }

        override fun next(): Feature {
            findNext()
            if (next == null) {
                throw NoSuchElementException()
            }
            val n = next
            next = null
            return n!!
        }

        fun findNext() {
            if (next != null) {
                return
            }

            while (true) {
                if (featureIterator == null || !featureIterator!!.hasNext()) {
                    if (!layerIterator.hasNext()) {
                        next = null
                        break
                    }

                    val layer = layerIterator.next()
                    if (!filter.include(layer.getName())) {
                        continue
                    }

                    parseLayer(layer)
                    continue
                }

                next = parseFeature(featureIterator!!.next()!!)
                break
            }
        }

        fun parseLayer(layer: VectorTile.Tile.Layer) {
            layerName = layer.getName()
            extent = layer.getExtent()
            scale = if (autoScale) extent / 256.0 else 1.0

            keys.clear()
            keys.addAll(layer.getKeysList())
            values.clear()

            for (value in layer.getValuesList()) {
                if (value.hasBoolValue()) {
                    values.add(value.getBoolValue())
                } else if (value.hasDoubleValue()) {
                    values.add(value.getDoubleValue())
                } else if (value.hasFloatValue()) {
                    values.add(value.getFloatValue())
                } else if (value.hasIntValue()) {
                    values.add(value.getIntValue())
                } else if (value.hasSintValue()) {
                    values.add(value.getSintValue())
                } else if (value.hasUintValue()) {
                    values.add(value.getUintValue())
                } else if (value.hasStringValue()) {
                    values.add(value.getStringValue())
                } else {
                    values.add(null)
                }
            }

            featureIterator = layer.getFeaturesList().iterator()
        }

        fun parseFeature(feature: VectorTile.Tile.Feature): Feature {
            val tagsCount = feature.getTagsCount()
            val attributes: MutableMap<String?, Any?> = HashMap<String?, Any?>(tagsCount / 2)
            var tagIdx = 0
            while (tagIdx < feature.getTagsCount()) {
                val key = keys.get(feature.getTags(tagIdx++))
                val value = values.get(feature.getTags(tagIdx++))
                attributes.put(key, value)
            }

            var geometry: Geometry? = decodeGeometry(gf, feature.getType(), feature.getGeometryList(), scale)
            if (geometry == null) {
                geometry = gf.createGeometryCollection(arrayOfNulls<Geometry>(0))
            }

            return Feature(
                layerName,
                extent,
                geometry,
                Collections.unmodifiableMap<String?, Any?>(attributes),
                feature.getId()
            )
        }

        override fun remove() {
            throw UnsupportedOperationException()
        }
    }

    class Feature(
        val layerName: String?,
        val extent: Int,
        val geometry: Geometry?,
        val attributes: MutableMap<String?, Any?>?,
        val id: Long
    )

    companion object {
        fun zigZagDecode(n: Int): Int {
            return ((n shr 1) xor (-(n and 1)))
        }

        @JvmStatic
        fun decodeGeometry(
            gf: GeometryFactory,
            geomType: GeomType,
            commands: MutableList<Int?>,
            scale: Double
        ): Geometry? {
            var x = 0
            var y = 0

            val coordsList: MutableList<MutableList<Coordinate?>> = ArrayList<MutableList<Coordinate?>>()
            var coords: MutableList<Coordinate?>? = null

            val geometryCount = commands.size
            var length = 0
            var command = 0
            var i = 0
            while (i < geometryCount) {
                if (length <= 0) {
                    length = commands.get(i++)!!
                    command = length and ((1 shl 3) - 1)
                    length = length shr 3
                }

                if (length > 0) {
                    if (command == Command.MoveTo) {
                        coords = ArrayList<Coordinate?>()
                        coordsList.add(coords)
                    }

                    if (command == Command.ClosePath) {
                        if (geomType != GeomType.POINT && !coords!!.isEmpty()) {
                            coords.add(Coordinate(coords.get(0)))
                        }
                        length--
                        continue
                    }

                    var dx: Int = commands.get(i++)!!
                    var dy: Int = commands.get(i++)!!

                    length--

                    dx = zigZagDecode(dx)
                    dy = zigZagDecode(dy)

                    x = x + dx
                    y = y + dy

                    val coord = Coordinate(x / scale, y / scale)
                    coords!!.add(coord)
                }
            }

            var geometry: Geometry? = null

            when (geomType) {
                GeomType.LINESTRING -> {
                    val lineStrings: MutableList<LineString?> = ArrayList<LineString?>()
                    for (cs in coordsList) {
                        if (cs.size <= 1) {
                            continue
                        }
                        lineStrings.add(gf.createLineString(cs.toTypedArray<Coordinate?>()))
                    }
                    if (lineStrings.size == 1) {
                        geometry = lineStrings.get(0)
                    } else if (lineStrings.size > 1) {
                        geometry = gf.createMultiLineString(lineStrings.toTypedArray<LineString?>())
                    }
                }

                GeomType.POINT -> {
                    val allCoords: MutableList<Coordinate?> = ArrayList<Coordinate?>()
                    for (cs in coordsList) {
                        allCoords.addAll(cs)
                    }
                    if (allCoords.size == 1) {
                        geometry = gf.createPoint(allCoords.get(0))
                    } else if (allCoords.size > 1) {
                        geometry = gf.createMultiPointFromCoords(allCoords.toTypedArray<Coordinate?>())
                    }
                }

                GeomType.POLYGON -> {
                    val polygonRings: MutableList<MutableList<LinearRing?>> = ArrayList<MutableList<LinearRing?>>()
                    var ringsForCurrentPolygon: MutableList<LinearRing?>? = null
                    var ccw: Boolean? = null
                    for (cs in coordsList) {
                        val ringCoords = cs.toTypedArray<Coordinate?>()
                        val area = Area.ofRingSigned(ringCoords)
                        if (area == 0.0) {
                            continue
                        }
                        val thisCcw = area < 0
                        if (ccw == null) {
                            ccw = thisCcw
                        }
                        val ring = gf.createLinearRing(ringCoords)
                        if (ccw == thisCcw) {
                            if (ringsForCurrentPolygon != null) {
                                polygonRings.add(ringsForCurrentPolygon)
                            }
                            ringsForCurrentPolygon = ArrayList<LinearRing?>()
                        }
                        ringsForCurrentPolygon!!.add(ring)
                    }
                    if (ringsForCurrentPolygon != null) {
                        polygonRings.add(ringsForCurrentPolygon)
                    }

                    val polygons: MutableList<Polygon?> = ArrayList<Polygon?>()
                    for (rings in polygonRings) {
                        val shell = rings.get(0)
                        val holes = rings.subList(1, rings.size).toTypedArray<LinearRing?>()
                        polygons.add(gf.createPolygon(shell, holes))
                    }
                    if (polygons.size == 1) {
                        geometry = polygons.get(0)
                    }
                    if (polygons.size > 1) {
                        geometry = gf.createMultiPolygon(GeometryFactory.toPolygonArray(polygons))
                    }
                }

                GeomType.UNKNOWN -> {}
                else -> {}
            }

            if (geometry == null) {
                geometry = gf.createGeometryCollection(arrayOfNulls<Geometry>(0))
            }

            return geometry
        }
    }
}
