@file:OptIn(ExperimentalForeignApi::class)

import kotlinx.cinterop.ExperimentalForeignApi
import libgdal.OGRFeatureH
import libgdal.OGR_F_Destroy
import libgdal.OGR_F_GetGeometryRef
import libgdal.OGR_F_SetGeometry
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.ref.Cleaner
import kotlin.native.ref.createCleaner


class OgrFeature(
    val ptr: OGRFeatureH,
) {

    @OptIn(ExperimentalNativeApi::class)
    private val cleaner: Cleaner = createCleaner(ptr) {
        OGR_F_Destroy(it)
    }

    var geometry: OgrGeometry? = null
        set(value) {
            OGR_F_SetGeometry(ptr, value?.ptr).requireSuccess {
                "failed adding geometry to feature"
            }
            field = value
        }
        get() = OGR_F_GetGeometryRef(ptr)?.let {
            OgrGeometry(it, false)
        }
}
