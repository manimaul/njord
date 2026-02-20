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
            mvtDataset.addFeature(
                "DEBUG", mapOf(
                    "DMSG" to "$z, $x, $y".json
                ), it
            )
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
                val chartGeo = OgrGeometry.fromWkb4326(chart.covrWKB) ?: error("chart cover geo not valid")
                if (!include.isEmpty() && chart.zoom in 0..z) {
                    chartDao.findChartFeaturesAsync4326(
                        inclusionMask = include.wkb,
                        chartId = chart.id,
                        zoom = z,
                    )?.forEach { feature ->
                        val props = layerFactory.preTileEncode(feature).props.filtered().also {
                            it["CID"] = chart.id.json
                        }
                        feature.geomWKB?.let { OgrGeometry.fromWkb4326(it) }?.takeIf { it.isValid && !it.isEmpty() }?.let { tileGeo ->
                            if (info) {
                                infoFeatures.add(
                                    ChartFeatureInfo(
                                        feature.layer,
                                        props,
                                        tileGeo.geoJson()
                                    )
                                )
                            }
                            mvtDataset.addFeature(feature.layer, props, tileGeo)
                        }
                    }
                }
                include = include.difference(chartGeo) ?: include
                chartGeo.intersection(tileEnvelope)?.let {
                    mvtDataset.addFeature("PLY", emptyMap(), it)
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
