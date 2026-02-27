@file:OptIn(ExperimentalForeignApi::class)

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.toKString
import libgdal.*
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.ref.Cleaner
import kotlin.native.ref.createCleaner

class OgrShapefileDataset(val file: File) {
    private val ptr: GDALDatasetH = requireNotNull(
        GDALOpenEx(
            file.getAbsolutePath().toString(),
            GDAL_OF_VECTOR.toUInt(),
            null,
            null,
            null
        )
    ) { "Failed to open shapefile: ${file.getAbsolutePath()}" }

    @OptIn(ExperimentalNativeApi::class)
    private val cleaner: Cleaner = createCleaner(ptr) {
        GDALClose(it)
    }

    val layerCount: Int = OGR_DS_GetLayerCount(ptr)

    fun layerNames(): List<String> {
        return if (layerCount > 0) {
            (0 until layerCount).mapNotNull { index ->
                OGR_DS_GetLayer(ptr, index)?.let { lp ->
                    OGR_L_GetName(lp)?.toKString()
                }
            }
        } else {
            emptyList()
        }
    }

    fun getLayer(name: String): OgrLayer? {
        return OGR_DS_GetLayerByName(ptr, name)?.let {
            OgrLayer(it, this)
        }
    }

    fun getLayerAt(index: Int): OgrLayer? {
        return OGR_DS_GetLayer(ptr, index)?.let {
            OgrLayer(it, this)
        }
    }

    fun featureCount(): Long {
        return layerNames().sumOf {
            getLayer(it)?.featureCount ?: 0
        }
    }
}
