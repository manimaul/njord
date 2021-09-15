package io.madrona.njord.geo

import com.fasterxml.jackson.databind.ObjectMapper
import io.madrona.njord.Singletons
import mil.nga.sf.geojson.FeatureCollection
import org.gdal.gdal.Dataset
import org.gdal.gdal.gdal
import org.gdal.ogr.Feature
import org.gdal.ogr.Geometry
import org.gdal.ogr.Layer
import org.gdal.osr.SpatialReference
import mil.nga.sf.geojson.FeatureConverter
import org.gdal.ogr.ogrConstants.*
import java.io.File
import java.lang.RuntimeException

class S57(
    file: File,
    private val inLayers: Set<String>? = null,
    private val sr4326: SpatialReference = Singletons.wgs84SpatialRef,
    private val objectMapper: ObjectMapper = Singletons.objectMapper,
) {

    private val dataSet: Dataset

    init {
        dataSet = gdal.OpenEx(file.absolutePath)
    }

    val layerNames: Set<String> by lazy {
        layerGeoJson.keys
    }

    val layerGeoJson: Map<String, FeatureCollection> by lazy {
        dataSet.layers().fold(mutableMapOf()) { acc, layer ->
            val name = layer.GetName()
            if (inLayers == null || inLayers.contains(name)) {
                acc[layer.GetName()] = layer.featureCollection()
            }
            acc
        }
    }

    fun renderGeoJson(
        outDir: File
    ) {
        layerGeoJson.forEach {
            objectMapper.writeValue(File(outDir, "${it.key}.json"), it.value)
        }
    }

    private fun Feature.geoJsonFeature(): mil.nga.sf.geojson.Feature? {
        return GetGeometryRef()?.geoJson()?.let { geoJson ->
            mil.nga.sf.geojson.Feature().also {
                it.geometry = FeatureConverter.toGeometry(geoJson)
                it.properties = fields()
            }
        }
    }

    private fun Feature.fields(): Map<String, Any?> {
        return (0 until GetFieldCount()).asSequence().map {
            GetFieldDefnRef(it).GetName() to value(it)
        }.toMap()
    }

    private fun Feature.value(id: Int): Any? {
        return when (GetFieldType(id)) {
            OFTInteger -> GetFieldAsInteger(id)
            OFTIntegerList -> GetFieldAsIntegerList(id)
            OFTReal -> GetFieldAsDouble(id)
            OFTRealList -> GetFieldAsDoubleList(id)
            OFTWideString,
            OFTString -> GetFieldAsString(id)
            OFTStringList,
            OFTWideStringList -> GetFieldAsStringList(id)
            OFTBinary -> GetFieldAsBinary(id)
            OFTDate,
            OFTTime,
            OFTDateTime,
            OFTInteger64 -> GetFieldAsInteger64(id)
            OFTInteger64List -> GetFieldAsStringList(id)
            else -> null
        }
    }

    private fun Layer.featureCollection(): FeatureCollection {
        return FeatureCollection().also { fc ->
            fc.addFeatures(features().mapNotNull { feat ->
                feat.geoJsonFeature()
            }.toList())
        }
    }

    private fun Layer.features(): Sequence<Feature> {
        ResetReading()
        return (0 until GetFeatureCount()).asSequence().mapNotNull {
            GetNextFeature()
        }
    }

    private fun Dataset.layers(): Sequence<Layer> {
        ResetReading()
        return (0 until GetLayerCount()).asSequence().mapNotNull {
            GetLayer(it)
        }
    }


    private fun Geometry.wgs84() {
        if (GetSpatialReference()?.IsSameGeogCS(sr4326) != 1) {
            val result = TransformTo(sr4326)
            if (result != 0) {
                throw RuntimeException("failed to transform Geometry to wgs84")
            }
        }
    }

    fun Geometry.geoJson(): String? {
        wgs84()
        return ExportToJson()
    }
}

