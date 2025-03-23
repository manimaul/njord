@file:OptIn(ExperimentalForeignApi::class)

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.toKString
import kotlinx.serialization.json.JsonElement
import libgdal.*

class OgrLayer(
    val ptr: OGRLayerH
) {

    private val features: MutableList<OgrFeature> = mutableListOf()

    //note: no cleaner - Layers destroyed when dataset is closed

    init {
        val count = OGR_L_GetFeatureCount(ptr, 1)
        if (count > 0) {
            (0 until count).forEach { i ->
                OGR_L_GetFeature(ptr, i)?.let { fPtr ->
                    features.add(OgrFeature(fPtr))
                }
            }
        }
    }


    val name: String?
        get() = OGR_L_GetName(ptr)?.toKString()

    val featureCount: Long
        get() = OGR_L_GetFeatureCount(ptr, 1)

    fun getFeature(index: Int): OgrFeature? {
        if (index > 0 && index < features.size) {
            return features[index]
        } else {
            return null
        }
    }

    fun addFeature(geometry: OgrGeometry,  props: Map<String, JsonElement> = emptyMap()): OgrFeature {
        val def = OGR_L_GetLayerDefn(ptr)
        val fp = OGR_F_Create(def) ?: error("failed to create feature")
        val f = OgrFeature(fp)
        f.geometry = geometry
        props.forEach {
            f.addProperty(it.key, it.value)
        }
        OGR_L_CreateFeature(ptr, fp).requireSuccess {
            "failed to bind feature to layer"
        }
        features.add(f)
        return f
    }
}
