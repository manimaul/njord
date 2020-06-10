package io.madrona.njord.gis

import io.madrona.njord.gis.tilesystem.GeoExtent
import io.madrona.njord.gis.tilesystem.GeoPoint
import org.gdal.ogr.Geometry

fun Geometry.wgs84Wkt(): String? {
    return if (GetSpatialReference()?.IsSameGeogCS(wgs84SpatialRef) == 1) {
        ExportToWkt()
    } else {
        TransformTo(wgs84SpatialRef)
        ExportToWkt()
    }
}

fun Geometry.wgs84Centroid(): GeoPoint? {
    if (GetSpatialReference()?.IsSameGeogCS(wgs84SpatialRef) != 1) {
        TransformTo(wgs84SpatialRef)
    }
    return Centroid()?.GetPoint(0)?.let {
        GeoPoint(it[0], it[1])
    }
}

fun Geometry.wgs84Extent(): GeoExtent? {
    if (GetSpatialReference()?.IsSameGeogCS(wgs84SpatialRef) != 1) {
        TransformTo(wgs84SpatialRef)
    }
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