package io.madrona.njord.gis.tilesystem

import org.locationtech.jts.geom.Polygon
import kotlin.math.*

class TileSystem(
        private val tileSize: Int = 256,
        private val maxZoomLevel: Int = 23
) {
    private val earthRadius = 6378137.0
    private val earthCircumference = 2.0 * PI * earthRadius // at equator
    private val originShift = earthCircumference / 2.0  // 20037508.342789244
    private val minLatY = -85.05112878
    private val maxLatY = 85.05112878
    private val minLngX = -180.0
    private val maxLngX = 180.0
    private val inchesPerMeter = 39.3701

    companion object {

        /**
         * num - the number to clip
         * minValue - minimum allowable value
         * maxValue - maximum allowable value
         */
        fun clip(num: Double, minValue: Double, maxValue: Double) =
                min(max(num, minValue), maxValue)
    }


    /**
     * Determines the map width and height (in pixels) at a specified level of detail
     * levelOfDetail, from 1 (lowest detail) to 23 (highest detail)
     * returns map height and width in pixels
     */
    fun mapSize(levelOfDetail: Int): Int {
        return tileSize.shl(levelOfDetail)
    }

    /**
     * determines the map width and height (in tiles) at a specified level of detail
     * levelOfDetail, from 1 (lowest detail) to 23 (highest detail)
     * returns map height and width in number of tiles
     */
    fun mapSizeTiles(levelOfDetail: Int): Int {
        return mapSize(levelOfDetail) / tileSize
    }

    /**
     * determines the ground resolution (in meters per pixel) at a specific latitude and
     * level of detail
     * latitude - (in decimal degrees) at which to measure the ground resolution
     * levelOfDetail, from 1 (lowest detail) to 23 (highest detail)
     * returns the ground resolution in meters per pixel
     */
    fun groundResolution(latY: Double, levelOfDetail: Int): Double {
        return cos(clip(latY, minLatY, maxLatY) * PI / 180.0) * 2.0 * PI * earthRadius / mapSize(levelOfDetail)
    }

    /**
     * determines the map scale at a specified latitude, level of detail, and dpi resolution
     * latitude - (in decimal degrees) at which to measure the ground resolution
     * levelOfDetail, from 1 (lowest detail) to 23 (highest detail)
     * dpi - resolution in dots per inch
     */
    fun mapScale(latY: Double, levelOfDetail: Int, dpi: Int): Double {
        return groundResolution(latY, levelOfDetail) * dpi / 0.0254
    }

    /**
     * converts latitude/longitude WGS-84 coordinates (in decimal degrees) into pixel x,y
     * latitude - (in decimal degrees) to convert
     * longitude - (in decimal degrees) to convert
     * levelOfDetail, from 1 (lowest detail) to 23 (highest detail)
     */
    fun latLngToPixelXy(latY: Double, lngX: Double, levelOfDetail: Int): PixelPoint {
        val latitude = clip(latY, minLatY, maxLatY)
        val longitude = clip(lngX, minLngX, maxLngX)

        var x: Double = (longitude + 180.0) / 360.0
        val sinLat: Double = sin(latitude * PI / 180.0)
        var y: Double = .5 - log10((1.0 + sinLat) / (1.0 - sinLat)) / (4.0 * PI)

        val mSize = mapSize(levelOfDetail)
        x = clip(x * mSize + .5, 0.0, mSize - 1.0)
        y = clip(y * mSize + .5, 0.0, mSize - 1.0)
        return PixelPoint(x.toInt(), y.toInt())
    }

    /**
     * converts pixel x,y coordinates into tile x,y coordinates of the tile containing the specified pixel
     * point - pixel coordinates
     */
    fun pixelXyToTileXy(point: PixelPoint): TileCoord2D {
        return TileCoord2D((point.x / tileSize), (point.y / tileSize))
    }

    /**
     * gives you (x, y) tile coordinate for given latitude, longitude WGS-84 coordinates (in decimal degrees)
     */
    fun latLngToTileXy(latY: Double, lngX: Double, levelOfDetail: Int): TileCoord3D {
        val xy = latLngToPixelXy(latY, lngX, levelOfDetail)
        return TileCoord3D.from2D(pixelXyToTileXy(xy), levelOfDetail)
    }


    /**
     * converts a pixel x,y coordinates at a specified level of detail into
     * latitude,longitude WGS-84 coordinates (in decimal degrees)
     * x - coordinate of point in pixels
     * y - coordinate of point in pixels
     * levelOfDetail, from 1 (lowest detail) to 23 (highest detail)
     */
    fun pixelXyToLatLng(point: PixelPoint, levelOfDetail: Int): GeoPoint {
        val mSize = mapSize(levelOfDetail)

        val x = (clip(point.x.toDouble(), 0.0, mSize - 1.0) / mSize) - 0.5
        val y = 0.5 - (clip(point.y.toDouble(), 0.0, mSize - 1.0) / mSize)

        val lat = 90.0 - 360.0 * atan(exp(-y * 2.0 * PI)) / PI
        val lng = 360.0 * x
        return GeoPoint(lng, lat)
    }

    /**
     * gives you the xy within the (mapbox vector tile) tile's own coordinate system (0,0 is bottom left)
     * https://github.com/mapbox/vector-tile-spec/tree/master/2.1
     * latY: WGS-84 coordinates
     * lngX: WGS-84 coordinates
     * levelOfDetail: z axis level from 1 (lowest detail) to 23 (highest detail)
     * tileX: the tile x value
     * tileY: the tile y value
     * coordinate within the corresponding tile
     */
    fun latLngToTileBoundedXy(latY: Double, lngX: Double, tileCoord: TileCoord3D): PixelPoint {
        val point = latLngToPixelXy(latY, lngX, tileCoord.z)
        val x = point.x - tileCoord.x * tileSize
        val y = point.y - tileCoord.y * tileSize
        return PixelPoint(x, tileSize - y)
    }

    /**
     * converts tile x,y coordinates into pixel x,y coordinates of the upper-left pixel of the specified tile
     * tileX - tile coordinate
     * tileY - tile coordinate
     */
    fun tileXyToPixelXy(tile: TileCoord2D): PixelPoint {
        return PixelPoint(tile.x * tileSize, tile.y * tileSize)
    }


    /**
     * calculate the WGS-84 coordinate (in decimal degrees) bounds of a tile
     * levelOfDetail: z axis level from 1 (lowest detail) to 23 (highest detail)
     * x: the tile x value
     * y: the tile y value
     * a tuple (top left, top right, bottom right, bottom left) of 4 WGS84 (latitude,longitude) tuples
     */
    fun tileZxyToLatLngBounds(tile: TileCoord3D): GeoBounds {
//        val pxpy = tileXyToPixelXy(tile.x, tile.y)
//        tl = self.pixelXyToLatLng(px, py, levelOfDetail)
//        tr = self.pixelXyToLatLng(px + self.tileSize, py, levelOfDetail)
//        br = self.pixelXyToLatLng(px + self.tileSize, py + self.tileSize, levelOfDetail)
//        bl = self.pixelXyToLatLng(px, py + self.tileSize, levelOfDetail)
//        return tl, tr, br, bl
        TODO()
    }

    /**
     * Creates a polygon in WGS84 coordinates for a zxy tile
     * levelOfDetail z axis level from 1 (lowest detail) to 23 (highest detail)
     * tileX: the tile x value
     * tileY: the tile y value
     * a polygon in WGS84 coordinates
     */
    fun createTileClipPolygon(tile: TileCoord3D): Polygon {
        val bounds  = tileZxyToLatLngBounds(tile)
        //return Polygon([(bounds.nw.longitude, tl[0]), (tr[1], tr[0]), (br[1], br[0]), (bl[1], bl[0], (tl[1], tl[0]))])
        TODO()
    }

    /**
     * Determine the tile extent of a wgs extent at a given z axis
     * wgsExtent: the WGS-84 coordinates extent
     * levelOfDetail: z axis level from 1 (lowest detail) to 23 (highest detail)
     */
    fun latLngBoundsToTileExtent(wgsExtent: GeoExtent, levelOfDetail: Int) : TileExtent {
//        tileWest, tileNorth, tileEast, tileSouth, numTilesWestEast, numTilesNorthSouth = \
//        self.latLngBoundsToTileBoundsCount(wgsExtent.west, wgsExtent.north, wgsExtent.east, wgsExtent.south, levelOfDetail)
//
//        tileExtent = extents.Extent()
//        tileExtent.north = tileNorth
//        tileExtent.south = tileSouth
//        tileExtent.east = tileEast
//        tileExtent.west = tileWest
//        return tileExtent
        TODO()
    }

    /**
     * Latitude longitude bounds to tile system bounds
     * (west, north, east, south)
     * levelOfDetail: z axis level from 1 (lowest detail) to 23 (highest detail)
     * tile bounding extents and count
     */
    fun latLngBoundsToTileBoundsCount(wgsExtent: GeoExtent,
                                      levelOfDetail: Int) : TileBoundsCount {
        //# this seems counter intuitive... tile / pixel 0,0 is top left where as lat long 0,0 is bottom left
        val sw: TileCoord3D = latLngToTileXy(wgsExtent.south, wgsExtent.west, levelOfDetail)
        val ne: TileCoord3D = latLngToTileXy(wgsExtent.north, wgsExtent.east, levelOfDetail)
        var numTilesWestEast = ne.x - sw.x + 1
        val numTilesNorthSouth = ne.y - sw.y + 1
        // dateline wrap
        if (numTilesWestEast < 0) {
            numTilesWestEast = mapSizeTiles(levelOfDetail) - sw.x + ne.x + 2
        }
        val extent = TileExtent(
                z = levelOfDetail,
                nw = TileCoord2D(
                        x = sw.x,
                        y = ne.y
                ),
                ne = ne,
                se = TileCoord2D(
                        x = ne.x,
                        y = sw.y
                ),
                sw = sw
        )
        return TileBoundsCount(extent, numTilesWestEast, numTilesNorthSouth)
    }



    /*

    def levelOfDetailForPixelSize(self, latY, pixelSize):
        """
        maximal scale down zoom of the pyramid closest to the pixelSize
        """
        for zoom in range(self.maxZoomLevel):
            if pixelSize > self.groundResolution(latY, zoom):
                if zoom is not 0:
                    return zoom
                else:
                    return 0  # We don't want to scale up

    # Following methods adapted from http://www.klokan.cz/projects/gdal2tiles/gdal2tiles.py
    # and changed from TMS pyramid coordinate to ZXY coordinate outputs

    def pixelsToMeters(self, px, py, levelOfDetail):
        """
            px: X tile system pixels
            py: Y tile system pixels
            levelOfDetail: z axis level from 1 (lowest detail) to 23 (highest detail)
            EPSG:3857 map coordinates
        """
        res = self.groundResolution(0, levelOfDetail)
        mx = px * res - self.originShift
        my = py * res - self.originShift
        return mx, my

    def metersToPixels(self, metersX, metersY, levelOfDetail):
        """
        converts XY point from Spherical Mercator EPSG:3857 to ZXY pixel coordinates
        """
        res = self.groundResolution(0, levelOfDetail)  # ground resolution at equator
        x = int((metersX + self.originShift) / res)
        y = int((metersY + self.originShift) / res)
        return self.tmsToZxyCoord(x, y, levelOfDetail)

    def metersToTile(self, metersX, metersY, levelOfDetail):
        """
        converts XY point from Spherical Mercator EPSG:3857 to ZXY tile coordinates
        """
        x, y = self.metersToPixels(metersX, metersY, levelOfDetail)
        tx, ty = self.pixelXyToTileXy(x, y)
        return tx, ty

    def metersToLatLng(self, metersX, metersY):
        """
        converts XY point from Spherical Mercator EPSG:3857 to lat/lon in WGS84 Datum
        """
        lng = (metersX / self.originShift) * 180.0
        lat = (metersY / self.originShift) * 180.0

        lat = 180 / numpy.pi * (2 * numpy.arctan(numpy.exp(lat * numpy.pi / 180.0)) - numpy.pi / 2.0)
        return lat, lng

    # conversions from TMS tile system coordinates
    # TMS coordinates originate from bottom left
    # ZXY coordinates originate from the top left

    def tmsToZxyCoord(self, px, py, zoom):
        """Converts TMS pixel coordinates to ZXY pixel coordinates
           or vise versa
        """
        return px, (2 ** zoom) * self.tileSize - py - 1

    @staticmethod
    def tmsTileToZxyTile(tmsX, tmsY, zoom):
        """Converts TMS tile coordinates to ZXY tile coordinates
           or vise versa
        """
        return tmsX, (2 ** zoom - 1) - tmsY

    # def latLngBoundsToPixelBoundsRes(self, (minLng, maxLat, maxLng, minLat), levelOfDetail):
    #     """
    #         Latitude longitude bounds to tile system pixel bounds
    #         (west, north, east, south)
    #         levelOfDetail: z axis level from 1 (lowest detail) to 23 (highest detail)
    #         tile system pixel extents and resolution
    #     """
    #     # this seems counter intuitive... tile / pixel 0,0 is top left where as lat long 0,0 is bottom left
    #     pixelWest, pixelNorth = self.latLngToPixelXy(minLat, minLng, int(levelOfDetail))
    #     pixelEast, pixelSouth = self.latLngToPixelXy(maxLat, maxLng, int(levelOfDetail))
    #     resX = pixelEast - pixelWest + 1
    #     resY = pixelNorth - pixelSouth + 1
    #
    #     # dateline wrap
    #     if resX < 0:
    #         resX = (self.mapSize(levelOfDetail) - pixelWest) + pixelEast + 2
    #
    #     return pixelWest, pixelNorth, pixelEast, pixelSouth, resX, resY
     */
}



