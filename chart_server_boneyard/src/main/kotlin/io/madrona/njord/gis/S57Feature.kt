package io.madrona.njord.gis

import org.gdal.ogr.Feature
import org.gdal.ogr.FieldDefn

open class S57Feature(
        private val dsFeature: Feature
) {
    val wkt: String?
        get() = dsFeature.GetGeometryRef()?.wgs84Wkt()

    val geoJson: String?
        get() = dsFeature.GetGeometryRef()?.geoJson()

    val hasGeometry: Boolean
        get() = dsFeature.GetGeometryRef() != null

    val fields: List<S57Field> by lazy {
        IntRange(0, dsFeature.GetFieldCount() - 1).mapNotNull {
            if (dsFeature.IsFieldSet(it)) {
                dsFeature.GetDefnRef()?.GetFieldDefn(it)?.let { fieldDefn ->
                    S57Field(fieldDefn, dsFeature)
                }
            } else {
                null
            }
        }
    }

    val properties: Map<String, String> by lazy {
       mutableMapOf<String, String>().also { map ->
           fields.forEach {
               map[it.name] = it.valueString ?: ""
           }
       }
    }

    val fieldNames: List<String>
        get() = fields.map { it.name }
}

class S57Field(
        private val dsField: FieldDefn,
        private val dsFeature: Feature
) {

    val name: String
        get() = dsField.GetName()

    //todo: check field type ... maybe use sealed class
    val valueString: String?
        get() = dsFeature.GetFieldAsString(name)
}