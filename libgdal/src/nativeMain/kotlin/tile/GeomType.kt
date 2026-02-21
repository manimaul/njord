@file:OptIn(ExperimentalForeignApi::class)
package tile

import kotlinx.cinterop.ExperimentalForeignApi
import libgdal.OGRwkbGeometryType
import libgdal.wkbGeometryCollection
import libgdal.wkbLineString
import libgdal.wkbLinearRing
import libgdal.wkbMultiLineString
import libgdal.wkbMultiPoint
import libgdal.wkbMultiPolygon
import libgdal.wkbPoint
import libgdal.wkbPolygon
import libgdal.wkbUnknown

enum class GeomType(
    val wktType: String,
    val gdalType: OGRwkbGeometryType,
    val tileType: Tile.GeomType
) {
    Unknown("POINT", wkbUnknown, Tile.GeomType.UNKNOWN),

    MultiPoint("MULTIPOINT", wkbMultiPoint, Tile.GeomType.POINT),
    Point("POINT", wkbPoint, Tile.GeomType.POINT),

    MultiLineString("MULTILINESTRING", wkbMultiLineString, Tile.GeomType.LINESTRING),
    LineString("LINESTRING", wkbLineString, Tile.GeomType.LINESTRING),
    LinearRing("LINESTRING", wkbLinearRing, Tile.GeomType.LINESTRING),

    Polygon("POLYGON", wkbPolygon, Tile.GeomType.POLYGON),
    MultiPolygon("MULTIPOLYGON", wkbMultiPolygon, Tile.GeomType.POLYGON),

    GeometryCollection("GEOMETRYCOLLECTION", wkbGeometryCollection, Tile.GeomType.UNKNOWN)
}
