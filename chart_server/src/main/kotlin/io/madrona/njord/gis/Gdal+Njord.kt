package io.madrona.njord.gis

import org.gdal.ogr.Geometry

fun Geometry.wgs84Wkt() : String? {
    return if (GetSpatialReference()?.IsSameGeogCS(wgs84SpatialRef) == 1) {
        ExportToWkt()
    } else {
        TransformTo(wgs84SpatialRef)
        ExportToWkt()
    }
}