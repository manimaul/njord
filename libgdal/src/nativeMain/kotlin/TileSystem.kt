@file:OptIn(ExperimentalForeignApi::class)

import io.madrona.njord.geojson.Position
import kotlinx.cinterop.ExperimentalForeignApi
import libgdal.OGRGeometryH
import libgdal.OGR_G_AddGeometryDirectly
import libgdal.OGR_G_AddPoint_2D
import libgdal.OGR_G_CreateGeometry
import libgdal.wkbLinearRing
import libgdal.wkbPolygon
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

class TileSystem(
    private val tileSize: Int = 4096,
) {
    private val minLatY = -85.05112878
    private val maxLatY = 85.05112878
    private val minLngX = -180.0
    private val maxLngX = 180.0

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
     * levelOfDetail.
     * returns map height and width in pixels
     */
    fun mapSize(levelOfDetail: Number): Int {
        return tileSize.shl(levelOfDetail.toInt())
    }

    /**
     * converts a pixel x,y coordinates at a specified level of detail into
     * latitude,longitude WGS-84 coordinates (in decimal degrees)
     * [pixels] - pixel coordinates
     * [levelOfDetail] the z / zoom level
     */
    private fun pixelXyToLatLng(pixels: Position, levelOfDetail: Int): Position {
        val mSize = mapSize(levelOfDetail)

        val x = (clip(pixels.x, 0.0, mSize - 1.0) / mSize) - 0.5
        val y = 0.5 - (clip(pixels.y, 0.0, mSize - 1.0) / mSize)

        val lat = 90.0 - 360.0 * atan(exp(-y * 2.0 * PI)) / PI
        val lng = 360.0 * x
        return Position(lng, lat)
    }

    /**
     * converts pixel x,y coordinates into tile x,y coordinates of the tile containing the specified pixel
     * @param x - pixel coordinate
     * @param y - pixel coordinate
     */
    private fun pixelXyToTileXy(x: Number, y: Number) = Position(x.toDouble() / tileSize, y.toDouble() / tileSize)


    /**
     * converts tile x,y coordinates into pixel x,y coordinates of the upper-left pixel of the specified tile
     * tileX - tile coordinate
     * tileY - tile coordinate
     */
    private fun tileXyToPixelXy(x: Number, y: Number) = Position(x.toDouble() * tileSize, y.toDouble() * tileSize)

    /**
     * calculate the WGS-84 coordinate (in decimal degrees) bounds of a tile
     * levelOfDetail: z axis level from 1 (lowest detail) to 23 (highest detail)
     * [x] the tile x value
     * [y] the tile y value
     * [z] the tile zoom
     * [expandPixels] the amount of pixels to expand the tile by
     */
    fun createTileClipPolygon(x: Int, y: Int, z: Int, expandPixels: Int = 0): OgrGeometry {
        val ring: OGRGeometryH = requireNotNull(OGR_G_CreateGeometry(wkbLinearRing))
        val pixelCoord = tileXyToPixelXy(x, y)
        val tl = pixelXyToLatLng(pixelCoord.newPosition(x = pixelCoord.x - expandPixels, y = pixelCoord.y - expandPixels), z)
        val tr = pixelXyToLatLng(pixelCoord.newPosition(x = pixelCoord.x + tileSize + expandPixels, y = pixelCoord.y - expandPixels), z)
        val br = pixelXyToLatLng(pixelCoord.newPosition(x = pixelCoord.x + tileSize + expandPixels, y = pixelCoord.y + tileSize + expandPixels), z)
        val bl = pixelXyToLatLng(pixelCoord.newPosition(x = pixelCoord.x - expandPixels, y = pixelCoord.y + tileSize + expandPixels), z)

        OGR_G_AddPoint_2D(ring, tl.x, tl.y);
        OGR_G_AddPoint_2D(ring, tr.x, tr.y);
        OGR_G_AddPoint_2D(ring, br.x, br.y);
        OGR_G_AddPoint_2D(ring, bl.x, bl.y);
        OGR_G_AddPoint_2D(ring, tl.x, tl.y);
        val polygon: OGRGeometryH = requireNotNull(OGR_G_CreateGeometry(wkbPolygon))
        OGR_G_AddGeometryDirectly(polygon, ring)
        return OgrGeometry(polygon)
    }

    /**
     * converts latitude/longitude WGS-84 coordinates (in decimal degrees) into pixel x,y
     * latitude - (in decimal degrees) to convert
     * longitude - (in decimal degrees) to convert
     * levelOfDetail - the z / zoom level
     */
    private fun latLngToPixelXy(lngX: Double, latY: Double, levelOfDetail: Number): Position {
        val latitude = clip(latY, minLatY, maxLatY)
        val longitude = clip(lngX, minLngX, maxLngX)

        var x: Double = (longitude + 180.0) / 360.0
        val sinLat: Double = sin(latitude * PI / 180.0)
        var y: Double = .5 - ln((1.0 + sinLat) / (1.0 - sinLat)) / (4.0 * PI)

        val mSize = mapSize(levelOfDetail)
        x = clip(x * mSize + .5, 0.0, mSize - 1.0).toInt().toDouble()
        y = clip(y * mSize + .5, 0.0, mSize - 1.0).toInt().toDouble()
        return Position(x, y)
    }

    /**
     * @return tile coordinate for given latitude, longitude WGS-84 coordinates (in decimal degrees)
     */
    fun latLngToTileXy(lngX: Double, latY: Double, levelOfDetail: Number): Position {
        val pxy = latLngToPixelXy(lngX, latY, levelOfDetail)
        return pixelXyToTileXy(pxy.x, pxy.y)
    }

    fun latLngToTileBoundedXy(lngX: Double, latY: Double, x: Int, y: Int, z: Int) : Position {
        val pxy = latLngToPixelXy(lngX, latY, z)
        val tileX = pxy.x - x * tileSize
        val tileY = pxy.y - y * tileSize
        return Position(tileX, tileY)
    }
}