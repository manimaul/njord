package io.madrona.njord.gis.tilesystem

interface Point {
    val x: Number
    val y: Number
}

interface TileCoord {
    val x: Int
    val y: Int
}

data class TileCoord2D(
        override val x: Int,
        override val y: Int
) : TileCoord

data class TileCoord3D(
        val z: Int,
        override val x: Int,
        override val y: Int
) : TileCoord {
    companion object {
        fun from2D(tileCoord2D: TileCoord2D, levelOfDetail: Int) : TileCoord3D {
            return TileCoord3D(levelOfDetail, tileCoord2D.x, tileCoord2D.y)
        }
    }
}

data class TileExtent(
        val z: Int,
        val nw: TileCoord,
        val ne: TileCoord,
        val se: TileCoord,
        val sw: TileCoord
)

data class TileBoundsCount(
        val extent: TileExtent,
        val numTilesWestEast: Int,
        val numTilesNorthSouth: Int
)


data class PixelPoint(
        override val x: Int,
        override val y: Int
) : Point

data class GeoPoint(
        val longitude: Double,
        val latitude: Double
) : Point {
    override val x: Double
        get() = longitude
    override val y: Double
        get() = latitude
}

data class GeoExtent(
        val west: Double,
        val north: Double,
        val east: Double,
        val south: Double
)

data class GeoBounds(
        val nw: GeoPoint,
        val ne: GeoPoint,
        val se: GeoPoint,
        val sw: GeoPoint
)
