@file:OptIn(ExperimentalForeignApi::class, ExperimentalNativeApi::class)

import io.madrona.njord.geojson.Feature
import io.madrona.njord.geojson.Geometry
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.IntVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.toKString
import kotlinx.cinterop.value
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import libgdal.*
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.ref.Cleaner
import kotlin.native.ref.createCleaner


class OgrFeature(
    val ptr: OGRFeatureH,
    val owner: OgrLayer? = null,
) {

    @Suppress("unused")
    private val cleaner: Cleaner? = if (owner == null) createCleaner(ptr) {
        //println("free feature")
        OGR_F_Destroy(it)
    } else null

    val properties: JsonObject by lazy {
        val count = OGR_F_GetFieldCount(ptr)
        JsonObject(buildMap {
            for (i in 0 until count) {
                if (OGR_F_IsFieldSetAndNotNull(ptr, i) != 1) continue
                val defn = OGR_F_GetFieldDefnRef(ptr, i) ?: continue
                val name = OGR_Fld_GetNameRef(defn)?.toKString() ?: continue
                val type = OGR_Fld_GetType(defn)
                val subType = OGR_Fld_GetSubType(defn)
                val value: JsonElement = when (type) {
                    OFTString -> JsonPrimitive(OGR_F_GetFieldAsString(ptr, i)?.toKString() ?: "")

                    OFTStringList -> {
                        val list = OGR_F_GetFieldAsStringList(ptr, i)
                        JsonArray(buildList {
                            var j = 0
                            while (true) {
                                add(JsonPrimitive(list?.get(j)?.toKString() ?: break))
                                j++
                            }
                        })
                    }

                    OFTInteger -> if (subType == OFSTBoolean) {
                        JsonPrimitive(OGR_F_GetFieldAsInteger(ptr, i) != 0)
                    } else {
                        JsonPrimitive(OGR_F_GetFieldAsInteger(ptr, i))
                    }

                    OFTIntegerList -> memScoped {
                        val countVar = alloc<IntVar>()
                        val arr = OGR_F_GetFieldAsIntegerList(ptr, i, countVar.ptr)
                        JsonArray((0 until countVar.value).map { j -> JsonPrimitive(arr!![j]) })
                    }

                    OFTReal -> JsonPrimitive(OGR_F_GetFieldAsDouble(ptr, i))

                    OFTRealList -> memScoped {
                        val countVar = alloc<IntVar>()
                        val arr = OGR_F_GetFieldAsDoubleList(ptr, i, countVar.ptr)
                        JsonArray((0 until countVar.value).map { j -> JsonPrimitive(arr!![j]) })
                    }

                    OFTInteger64 -> JsonPrimitive(OGR_F_GetFieldAsInteger64(ptr, i))

                    OFTInteger64List -> memScoped {
                        val countVar = alloc<IntVar>()
                        val arr = OGR_F_GetFieldAsInteger64List(ptr, i, countVar.ptr)
                        JsonArray((0 until countVar.value).map { j -> JsonPrimitive(arr!![j]) })
                    }

                    else -> JsonPrimitive(OGR_F_GetFieldAsString(ptr, i)?.toKString() ?: "")
                }
                put(name, value)
            }
        })
    }

    fun geoJson(): Feature = Feature(
        geometry = geometry?.geoJson(),
        properties = properties,
    )

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
