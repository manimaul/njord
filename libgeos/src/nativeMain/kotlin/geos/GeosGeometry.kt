package geos

import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import libgeos.GEOSDifference
import libgeos.GEOSGeomTypeId
import libgeos.GEOSGeomTypes
import libgeos.GEOSGeom_destroy
import libgeos.GEOSGeometry
import libgeos.GEOSGetCentroid
import libgeos.GEOSGetExteriorRing
import libgeos.GEOSGetGeometryN
import libgeos.GEOSGetNumGeometries
import libgeos.GEOSUnion
import libgeos.GEOSisEmpty

typealias Transformer = (Coordinate) -> Coordinate

@OptIn(ExperimentalForeignApi::class)
class GeosGeometry(
    val ptr: CPointer<GEOSGeometry>,
    val owned: Boolean = false
) : AutoCloseable {

    val isEmpty: Boolean
        get() = GEOSisEmpty(ptr) == 1.toByte()

    fun centroid() : GeosGeometry {
        return GeosGeometry(checkNotNull(GEOSGetCentroid(ptr)), false)
    }

    val exteriorRing: GeosGeometry
        get() = GeosGeometry(checkNotNull(GEOSGetExteriorRing(ptr)), true)

    val type: GeosGeomType
        get() {
            return when (GEOSGeomTypeId(ptr).toUInt()) {
                GEOSGeomTypes.GEOS_POINT.value -> GeosGeomType.Point
                GEOSGeomTypes.GEOS_LINESTRING.value -> GeosGeomType.LineString
                GEOSGeomTypes.GEOS_LINEARRING.value -> GeosGeomType.LinearRing
                GEOSGeomTypes.GEOS_POLYGON.value -> GeosGeomType.Polygon
                GEOSGeomTypes.GEOS_MULTIPOINT.value -> GeosGeomType.MultiPoint
                GEOSGeomTypes.GEOS_MULTILINESTRING.value -> GeosGeomType.MultiLineString
                GEOSGeomTypes.GEOS_MULTIPOLYGON.value -> GeosGeomType.MultiPolygon
                GEOSGeomTypes.GEOS_GEOMETRYCOLLECTION.value -> GeosGeomType.GeometryCollection
                else -> GeosGeomType.Unknown
            }
        }

    val numGeometries: Int
        get() = GEOSGetNumGeometries(ptr)

    fun getGeometryN(index: Int): GeosGeometry? {
        return GeosGeometry(GEOSGetGeometryN(ptr, index)!!, true)
    }

    fun union(other: GeosGeometry): GeosGeometry {
        return GeosGeometry(GEOSUnion(ptr, other.ptr)!!, false)
    }

    fun difference(other: GeosGeometry): GeosGeometry {
        return GeosGeometry(GEOSDifference(ptr, other.ptr)!!, false)
    }

    fun transform(tx: Transformer) : GeosGeometry {
        //GEOSGeom_transformXY()
        TODO()
    }

    override fun close() {
        if (!owned) {
            GEOSGeom_destroy(ptr)
        }
    }
}

enum class GeosGeomType {
    Unknown,
    Point,
    LineString,
    LinearRing,
    Polygon,
    MultiPoint,
    MultiLineString,
    MultiPolygon,
    GeometryCollection
}