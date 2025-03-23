@file:OptIn(ExperimentalForeignApi::class)

import Gdal.epsg4326
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.toKString
import kotlinx.serialization.json.JsonElement
import libgdal.GDALClose
import libgdal.GDALDatasetCreateLayer
import libgdal.GDALDatasetH
import libgdal.GDALDatasetResetReading
import libgdal.OGRFeatureH
import libgdal.OGRLayerH
import libgdal.OGRSpatialReferenceH
import libgdal.OGR_DS_GetLayer
import libgdal.OGR_DS_GetLayerCount
import libgdal.OGR_L_GetName
import libgdal.OGR_L_GetNextFeature
import libgdal.OSRGetAuthorityCode
import libgdal.wkbUnknown
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
            println("closing dataset")
            GDALClose(it)
        }
    } else {
        null
    }

    private val layers: MutableMap<String, OgrLayer> = mutableMapOf()

    fun getOrCreateLayer(layerName: String): OgrLayer {
        return layers.getOrPut(layerName) {
            val p = memScoped {
//                val options = allocStringArray("ADVERTIZE_UTF8=YES")
                val lp = requireNotNull(GDALDatasetCreateLayer(ptr, layerName, sr, wkbUnknown, null))
                val epsg = OSRGetAuthorityCode(sr, null)?.toKString()
                println("created layer=$layerName epsg=$epsg")
                lp
            }
            OgrLayer(p)
        }
    }

    fun getLayer(name: String) : OgrLayer? {
        return layers[name]
    }

    val layerNames: Set<String>
        get() = layers.values.mapNotNull { it.name }.toSet()

    val layerCount: Int = OGR_DS_GetLayerCount(ptr)

    open fun addFeature(layerName: String, props: Map<String, JsonElement>, geometry: OgrGeometry) {
        val layer: OgrLayer = getOrCreateLayer(layerName)
        val feature = layer.addFeature(geometry, props)
        feature.geometry = geometry
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
}
