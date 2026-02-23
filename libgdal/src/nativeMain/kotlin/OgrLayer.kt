@file:OptIn(ExperimentalForeignApi::class, ExperimentalNativeApi::class)

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.toKString
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
import libgdal.*
import kotlin.experimental.ExperimentalNativeApi

class OgrLayer(
    val ptr: OGRLayerH
) {

    val name: String?
        get() = OGR_L_GetName(ptr)?.toKString()

    val featureCount: Long
        get() = OGR_L_GetFeatureCount(ptr, 1)


    val features: List<OgrFeature> by lazy {
        OGR_L_ResetReading(ptr)
        buildList {
            while (true) {
                val feature = OGR_L_GetNextFeature(ptr) ?: break
                add(OgrFeature(feature, this))
            }
        }
    }

    fun addFeature(geometry: OgrGeometry, props: Map<String, JsonElement> = emptyMap()) {
        val data = props.mapNotNull {
            createFieldSchema(it.key, it.value)
        }

        val layerDef: OGRFeatureDefnH = OGR_L_GetLayerDefn(ptr) ?: error("OGR_L_GetLayerDefn failed")
        val feature: OGRFeatureH = OGR_F_Create(layerDef) ?: error("OGR_F_Create failed")

        //This function updates the features geometry, and operates the same as SetGeometryDirectly(), except that this
        // function does not assume ownership of the passed geometry, but instead makes a copy of it.
        OGR_F_SetGeometry(feature, geometry.ptr)

        data.forEach {
            bindFeatureProperty(feature, it)
        }

        OGR_L_CreateFeature(ptr, feature).requireSuccess { "OGR_L_CreateFeature failed" }

        OGR_F_Destroy(feature)
    }

    private fun createFieldSchema(key: String, value: JsonElement) : FieldData? {
        val index = OGR_L_FindFieldIndex(ptr, key, 1)

        return value.fieldType()?.let {
            if (index == -1) {
                val fieldDef: OGRFieldDefnH =
                    OGR_Fld_Create(key, it.nativeType) ?: error("OGR_Fld_Create failed")
                OGR_Fld_SetName(fieldDef, key)
                OGR_Fld_SetType(fieldDef, it.nativeType)
                OGR_Fld_SetSubType(fieldDef, it.nativeSubType)
                OGR_L_CreateField(ptr, fieldDef, 1).requireSuccess {
                    "OGR_L_CreateField failed"
                }
                OGR_Fld_Destroy(fieldDef)
            }
            FieldData(key, it, value, index)
        }
    }

    private fun bindFeatureProperty(fp: OGRFeatureH, fieldData: FieldData) {
        val fieldIndex = if (fieldData.index == -1) {
            OGR_F_GetFieldIndex(fp, fieldData.key)
        } else {
            fieldData.index
        }
        when (fieldData.fieldType) {
            OgrFieldType.StringField -> memScoped {
                val strValue = (fieldData.value as JsonPrimitive).content
                OGR_F_SetFieldString(fp, 0, strValue)
            }

            OgrFieldType.StringListField -> memScoped {
                val args = (fieldData.value as JsonArray).mapNotNull { (it as? JsonPrimitive)?.content }
                val arr = allocStringArray(args)
                OGR_F_SetFieldStringList(fp, fieldIndex, arr)
            }

            OgrFieldType.IntField -> {
                val intValue = (fieldData.value as JsonPrimitive).int
                OGR_F_SetFieldInteger(fp, fieldIndex, intValue)
            }

            OgrFieldType.IntListField -> memScoped {
                val jsonList = (fieldData.value as JsonArray).mapNotNull { (it as? JsonPrimitive)?.intOrNull }
                val count = jsonList.size
                val nativeArray = allocIntArray(jsonList)
                OGR_F_SetFieldIntegerList(fp, fieldIndex, count, nativeArray)
            }

            OgrFieldType.BooleanField -> OGR_F_SetFieldInteger(
                fp,
                fieldIndex,
                if ((fieldData.value as JsonPrimitive).boolean) 1 else 0
            )

            OgrFieldType.DoubleField -> OGR_F_SetFieldDouble(fp, fieldIndex, (fieldData.value as JsonPrimitive).double)
            OgrFieldType.DoubleListField -> memScoped {
                val args =
                    (fieldData.value as JsonArray).mapNotNull { (it as? JsonPrimitive)?.doubleOrNull }
                val nativeArray = allocDoubleArray(args)
                OGR_F_SetFieldDoubleList(fp, fieldIndex, args.size, nativeArray)
            }

            OgrFieldType.LongField -> OGR_F_SetFieldInteger64(fp, fieldIndex, (fieldData.value as JsonPrimitive).long)
            OgrFieldType.LongListField -> memScoped {
                val args =
                    (fieldData.value as JsonArray).mapNotNull { (it as? JsonPrimitive)?.longOrNull }
                val nativeArray = allocLongArray(args)
                OGR_F_SetFieldInteger64List(fp, fieldIndex, args.size, nativeArray)
            }
        }
    }
}

enum class OgrFieldType(val nativeType: OGRFieldType, val nativeSubType: OGRFieldSubType) {
    StringField(OFTString, OFSTNone),
    StringListField(OFTStringList, OFSTNone),
    IntField(OFTInteger, OFSTNone),
    IntListField(OFTIntegerList, OFSTNone),
    BooleanField(OFTInteger, OFSTBoolean),
    DoubleField(OFTReal, OFSTNone),
    DoubleListField(OFTRealList, OFSTNone),
    LongField(OFTInteger64, OFSTNone),
    LongListField(OFTInteger64List, OFSTNone),
}

data class FieldData(
    val key: String,
    val fieldType: OgrFieldType,
    val value: JsonElement,
    val index: Int,
)

private fun JsonElement.fieldType(): OgrFieldType? {
    return when {
        this is JsonPrimitive && this.isString -> OgrFieldType.StringField

        this is JsonPrimitive && this.booleanOrNull != null -> OgrFieldType.BooleanField

        this is JsonPrimitive && this.intOrNull != null -> OgrFieldType.IntField

        this is JsonPrimitive && this.longOrNull != null -> OgrFieldType.LongField

        this is JsonPrimitive && this.doubleOrNull != null -> OgrFieldType.DoubleField

        this is JsonArray -> {
            if (this.isNotEmpty()) {
                val first = this.firstOrNull()
                when {
                    first is JsonPrimitive && first.isString -> OgrFieldType.StringListField

                    first is JsonPrimitive && first.intOrNull != null -> OgrFieldType.IntListField

                    first is JsonPrimitive && first.longOrNull != null -> OgrFieldType.LongListField

                    first is JsonPrimitive && first.doubleOrNull != null -> OgrFieldType.DoubleListField
                    else -> null
                }
            } else {
                null
            }
        }

        else -> null
    }
}
