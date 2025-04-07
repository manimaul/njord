import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.toKString
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import libgdal.GDALVersionInfo

@Serializable
private data class Message(
    val gdalVersion: String,
)

private val PrettyPrintJson = Json {
    prettyPrint = true
}

@OptIn(ExperimentalForeignApi::class)
fun main() {
    //https://gdal.org/en/stable/api/raster_c_api.html
    val info = GDALVersionInfo("BUILD_INFO");
    val message = Message(
        gdalVersion = info?.toKString() ?: "",
    )
    println(PrettyPrintJson.encodeToString(message))
}
