package io.madrona.njord.geo

import io.madrona.njord.Singletons
import io.madrona.njord.db.InsertError
import io.madrona.njord.db.InsertSuccess
import io.madrona.njord.db.Insertable
import io.madrona.njord.ext.json
import io.madrona.njord.geo.symbols.*
import io.madrona.njord.geojson.FeatureBuilder
import io.madrona.njord.geojson.FeatureCollection
import io.madrona.njord.geojson.intValue
import io.madrona.njord.geojson.stringValue
import io.madrona.njord.model.ChartInsert
import io.madrona.njord.model.LayerGeoJson
import io.madrona.njord.util.ZFinder
import kotlinx.coroutines.*
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import org.gdal.gdal.Dataset
import org.gdal.gdal.gdal
import org.gdal.ogr.Feature
import org.gdal.ogr.Geometry
import org.gdal.ogr.Layer
import org.gdal.ogr.ogrConstants.*
import org.gdal.osr.SpatialReference
import java.io.File
import java.util.concurrent.Executors

class S57(
    val file: File,
    private val sr4326: SpatialReference = Singletons.wgs84SpatialRef,
    private val zFinder: ZFinder = Singletons.zFinder,
    private val dispatcher: CoroutineDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher(),
) : CoroutineScope by CoroutineScope(dispatcher + SupervisorJob()) {

    private val dataSet by lazy {
        runBlocking { dataSourceAccess { gdal.OpenEx(file.absolutePath) } }
    }

    private suspend fun <R> dataSourceAccess(block: suspend CoroutineScope.() -> R): R {
        return coroutineScope {
            async(dispatcher) {
                block()
            }.await()
        }
    }

    suspend fun layerNames(): List<String> {
        return dataSourceAccess { layers }
    }

    private val layers: List<String> by lazy {
        dataSet.layers().map { it.GetName() }.toList()
    }

    suspend fun findLayer(name: String): FeatureCollection? {
        return dataSourceAccess {
            dataSet.GetLayer(name)?.featureCollection()
        }
    }

    suspend fun chartInsertInfo(): Insertable<ChartInsert> {
        val chartTxt = file.parentFile.listFiles { _: File, name: String ->
            name.endsWith(".TXT", true)
        }?.map {
            it.name to it.readText().replace('\u0000', ' ')
        }?.toMap() ?: emptyMap()

        val dsid = findLayer("DSID") ?: return InsertError("dsid is missing")
        val mcovr = findLayer("M_COVR")?.features?.filter {
            it.properties.intValue("CATCOV") == 1
        } ?: return InsertError("M_COVR is missing")
        val combinedCoverage = if (mcovr.size == 1) {
            mcovr.first()
        } else {
            // Multiple coverage polygons - merge them into a single multipolygon
            val polygons = mcovr.mapNotNull { feature ->
                feature.geometry as? io.madrona.njord.geojson.Polygon
            }
            val multiPolygon = io.madrona.njord.geojson.MultiPolygon(polygons)
            io.madrona.njord.geojson.Feature(
                geometry = multiPolygon
            )
        }
        val props = dsid.features?.firstOrNull()?.properties ?: return InsertError("DSID props are missing")
        val scale = props.intValue("DSPM_CSCL") ?: return InsertError("DSID DSPM_CSCL is missing")

        return InsertSuccess(
            ChartInsert(
                name = props.stringValue("DSID_DSNM") ?: "",
                scale = scale,
                fileName = file.name,
                updated = props.stringValue("DSID_UADT") ?: "",
                issued = props.stringValue("DSID_ISDT") ?: "",
                zoom = zFinder.findZoom(scale),
                covr = combinedCoverage,
                dsidProps = props,
                chartTxt = chartTxt
            )
        )
    }

    suspend fun layerGeoJsonSequence(exLayers: Set<String>? = null): Sequence<LayerGeoJson> {
        return dataSourceAccess {
            dataSet.layers().mapNotNull { layer ->
                layer.GetName()?.takeIf { exLayers == null || !exLayers.contains(it) }?.let {
                    LayerGeoJson(it, layer.featureCollection())
                }
            }
        }
    }

    private fun Feature.geoJsonFeature(soundg: Boolean = false): io.madrona.njord.geojson.Feature? {
        return FeatureBuilder(GetGeometryRef()?.geoJson()).also { featureBuilder ->
            fields().also { props ->
                featureBuilder.addAll(props)
                props.intValue("SCAMIN")?.takeIf { it > 0 }?.let {
                    featureBuilder.addProperty("MINZ", zFinder.findZoom(it))
                }
                props.intValue("SCAMAX")?.takeIf { it > 0 }?.let {
                    featureBuilder.addProperty("MAXZ", zFinder.findZoom(it))
                }
            }
            if (soundg) featureBuilder.addSounding()
        }.build()
    }

    private fun Feature.fields(): S57Prop {
        return (0 until GetFieldCount()).asSequence().map { id ->
            GetFieldDefnRef(id).let {
                val name = it.GetName()
                name to value(it.GetFieldType(), id)
            }
        }.toMap().toMutableMap()
    }

    private fun Feature.value(type: Int, id: Int): JsonElement {
        return when (type) {
            OFTInteger -> GetFieldAsInteger(id).json
            OFTIntegerList -> GetFieldAsIntegerList(id).json
            OFTReal -> GetFieldAsDouble(id).json
            OFTRealList -> GetFieldAsDoubleList(id).json
            OFTDate,
            OFTTime,
            OFTDateTime,
            OFTWideString,
            OFTString -> GetFieldAsString(id).json

            OFTStringList,
            OFTWideStringList -> GetFieldAsStringList(id).json

            OFTBinary -> GetFieldAsBinary(id).json
            OFTInteger64 -> GetFieldAsInteger64(id).json
            OFTInteger64List -> GetFieldAsStringList(id).mapNotNull { it.toLongOrNull() }.json
            else -> JsonNull
        }
    }

    private fun Layer.featureCollection(): FeatureCollection {
        val name = GetName()
        return FeatureCollection(
            features = features().mapNotNull {
                it.geoJsonFeature("SOUNDG".equals(name, ignoreCase = false))
            }.toList())
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
        private const val OGR_S57_OPTIONS_V =
            "RETURN_PRIMITIVES=OFF,RETURN_LINKAGES=OFF,LNAM_REFS=ON,UPDATES=APPLY,SPLIT_MULTIPOINT=ON,RECODE_BY_DSSI=ON:ADD_SOUNDG_DEPTH=ON"

        init {
            gdal.AllRegister()
            gdal.SetConfigOption(OGR_S57_OPTIONS_K, OGR_S57_OPTIONS_V)
        }

        fun from(file: File): S57? {
            return file.takeIf { it.name.endsWith(".000") }?.let {
                try {
                    S57(it).takeIf {
                        it.layers.isNotEmpty()
                    } ?: run {
                        log.warn("empty layers in file ${file.absolutePath}")
                        null
                    }
                } catch (e: Exception) {
                    null
                }
            }
        }
    }
}
