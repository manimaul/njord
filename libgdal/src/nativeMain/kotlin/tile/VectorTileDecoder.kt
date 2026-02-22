package tile

import OgrGeometry
import io.madrona.njord.geojson.Position

class VectorTileDecoder {

    companion object {
        fun zigZagDecode(n: Int): Int {
            return ((n shr 1) xor (-(n and 1)))
        }

        fun decodeGeometry(
            geomType: Tile.GeomType,
            commands: List<Int>,
            scale: Double
        ): OgrGeometry {
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

                    x += dx
                    y += dy

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
