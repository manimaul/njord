package geos

class GeosPolygon : GeosGeometry {

    override val centroid: GeosGeometry
        get() = TODO("Not yet implemented")

    override fun union(other: GeosGeometry): GeosGeometry {
        TODO("Not yet implemented")
    }

    override fun difference(other: GeosGeometry): GeosGeometry {
        TODO("Not yet implemented")
    }

    val exteriorRing: GeosGeometry
        get() = TODO()

    override val isEmpty: Boolean
        get() = TODO("Not yet implemented")

    override fun close() {
        TODO("Not yet implemented")
    }

    companion object {
        fun create(vararg coordinate: Coordinate): GeosPolygon {
            TODO()
        }
    }
}