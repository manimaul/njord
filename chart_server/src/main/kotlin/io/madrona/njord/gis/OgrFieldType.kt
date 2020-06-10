package io.madrona.njord.gis

import org.gdal.ogr.Feature
import java.util.*

enum class OgrFieldType {
    OFTInteger,
    OFTIntegerList,
    OFTReal,
    OFTRealList,
    OFTString,
    OFTStringList,
    OFTWideString,
    OFTWideStringList,
    OFTBinary,
    OFTDate,
    OFTTime,
    OFTDateTime,
    OFTInteger64,
    OFTInteger64List;


    companion object {
        fun fromCode(code: Int): OgrFieldType? {
            return when (code) {
                0 -> OFTInteger
                1 -> OFTIntegerList
                2 -> OFTReal
                3 -> OFTRealList
                4 -> OFTString
                5 -> OFTStringList
                6 -> OFTWideString
                7 -> OFTWideStringList
                8 -> OFTBinary
                9 -> OFTDate
                10 -> OFTTime
                11 -> OFTDateTime
                12 -> OFTInteger64
                13 -> OFTInteger64List
                else -> null
            }
        }
    }
}

fun Feature.printFields() {
    println("field count = ${GetFieldCount()}")
    for (i in 0 until GetFieldCount()) {
        val ref = GetFieldDefnRef(i)
        val name = ref.GetName()
        when(getFieldType(i)) {
            OgrFieldType.OFTInteger -> println("$name = ${GetFieldAsInteger(i)}")
            OgrFieldType.OFTIntegerList -> println("$name = ${Arrays.toString(GetFieldAsIntegerList(i))}")
            OgrFieldType.OFTReal -> println("$name = ${GetFieldAsDouble(i)}")
            OgrFieldType.OFTRealList -> println("$name = ${Arrays.toString(GetFieldAsDoubleList(i))}")
            OgrFieldType.OFTString, OgrFieldType.OFTWideString -> println("$name = ${GetFieldAsString(i)}")
            OgrFieldType.OFTStringList, OgrFieldType.OFTWideStringList  -> println("$name = ${Arrays.toString(GetFieldAsStringList(i))}")
            OgrFieldType.OFTBinary -> println("$name = ${GetFieldAsBinary(i)}")
            OgrFieldType.OFTDate, OgrFieldType.OFTTime, OgrFieldType.OFTDateTime -> println("$name = TODO - JNI date") //${GetFieldAsDateTime(i)}
            OgrFieldType.OFTInteger64 -> println("$name = ${GetFieldAsInteger64(i)}")
            OgrFieldType.OFTInteger64List -> println("$name = ${Arrays.toString(GetFieldAsIntegerList(i))}")
        }
    }
}

fun Feature.getFieldType(i: Int) : OgrFieldType? {
    return OgrFieldType.fromCode(GetFieldType(i))

}