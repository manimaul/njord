package io.madrona.njord.geo

import MvtDataset
import OgrGeometry
import TileSystem
import io.madrona.njord.Singletons
import io.madrona.njord.db.ChartDao
import io.madrona.njord.ext.json
import io.madrona.njord.geo.symbols.S57ObjectLibrary
import io.madrona.njord.layers.LayerFactory
import io.madrona.njord.model.ChartFeatureInfo
import kotlinx.serialization.json.*

class TileEncoder(
    val x: Int,
    val y: Int,
    val z: Int,
    private val tileSystem: TileSystem = Singletons.tileSystem,
    private val mvtDataset: MvtDataset = MvtDataset(),
    private val chartDao: ChartDao = Singletons.chartDao,
    private val layerFactory: LayerFactory = Singletons.layerFactory,
    private val s57ObjectLibrary: S57ObjectLibrary = Singletons.s57ObjectLibrary,
) {

    private val tileEnvelope: OgrGeometry = tileSystem.createTileClipPolygon(x, y, z)

    fun addDebug(): TileEncoder {
        tileEnvelope.centroid().takeIf { !it.isEmpty() }?.let {
            mvtDataset.addFeature("DEBUG", mapOf(
                "DMSG" to "$z, $x, $y".json
            ), it)
        }
        return this
    }

    private val infoFeatures by lazy {
        mutableListOf<ChartFeatureInfo>()
    }

    private fun MutableMap<String, JsonElement>.filtered(): MutableMap<String, JsonElement> {
        iterator().also { itor ->
            while (itor.hasNext()) {
                itor.next().also { entry ->
                    when (val jp = entry.value) {
                        is JsonPrimitive -> {
                            if (jp.isString && jp.content.isBlank()) {
                                itor.remove()
                            }
                            if (jp.content == "0" && s57ObjectLibrary.attributes[entry.key]?.attributeType == "E") {
                                itor.remove()
                            }
                        }

                        is JsonArray -> {
                            if (jp.isEmpty()) {
                                itor.remove()
                            }
                        }

                        else -> {
                            error("unable to filter unexpected element type ${jp::class.simpleName}")
                        }
                    }
                }
            }
        }
        return this
    }

    suspend fun addCharts(info: Boolean): TileEncoder {
        var include: OgrGeometry = tileSystem.createTileClipPolygon(x, y, z) //wgs84
        chartDao.findInfoAsync(tileEnvelope.wkb)?.let { charts ->
            charts.forEach { chart ->
                if (!include.isEmpty() && chart.zoom in 0..z) {
                    chartDao.findChartFeaturesAsync4326(
                        inclusionMask = include.wkb,
                        chartId = chart.id,
                        zoom = z
                    )?.filter {
                        it.geomWKB != null
                    }?.forEach { feature ->
                        layerFactory.preTileEncode(feature)
                        feature.geomWKB?.let { OgrGeometry.fromWkb4326(it) }?.let { geo ->
                            val props = feature.props.filtered().also {
                                it["CID"] = chart.id.json
                            }
                            if (info) {
                                infoFeatures.add(
                                    ChartFeatureInfo(
                                        feature.layer,
                                        props,
                                        geo.geoJson()
                                    )
                                )
                            }
                            mvtDataset.addFeature(feature.layer, props, geo)
                        }
                    }
                }
                OgrGeometry.fromWkb4326(chart.covrWKB)?.let { chartCoverage ->
                    include.difference(chartCoverage)?.let { include = it }
                    chartCoverage.intersection(tileEnvelope)?.let {
                        mvtDataset.addFeature("PLY", emptyMap(), it)
                    }
                }
            }
        }
        return this
    }

    fun encode(): ByteArray {
        return mvtDataset.translateMvt(z, z).getMvt(z, x, y)
    }

    fun infoJson() = infoFeatures
}
