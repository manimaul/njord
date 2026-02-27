package io.madrona.njord.geo

import OgrGeometry
import TileSystem
import io.madrona.njord.Singletons
import io.madrona.njord.db.ChartDao
import io.madrona.njord.ext.json
import io.madrona.njord.geo.symbols.S57ObjectLibrary
import io.madrona.njord.layers.LayerFactory
import io.madrona.njord.model.ChartFeatureInfo
import kotlinx.serialization.json.*
import tile.VectorTileEncoder
import transformToTilePixels

class TileEncoder(
    val x: Int,
    val y: Int,
    val z: Int,
    val tileSystem: TileSystem = Singletons.tileSystem,
    val vectorTileEncoder: VectorTileEncoder = VectorTileEncoder(),
    val chartDao: ChartDao = Singletons.chartDao,
    val layerFactory: LayerFactory = Singletons.layerFactory,
    val s57ObjectLibrary: S57ObjectLibrary = Singletons.s57ObjectLibrary,
) {

    private val tileEnvelope: OgrGeometry = tileSystem.createTileClipPolygon(x, y, z)

    fun addDebug(): TileEncoder {
        vectorTileEncoder.addDebugEnvelope(
            "DEBUG", mapOf(
                "DMSG" to "$z, $x, $y".json
            )
        )
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
        //we need to expand the polygon so that lines are not drawn on the edge of the tile
        var include: OgrGeometry = tileSystem.createTileClipPolygon(x, y, z, expandPixels = 15)
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
                        feature.geomWKB?.let { OgrGeometry.fromWkb4326(it) }?.takeIf { it.isValid && !it.isEmpty() }
                            ?.let { tileGeo ->
                                if (info) {
                                    infoFeatures.add(
                                        ChartFeatureInfo(
                                            feature.layer,
                                            props,
                                            tileGeo.geoJson()
                                        )
                                    )
                                }
                                transformToTilePixels(tileGeo, x, y, z, tileSystem)
                                vectorTileEncoder.addFeature(
                                    feature.layer,
                                    props,
                                    tileGeo
                                )
                            }
                    }
                    include = include.difference(chartGeo) ?: include
                }
                val boundary = chartGeo.boundary()
                boundary?.let {
                    transformToTilePixels(it, x, y, z, tileSystem)
                    vectorTileEncoder.addFeature("PLY", emptyMap(), it)
                }
            }
        }

        // Encode base map features inside un rendered "include"
        if (!include.isEmpty() && !info) {
            chartDao.findBaseInfoAsync(findBaseMapScale(z))?.forEach { chart ->
                chartDao.findChartFeaturesAsync4326(
                    inclusionMask = include.wkb,
                    chartId = chart.id,
                    zoom = z,
                )?.forEach { feature ->
                    val props = layerFactory.preTileEncode(feature).props.filtered().also {
                        it["CID"] = chart.id.json
                        it["BASE"] = chart.name.json
                        it["SCALE"] = chart.scale.json
                    }
                    feature.geomWKB?.let { OgrGeometry.fromWkb4326(it) }?.takeIf { it.isValid && !it.isEmpty() }
                        ?.let { tileGeo ->
                            transformToTilePixels(tileGeo, x, y, z, tileSystem)
                            vectorTileEncoder.addFeature(
                                feature.layer,
                                props,
                                tileGeo
                            )
                        }
                }
            }
        }
        return this
    }


    /**
     *   ┌────────────────────┬─────────────┬────────────────────┐
     *   │        Data        │ Scale value │ Natural zoom range │
     *   ├────────────────────┼─────────────┼────────────────────┤
     *   │ NE 110m            │ 110,000,000 │ z 0–2              │
     *   ├────────────────────┼─────────────┼────────────────────┤
     *   │ NE 50m             │ 50,000,000  │ z 3–4              │
     *   ├────────────────────┼─────────────┼────────────────────┤
     *   │ NE 10m             │ 10,000,000  │ z 5–6              │
     *   └────────────────────┴─────────────┴────────────────────┘
     */
    fun findBaseMapScale(z: Int): Int {
        return if (z < 3) {
            110_000_000
        } else if (z < 5) {
            50_000_000
        } else {
            10_000_000
        }
    }


    fun encode(): ByteArray {
        return vectorTileEncoder.encode()
    }

    fun infoJson() = infoFeatures
}
