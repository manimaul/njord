package io.madrona.njord.geo


import org.locationtech.jts.geom.*
import org.locationtech.jts.geom.util.GeometryTransformer
import kotlin.math.*


class TileSystem(
    private val tileSize: Int = 4096,
) {
    private val minLatY = -85.05112878
    private val maxLatY = 85.05112878
    private val minLngX = -180.0
    private val maxLngX = 180.0
    private val maxZoomLevel = 32
    private val minZoomLevel = 0
    private val geometryFactory = GeometryFactory()

    companion object {

        /**
         * num - the number to clip
         * minValue - minimum allowable value
         * maxValue - maximum allowable value
         */
        fun clip(num: Double, minValue: Double, maxValue: Double): Double =
            min(max(num, minValue), maxValue)
    }


    /**
     * Determines the map width and height (in pixels) at a specified level of detail
     * levelOfDetail, from [minZoomLevel] to [maxZoomLevel] (the highest detail)
     * returns map height and width in pixels
     */
    fun mapSize(levelOfDetail: Number): Int {
        return tileSize.shl(levelOfDetail.toInt())
    }

    /**
     * converts a pixel x,y coordinates at a specified level of detail into
     * latitude,longitude WGS-84 coordinates (in decimal degrees)
     * x - coordinate of point in pixels
     * y - coordinate of point in pixels
     * levelOfDetail, from [minZoomLevel] to [maxZoomLevel] (the highest detail)
     */
    private fun pixelXyToLatLng(pixels: Coordinate, levelOfDetail: Int): Coordinate {
        val mSize = mapSize(levelOfDetail)

        val x = (clip(pixels.x.toDouble(), 0.0, mSize - 1.0) / mSize) - 0.5
        val y = 0.5 - (clip(pixels.y.toDouble(), 0.0, mSize - 1.0) / mSize)

        val lat = 90.0 - 360.0 * atan(exp(-y * 2.0 * PI)) / PI
        val lng = 360.0 * x
        return Coordinate(lng, lat)
    }

    /**
     * converts pixel x,y coordinates into tile x,y coordinates of the tile containing the specified pixel
     * @param x - pixel coordinate
     * @param y - pixel coordinate
     */
    private fun pixelXyToTileXy(x: Number, y: Number) = Coordinate(x.toDouble() / tileSize, y.toDouble() / tileSize)


    /**
     * converts tile x,y coordinates into pixel x,y coordinates of the upper-left pixel of the specified tile
     * tileX - tile coordinate
     * tileY - tile coordinate
     */
    private fun tileXyToPixelXy(x: Number, y: Number) = Coordinate(x.toDouble() * tileSize, y.toDouble() * tileSize)

    /**
     * calculate the WGS-84 coordinate (in decimal degrees) bounds of a tile
     * levelOfDetail: z axis level from 1 (lowest detail) to 23 (highest detail)
     * x: the tile x value
     * y: the tile y value
     * a tuple (top left, top right, bottom right, bottom left) of 4 WGS84 (latitude,longitude) tuples
     */
    fun createTileClipPolygon(x: Int, y: Int, z: Int): Polygon {
        val pixelCoord = tileXyToPixelXy(x, y)
        val tl = pixelXyToLatLng(pixelCoord, z)
        val tr = pixelXyToLatLng(pixelCoord.newCoordinate(x = pixelCoord.x + tileSize), z)
        val br = pixelXyToLatLng(pixelCoord.newCoordinate(x = pixelCoord.x + tileSize, y = pixelCoord.y + tileSize), z)
        val bl = pixelXyToLatLng(pixelCoord.newCoordinate(y = pixelCoord.y + tileSize), z)
        return geometryFactory.createPolygon(
            geometryFactory.coordinateSequenceFactory.create(
                arrayOf(
                    tl,
                    tr,
                    br,
                    bl,
                    tl
                )
            )
        )
    }

    /**
     * converts latitude/longitude WGS-84 coordinates (in decimal degrees) into pixel x,y
     * latitude - (in decimal degrees) to convert
     * longitude - (in decimal degrees) to convert
     * levelOfDetail, from [minZoomLevel] to [maxZoomLevel] (the highest detail)
     */
    private fun latLngToPixelXy(lngX: Double, latY: Double, levelOfDetail: Number): Coordinate {
        val latitude = clip(latY, minLatY, maxLatY)
        val longitude = clip(lngX, minLngX, maxLngX)

        var x: Double = (longitude + 180.0) / 360.0
        val sinLat: Double = sin(latitude * PI / 180.0)
        var y: Double = .5 - ln((1.0 + sinLat) / (1.0 - sinLat)) / (4.0 * PI)

        val mSize = mapSize(levelOfDetail)
        x = clip(x * mSize + .5, 0.0, mSize - 1.0).toInt().toDouble()
        y = clip(y * mSize + .5, 0.0, mSize - 1.0).toInt().toDouble()
        return Coordinate(x, y)
    }

    /**
     * @return tile coordinate for given latitude, longitude WGS-84 coordinates (in decimal degrees)
     */
    fun latLngToTileXy(lngX: Double, latY: Double, levelOfDetail: Number): Coordinate {
        val pxy = latLngToPixelXy(lngX, latY, levelOfDetail)
        return pixelXyToTileXy(pxy.x, pxy.y)
    }

    fun latLngToTileBoundedXy(lngX: Double, latY: Double, x: Int, y: Int, z: Int) : Coordinate {
        val pxy = latLngToPixelXy(lngX, latY, z)
        val tileX = pxy.x - x * tileSize
        val tileY = pxy.y - y * tileSize
        return Coordinate(tileX, tileSize - tileY)
    }


    fun tileGeometry(geometry: Geometry, x: Int, y: Int, z: Int): Geometry {
        return TileGeometryTransformer(x, y, z).transform(geometry)
    }

    private fun Coordinate.newCoordinate(x: Number? = null, y: Number? = null, z: Number? = null): Coordinate =
        Coordinate(x?.toDouble() ?: getX(), y?.toDouble() ?: getY(), z?.toDouble() ?: getZ())

    private inner class TileGeometryTransformer(val x: Int, val y: Int, val z: Int) : GeometryTransformer() {
        override fun transformCoordinates(coords: CoordinateSequence, parent: Geometry): CoordinateSequence {
            return geometryFactory.coordinateSequenceFactory.create(
                coords.toCoordinateArray().map { coordinate ->
                    latLngToTileBoundedXy(coordinate.x, coordinate.y, x, y, z)
                }.toTypedArray()
            )
        }
    }
}




