import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import libgeos.*
import kotlinx.cinterop.staticCFunction
import kotlinx.cinterop.toKString


@OptIn(ExperimentalForeignApi::class)
fun main() {
    println("init geos")
    val handler = staticCFunction<CPointer<ByteVar>, Unit> {
        val message = it.toKString()
        println("init geos message: $message")

    }
    initGEOS(handler, handler)
    val wkt = "POLYGON ((189 115, 200 170, 130 170, 35 242, 156 215, 210 290, 274 256, 360 190, 267 215, 300 50, 200 60, 189 115))";
    val reader = GEOSWKTReader_create()
    val polygon: CPointer<GEOSGeometry>? = GEOSWKTReader_read(reader, wkt)
    finishGEOS()
    println("finish geos")

}
