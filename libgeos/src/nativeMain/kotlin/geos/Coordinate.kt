package geos

import kotlinx.cinterop.*
import libgeos.*

data class Coordinate(
    val x: Double,
    val y: Double
)

@OptIn(ExperimentalForeignApi::class)
class GeosCoordinateSequence(
    val ptr: CPointer<GEOSCoordSequence>,
    var owned: Boolean = false
) : AutoCloseable {

    val coordinates: List<Coordinate>
        get() {
            val size = memScoped {
                val sz = alloc<UIntVar>()
                GEOSCoordSeq_getSize(ptr, sz.ptr)
                sz.value
            }
            return memScoped {
                val x = alloc<DoubleVar>()
                val y = alloc<DoubleVar>()

                (0.toUInt() until size).map { i ->
                    GEOSCoordSeq_getX(ptr, i, x.ptr)
                    GEOSCoordSeq_getY(ptr, i, y.ptr)
                    Coordinate(x.value, y.value)
                }.toList()
            }
        }

    override fun close() {
        if (!owned) {
            GEOSCoordSeq_destroy(ptr)
        }
    }

    companion object {
        fun from(list: List<Coordinate>): GeosCoordinateSequence {
            val ptr = checkNotNull(GEOSCoordSeq_create(list.size.toUInt(), 2.toUInt()))
            list.forEachIndexed { i, ea ->
                GEOSCoordSeq_setX(ptr, i.toUInt(), ea.x)
                GEOSCoordSeq_setY(ptr, i.toUInt(), ea.y)
            }
            return GeosCoordinateSequence(ptr)
        }
    }
}