import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.toKString
import libgdal.*

@OptIn(ExperimentalForeignApi::class)
class S57(
    path: String
) {
    private val dataSet: GDALDatasetH? = GDALOpenEx(
        path,
        GDAL_OF_VECTOR.toUInt(),
        null,
        null,
        null)

    val layerNames: List<String> by lazy {
        layers().mapNotNull {
           OGR_L_GetName(it)?.toKString()
        }
    }

    private fun layers() : List<OGRLayerH> {
        GDALDatasetResetReading(dataSet)
        val count = OGR_DS_GetLayerCount(dataSet)
        println("layer count $count")
        return (0 until  OGR_DS_GetLayerCount(dataSet)).mapNotNull {
            OGR_DS_GetLayer(dataSet, it)
        }
    }

    companion object {

        /**
         * https://gdal.org/drivers/vector/s57.html
         */
        private const val OGR_S57_OPTIONS_K = "OGR_S57_OPTIONS"

        /**
         * https://gdal.org/drivers/vector/s57.html#s-57-export
         */
        private const val OGR_S57_OPTIONS_V =
            "RETURN_PRIMITIVES=OFF,RETURN_LINKAGES=OFF,LNAM_REFS=ON,UPDATES=APPLY,SPLIT_MULTIPOINT=ON,RECODE_BY_DSSI=ON:ADD_SOUNDG_DEPTH=ON"
        init {
            GDALAllRegister()
            CPLSetConfigOption(OGR_S57_OPTIONS_K, OGR_S57_OPTIONS_V)
        }
    }
}