package io.madrona.njord.gdal

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
        println("name = ${ref.GetName()}")
        when(val ft = getFieldType(i)) {
            OgrFieldType.OFTInteger -> println("${ft.name} = ${GetFieldAsInteger(i)}")
            OgrFieldType.OFTIntegerList -> println("${ft.name} = ${Arrays.toString(GetFieldAsIntegerList(i))}")
            OgrFieldType.OFTReal -> println("${ft.name} = ${GetFieldAsDouble(i)}")
            OgrFieldType.OFTRealList -> println("${ft.name} = ${Arrays.toString(GetFieldAsDoubleList(i))}")
            OgrFieldType.OFTString, OgrFieldType.OFTWideString -> println("${ft.name} = ${GetFieldAsString(i)}")
            OgrFieldType.OFTStringList, OgrFieldType.OFTWideStringList  -> println("${ft.name} = ${Arrays.toString(GetFieldAsStringList(i))}")
            OgrFieldType.OFTBinary -> println("${ft.name} = ${GetFieldAsBinary(i)}")
            OgrFieldType.OFTDate, OgrFieldType.OFTTime, OgrFieldType.OFTDateTime -> println("${ft.name} = TODO - JNI date") //${GetFieldAsDateTime(i)}
            OgrFieldType.OFTInteger64 -> println("${ft.name} = ${GetFieldAsInteger64(i)}")
            OgrFieldType.OFTInteger64List -> println("${ft.name} = ${Arrays.toString(GetFieldAsIntegerList(i))}")
        }
    }
}

fun Feature.getFieldType(i: Int) : OgrFieldType? {
    return OgrFieldType.fromCode(GetFieldType(i))

}