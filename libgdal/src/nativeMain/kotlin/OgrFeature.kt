@file:OptIn(ExperimentalForeignApi::class, ExperimentalNativeApi::class)

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.serialization.json.JsonObject
import libgdal.OGRFeatureH
import libgdal.OGR_F_Destroy
import libgdal.OGR_F_GetGeometryRef
import libgdal.OGR_F_SetGeometry
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.ref.Cleaner
import kotlin.native.ref.createCleaner


class OgrFeature(
    val ptr: OGRFeatureH,
    val owner: Any? = null,
) {

    @Suppress("unused")
    private val cleaner: Cleaner? = if (owner == null) createCleaner(ptr) {
        //println("free feature")
        OGR_F_Destroy(it)
    } else null

    val properties: JsonObject by lazy {
        TODO()
    }

    var geometry: OgrGeometry? = null
        set(value) {
            //This function updates the features geometry, and operates the same as SetGeometryDirectly(),
            // except that this function does not assume ownership of the passed geometry, but instead makes a copy of it.
            OGR_F_SetGeometry(ptr, value?.ptr).requireSuccess {
                "failed adding geometry to feature"
            }
            field = value
        }
        get() = OGR_F_GetGeometryRef(ptr)?.let {
            OgrGeometry(it, this)
        }
}