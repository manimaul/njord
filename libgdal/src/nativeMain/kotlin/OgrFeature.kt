@file:OptIn(ExperimentalForeignApi::class)

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CPointerVar
import kotlinx.cinterop.DoubleVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.IntVar
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.cstr
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.set
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.double
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.long
import kotlinx.serialization.json.longOrNull
import libgdal.OGRLayerH
import libgdal.OGR_F_Destroy
import libgdal.OGR_F_GetFieldIndex
import libgdal.OGR_F_SetFieldDouble
import libgdal.OGR_F_SetFieldDoubleList
import libgdal.OGR_F_SetFieldInteger
import libgdal.OGR_F_SetFieldInteger64
import libgdal.OGR_F_SetFieldIntegerList
import libgdal.OGR_F_SetFieldString
import libgdal.OGR_F_SetFieldStringList
import libgdal.OGR_F_SetGeometry
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.ref.Cleaner
import kotlin.native.ref.createCleaner

class OgrFeature(
    val ptr: OGRLayerH,
) {

    @OptIn(ExperimentalNativeApi::class)
    private val cleaner: Cleaner = createCleaner(ptr) {
        //println("closing feature")
        OGR_F_Destroy(it)
    }

    val name: String by lazy {
        ""
    }

    val fieldCount: Int by lazy {
        0
    }

    val fields: Map<String, JsonElement> by lazy {
        emptyMap()
    }

    var geometry: OgrGeometry? = null
        set(value) {
            OGR_F_SetGeometry(ptr, value?.ptr).requireSuccess {
                "failed adding geometry to feature"
            }
            field = value
        }

    fun addProperty(key: String, value: JsonElement) {
        val fieldIndex = OGR_F_GetFieldIndex(ptr, key)
        if (fieldIndex != -1) {
            when {
                value is JsonPrimitive && value.isString ->
                    OGR_F_SetFieldString(ptr, fieldIndex, value.content)

                value is JsonPrimitive && value.booleanOrNull != null ->
                    OGR_F_SetFieldInteger(ptr, fieldIndex, if (value.boolean) 1 else 0)

                value is JsonPrimitive && value.intOrNull != null ->
                    OGR_F_SetFieldInteger(ptr, fieldIndex, value.int)

                value is JsonPrimitive && value.longOrNull != null ->
                    OGR_F_SetFieldInteger64(ptr, fieldIndex, value.long)

                value is JsonPrimitive && value.doubleOrNull != null ->
                    OGR_F_SetFieldDouble(ptr, fieldIndex, value.double)

                value is JsonArray -> {
                    if (value.isNotEmpty()) {
                        val first = value.firstOrNull()
                        when {
                            first is JsonPrimitive && first.isString -> {
                                memScoped {
                                    val args = value.mapNotNull { (it as? JsonPrimitive)?.content }.toTypedArray()
                                    OGR_F_SetFieldStringList(ptr, fieldIndex, allocStringArray(*args))
                                }
                            }

                            first is JsonPrimitive && first.intOrNull != null -> {
                                memScoped {
                                    val list = value.mapNotNull { (it as? JsonPrimitive)?.intOrNull }
                                    val nativeArray = allocIntArray(*list.toIntArray())
                                    OGR_F_SetFieldIntegerList(ptr, fieldIndex, list.size, nativeArray)
                                }
                            }

                            first is JsonPrimitive && first.longOrNull != null -> {
                                memScoped {
                                    val list = value.mapNotNull { (it as? JsonPrimitive)?.intOrNull }
                                    val nativeArray = allocIntArray(*list.toIntArray())
                                    OGR_F_SetFieldIntegerList(ptr, fieldIndex, list.size, nativeArray)
                                }
                            }

                            first is JsonPrimitive && first.doubleOrNull != null -> {
                                memScoped {
                                    val list = value.mapNotNull { (it as? JsonPrimitive)?.doubleOrNull }
                                    val nativeArray = allocDoubleArray(*list.toDoubleArray())
                                    OGR_F_SetFieldDoubleList(ptr, fieldIndex, list.size, nativeArray)
                                }
                            }

                            else -> Unit
                        }
                    }
                }

            }
        }
    }
}
