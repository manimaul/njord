@file:OptIn(ExperimentalForeignApi::class)

import Gdal.epsg4326
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.serialization.json.JsonElement
import libgdal.*
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.ref.Cleaner
import kotlin.native.ref.createCleaner

open class GdalDataset(
    val ptr: GDALDatasetH,
    val sr: OGRSpatialReferenceH = epsg4326,
    autoClose: Boolean = true,
) {

    @OptIn(ExperimentalNativeApi::class)
    private val cleaner: Cleaner? = if (autoClose) {
        createCleaner(ptr) {
            GDALClose(it)
        }
    } else {
        null
    }

    private val layers: MutableMap<String, OgrLayer> = mutableMapOf()

    fun getOrCreateLayer(layerName: String): OgrLayer {
        return layers.getOrPut(layerName) {
            val lp = requireNotNull(GDALDatasetCreateLayer(ptr, layerName, sr, wkbUnknown, null))
            OgrLayer(lp)
        }
    }

    fun getLayer(name: String): OgrLayer? {
        return layers[name]
    }

    val layerNames: Set<String>
        get() = layers.values.mapNotNull { it.name }.toSet()

    val layerCount: Int = OGR_DS_GetLayerCount(ptr)

    open fun addFeature(layerName: String, props: Map<String, JsonElement>, geometry: OgrGeometry) {
        val layer: OgrLayer = getOrCreateLayer(layerName)
        layer.addFeature(geometry, props)
    }

    fun featureCount(exLayers: Set<String> = emptySet()): Long {
        return layers.values.sumOf {
            if (exLayers.contains(it.name)) {
                0L
            } else {
                it.featureCount
            }
        }
    }

    companion object {
        fun create(
            driverName: String,
            path: String,
            epsg: Int
        ): GdalDataset? {
            val driver = GDALGetDriverByName(driverName)
            val sr = (OSRNewSpatialReference(null) as OGRSpatialReferenceH).also {
                OSRImportFromEPSG(it, epsg)
                OSRSetAxisMappingStrategy(it, OSRAxisMappingStrategy.OAMS_TRADITIONAL_GIS_ORDER)
            }
            return GDALCreate(driver, path, 0, 0, 0, GDT_Unknown, null)?.let {
                GdalDataset(it, sr)
            }
        }
    }
}
