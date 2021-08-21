package io.madrona.njord.gis

import io.madrona.njord.gis.tilesystem.GeoExtent
import io.madrona.njord.gis.tilesystem.GeoPoint
import org.gdal.ogr.Geometry
import java.lang.RuntimeException

private fun Geometry.wgs84() {
    if (GetSpatialReference()?.IsSameGeogCS(wgs84SpatialRef) != 1) {
        val result = TransformTo(wgs84SpatialRef)
        if (result != 0) {
            throw RuntimeException("failed to transform Geometry to wgs84")
        }
    }
}

fun Geometry.geoJson(): String? {
    wgs84()
    return ExportToJson()
}

fun Geometry.wgs84Wkt(): String? {
    wgs84()
    return ExportToWkt()
}

fun Geometry.wgs84Centroid(): GeoPoint? {
    wgs84()
    return Centroid()?.GetPoint(0)?.let {
        GeoPoint(it[0], it[1])
    }
}

fun Geometry.wgs84Extent(): GeoExtent? {
    wgs84()
    return DoubleArray(4).let {
        GetEnvelope(it)
        GeoExtent(
                west = it[0], // minX
                east = it[1], // maxX
                south = it[2], // minY
                north = it[3]  // maxY
        )
    }
}
