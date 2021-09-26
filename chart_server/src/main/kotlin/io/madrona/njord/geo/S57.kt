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
import org.gdal.ogr.FieldDefn
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

    val layerGeoJson: Map<String, FeatureCollection> by lazy {
        layerGeoJsonSequence().toMap()
    }

    fun layerGeoJsonSequence(): Sequence<Pair<String, FeatureCollection>> {
        return dataSet.layers().mapNotNull { layer ->
            val name = layer.GetName()
            if (inLayers == null || inLayers.contains(name)) {
                name to layer.featureCollection()
            } else {
                null
            }
        }
    }

    /**
     * export OGR_S57_OPTIONS="RETURN_PRIMITIVES=OFF,RETURN_LINKAGES=OFF,LNAM_REFS=ON:UPDATES:APPLY,SPLIT_MULTIPOINT:ON,RECODE_BY_DSSI:ON:ADD_SOUNDG_DEPTH=ON"
     * ogr2ogr -t_srs 'EPSG:4326' -f GeoJSON $(pwd)/ogr_SOUNDG.json $(pwd)/US5WA22M.000 SOUNDG
     * ogr2ogr -t_srs 'EPSG:4326' -f GeoJSON $(pwd)/ogr_BOYSPP.json $(pwd)/US3WA46M.000 BOYSPP
     * ogr2ogr -t_srs 'EPSG:4326' -f GeoJSON $(pwd)/ogr_DSID.json $(pwd)/US5WA22M.000 DSID
     */
    fun renderGeoJson(
        outDir: File,
        msg: (String) -> Unit
    ) {
        layerGeoJsonSequence().forEach {
            val name = "${it.first}.json"
            msg(name)
            objectMapper.writeValue(File(outDir, name), it.second)
        }
    }

    private fun Feature.geoJsonFeature(): mil.nga.sf.geojson.Feature? {
        return GetGeometryRef()?.geoJson()?.let { geoJson ->
            mil.nga.sf.geojson.Feature().also {
                it.geometry = FeatureConverter.toGeometry(geoJson)
                it.properties = fields()
            }
        } ?: run {
            fields().takeIf { it.isNotEmpty() }?.let { fields ->
                mil.nga.sf.geojson.Feature().also {
                    it.geometry = null
                    it.properties = fields
                }
            }
        }
    }

    private fun Feature.fields(): Map<String, Any?> {
        return (0 until GetFieldCount()).asSequence().map { id ->
            GetFieldDefnRef(id).let {
                val name = it.GetName()
                name to value(it.GetFieldType(), id)
            }
        }.toMap()
    }

    private fun Feature.value(type: Int, id: Int): Any? {
        return when (type) {
            OFTInteger -> GetFieldAsInteger(id)
            OFTIntegerList -> GetFieldAsIntegerList(id)
            OFTReal -> GetFieldAsDouble(id)
            OFTRealList -> GetFieldAsDoubleList(id)
            OFTDate,
            OFTTime,
            OFTDateTime,
            OFTWideString,
            OFTString -> GetFieldAsString(id)
            OFTStringList,
            OFTWideStringList -> GetFieldAsStringList(id)
            OFTBinary -> GetFieldAsBinary(id)
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

    private fun Geometry.geoJson(): String? {
        wgs84()
        return ExportToJson()
    }

    companion object {
        /**
         * https://gdal.org/drivers/vector/s57.html
         */
        private const val OGR_S57_OPTIONS_K = "OGR_S57_OPTIONS"

        /**
         * https://gdal.org/drivers/vector/s57.html#s-57-export
         */
        private const val OGR_S57_EXPORT_MIN = "RETURN_PRIMITIVES=OFF,RETURN_LINKAGES=OFF,LNAM_REFS=ON"

        private const val OGR_S57_OPTIONS_V =
            "${OGR_S57_EXPORT_MIN}:UPDATES:APPLY,SPLIT_MULTIPOINT:ON,RECODE_BY_DSSI:ON:ADD_SOUNDG_DEPTH=ON"

        init {
            gdal.AllRegister()
            gdal.SetConfigOption(OGR_S57_OPTIONS_K, OGR_S57_OPTIONS_V)
        }
    }
}
