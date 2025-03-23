import io.madrona.njord.geojson.Position

class VectorTileDecoder {
//    /**
//     * Get the autoScale setting.
//     *
//     * @return autoScale
//     */
//    /**
//     * Set the autoScale setting.
//     *
//     * @param isAutoScale
//     * when true, the encoder automatically scale and return all coordinates in the 0..255 range.
//     * when false, the encoder returns all coordinates in the 0..extent-1 range as they are encoded.
//     */
//    var isAutoScale: Boolean = true
//
//    fun decode(data: ByteArray, layerName: String): FeatureIterable {
//        return decode(data, Filter.Single(layerName))
//    }
//
//    fun decode(data: ByteArray, layerNames: Set<String>): FeatureIterable {
//        return decode(data, Filter.Any(layerNames))
//    }
//
//    fun decode(data: ByteArray, filter: Filter = Filter.ALL): FeatureIterable {
//        val tile = Tile.decodeTile(data)
//        return FeatureIterable(tile, filter, this.isAutoScale)
//    }
//
//    class FeatureIterable(private val tile: Tile, private val filter: Filter, private val autoScale: Boolean) :
//        Iterable<Feature> {
//        override fun iterator(): Iterator<Feature> {
//            return FeatureIterator(tile, filter, autoScale)
//        }
//
//        fun asList(): MutableList<Feature> {
//            val features: MutableList<Feature> = ArrayList<Feature>()
//            for (feature in this) {
//                features.add(feature)
//            }
//            return features
//        }
//
//        val layerNames: Set<String>
//            get() {
//                return tile.layers.map { it.name }.toSet()
//            }
//    }
//
//    private class FeatureIterator(tile: Tile, private val filter: Filter, private val autoScale: Boolean) :
//        Iterator<Feature> {
//        private val layerIterator: Iterator<Tile.Layer> = tile.layers.iterator()
//
//        private var featureIterator: Iterator<Tile.Feature?>? = null
//
//        private var extent = 0
//        private var layerName: String? = null
//        private var scale = 0.0
//
//        private val keys: MutableList<String?> = ArrayList<String?>()
//        private val values: MutableList<Any?> = ArrayList<Any?>()
//
//        private var next: Feature? = null
//
//        override fun hasNext(): Boolean {
//            findNext()
//            return next != null
//        }
//
//        override fun next(): Feature {
//            findNext()
//            if (next == null) {
//                throw NoSuchElementException()
//            }
//            val n = next
//            next = null
//            return n!!
//        }
//
//        fun findNext() {
//            if (next != null) {
//                return
//            }
//
//            while (true) {
//                if (featureIterator == null || !featureIterator!!.hasNext()) {
//                    if (!layerIterator.hasNext()) {
//                        next = null
//                        break
//                    }
//
//                    val layer = layerIterator.next()
//                    if (!filter.include(layer.name)) {
//                        continue
//                    }
//
//                    parseLayer(layer)
//                    continue
//                }
//
//                next = parseFeature(featureIterator!!.next()!!)
//                break
//            }
//        }
//
//        fun parseLayer(layer: Tile.Layer) {
//            layerName = layer.name
//            extent = layer.extent
//            scale = if (autoScale) extent / 256.0 else 1.0
//
//            keys.clear()
//            keys.addAll(layer.keys)
//            values.clear()
//
//            for (value in layer.values) {
//                if (value.boolValue != null) {
//                    values.add(value.boolValue)
//                } else if (value.doubleValue != null) {
//                    values.add(value.doubleValue)
//                } else if (value.floatValue != null) {
//                    values.add(value.floatValue)
//                } else if (value.intValue != null) {
//                    values.add(value.intValue)
//                } else if (value.sintValue != null) {
//                    values.add(value.sintValue)
//                } else if (value.uintValue != null) {
//                    values.add(value.uintValue)
//                } else if (value.stringValue != null) {
//                    values.add(value.stringValue)
//                } else {
//                    values.add(null)
//                }
//            }
//
//            featureIterator = layer.features.iterator()
//        }
//
//        fun parseFeature(feature: Tile.Feature): Feature {
//            val tagsCount = feature.tags.size
////            val attributes: MutableMap<String, *> = HashMap<String, Any?>(tagsCount / 2)
////            var tagIdx = 0
////            while (tagIdx < feature.tags.size) {
////                feature.tags
////                val key = keys[feature.tags[tagIdx++]]
////                val value = values[feature.tags[tagIdx++]]
////                attributes[key] = value
////            }
//
//            var geometry: OgrGeometry? = decodeGeometry(feature.type, feature.geometry, scale)
//            if (geometry == null) {
//                geometry = Geos.createGeometryCollection() //empty
//            }
//
//            return Feature(
////                layerName = layerName,
////                extent = extent,
//                geometry = geometry,
//                tags = emptyList(),
////                attributes = attributes,
//                id = feature.id
//            )
//        }
//    }
//
    companion object {
        fun zigZagDecode(n: Int): Int {
            return ((n shr 1) xor (-(n and 1)))
        }

        fun decodeGeometry(
            geomType: Tile.GeomType,
            commands: List<Int>,
            scale: Double
        ): OgrGeometry? {
            var x = 0
            var y = 0

            val coordsList: MutableList<MutableList<Position>> = ArrayList()
            var coords: MutableList<Position>? = null

            val geometryCount = commands.size
            var length = 0
            var command = 0
            var i = 0
            while (i < geometryCount) {
                if (length <= 0) {
                    length = commands[i++]
                    command = length and ((1 shl 3) - 1)
                    length = length shr 3
                }

                if (length > 0) {
                    if (command == Command.MoveTo) {
                        coords = ArrayList()
                        coordsList.add(coords)
                    }

                    if (command == Command.ClosePath) {
                        if (geomType != Tile.GeomType.POINT && !coords!!.isEmpty()) {

                            coords.add(coords.first().copy())
                        }
                        length--
                        continue
                    }

                    var dx: Int = commands[i++]
                    var dy: Int = commands[i++]

                    length--

                    dx = zigZagDecode(dx)
                    dy = zigZagDecode(dy)

                    x = x + dx
                    y = y + dy

                    val coord = Position(x / scale, y / scale)
                    coords!!.add(coord)
                }
            }

            var geometry: OgrGeometry? = null

            when (geomType) {
                Tile.GeomType.LINESTRING -> {
                    val lineStrings: MutableList<OgrGeometry> = mutableListOf()
                    for (cs in coordsList) {
                        if (cs.size <= 1) {
                            continue
                        }
                        lineStrings.add(Gdal.createLineString(*cs.toTypedArray()))
                    }
                    if (lineStrings.size == 1) {
                        geometry = lineStrings.get(0)
                    } else if (lineStrings.size > 1) {
                        geometry = Gdal.createMultiLineString(*lineStrings.toTypedArray())
                    }
                }

                Tile.GeomType.POINT -> {
                    val allCoords: MutableList<Position> = ArrayList<Position>()
                    for (cs in coordsList) {
                        allCoords.addAll(cs)
                    }
                    if (allCoords.size == 1) {
                        geometry = Gdal.createPoint(allCoords.first())
                    } else if (allCoords.size > 1) {
                        geometry = Gdal.createMultiPointFromCoords(*allCoords.toTypedArray())
                    }
                }

                Tile.GeomType.POLYGON -> {
                    val polygonRings: MutableList<MutableList<OgrGeometry>> = ArrayList()
                    var ringsForCurrentPolygon: MutableList<OgrGeometry>? = null
                    var ccw: Boolean? = null
                    for (cs in coordsList) {
                        val ringCoords = cs.toTypedArray<Position>()
                        val area = Gdal.createLinearRing(*cs.toTypedArray()).area
                        if (area == 0.0) {
                            continue
                        }
                        val thisCcw = area < 0
                        if (ccw == null) {
                            ccw = thisCcw
                        }
                        val ring = Gdal.createLinearRing(*ringCoords)
                        if (ccw == thisCcw) {
                            if (ringsForCurrentPolygon != null) {
                                polygonRings.add(ringsForCurrentPolygon)
                            }
                            ringsForCurrentPolygon = ArrayList()
                        }
                        ringsForCurrentPolygon!!.add(ring)
                    }
                    if (ringsForCurrentPolygon != null) {
                        polygonRings.add(ringsForCurrentPolygon)
                    }

                    val polygons: MutableList<OgrGeometry> = ArrayList<OgrGeometry>()
                    for (rings in polygonRings) {
                        val shell = rings[0]
                        val holes = rings.subList(1, rings.size).toTypedArray<OgrGeometry>()
                        polygons.add(Gdal.createPolygon(shell, *holes))
                    }

                    if (polygons.size == 1) {
                        geometry = polygons[0]
                    }
                    if (polygons.size > 1) {
                        geometry = Gdal.createMultiPolygon(*polygons.toTypedArray())
                    }
                }

                Tile.GeomType.UNKNOWN -> {}
            }

            if (geometry == null) {
                geometry = Gdal.createGeometryCollection() // empty
            }

            return geometry
        }
    }
}
