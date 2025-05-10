package geos

interface GeosGeometry : AutoCloseable {
    val isEmpty: Boolean
    val centroid: GeosGeometry

    fun union(other: GeosGeometry) : GeosGeometry
    fun difference(other: GeosGeometry) : GeosGeometry
}