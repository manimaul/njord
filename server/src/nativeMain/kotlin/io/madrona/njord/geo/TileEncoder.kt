package io.madrona.njord.geo

import OgrGeometry
import TileSystem
import io.madrona.njord.Singletons
import io.madrona.njord.db.BaseFeatureDao
import io.madrona.njord.db.ChartDao
import io.madrona.njord.ext.json
import io.madrona.njord.geo.symbols.S57ObjectLibrary
import io.madrona.njord.layers.LayerFactory
import io.madrona.njord.model.ChartFeature
import io.madrona.njord.model.ChartFeatureInfo
import kotlinx.serialization.json.*
import tile.VectorTileEncoder
import transformToTilePixels
import kotlin.time.Duration
import kotlin.time.measureTimedValue

class TileEncoder(
    val x: Int,
    val y: Int,
    val z: Int,
    val tileSystem: TileSystem = Singletons.tileSystem,
    val vectorTileEncoder: VectorTileEncoder = VectorTileEncoder(),
    val chartDao: ChartDao = Singletons.chartDao,
    val layerFactory: LayerFactory = Singletons.layerFactory,
    val s57ObjectLibrary: S57ObjectLibrary = Singletons.s57ObjectLibrary,
    val baseFeatureDao: BaseFeatureDao = Singletons.baseFeatureDao,
) {

    var chartQueryDuration: Duration = Duration.ZERO
        private set

    var featureQueryDuration: Duration = Duration.ZERO
        private set

    var preEncodeDuration: Duration = Duration.ZERO
        private set

    var geomOpDuration: Duration = Duration.ZERO
        private set

    var encodeDuration: Duration = Duration.ZERO
        private set

    var finalEncodeDuration: Duration = Duration.ZERO
        private set

    var baseMapDuration: Duration = Duration.ZERO
        private set

    fun spans() : String {
        return "chart query: $chartQueryDuration\n" +
                "feature query: $featureQueryDuration\n" +
                "pre encode: $preEncodeDuration\n" +
                "geometry ops: $geomOpDuration\n" +
                "encode: $encodeDuration\n" +
                "encode final pass: $finalEncodeDuration\n" +
                "base map: $baseMapDuration\n"
    }

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
        val tileWkb = include.wkb

        val (charts, cd) = measureTimedValue {
            chartDao.findInfoAsync(tileEnvelope.wkb)
        }
        chartQueryDuration
        chartQueryDuration = cd
        charts?.let { charts ->
            val eligibleChartIds = charts.filter { it.zoom in 0..z }.map { it.id }

            val (allFeatures, fd) = measureTimedValue {
                chartDao.findAllChartFeaturesAsync4326(tileWkb, eligibleChartIds, z) ?: emptyMap()
            }
            featureQueryDuration += fd

            charts.forEach { chart ->
                val chartGeo = OgrGeometry.fromWkb4326(chart.covrWKB) ?: error("chart cover geo not valid")
                if (!include.isEmpty() && chart.zoom in 0..z) {
                    val chartInclude = include
                    allFeatures[chart.id]?.forEach { feature ->

                        val (props, ed) = measureTimedValue {
                            layerFactory.preTileEncode(feature).props.filtered().also {
                                it["CID"] = chart.id.json
                            }
                        }
                        preEncodeDuration += ed

                        val (tileGeo, td) = measureTimedValue {
                            if (feature.layer == "LIGHTS") {
                                feature.geomWKB?.let { OgrGeometry.fromWkb4326(it) }
                                    ?.takeIf { it.isValid && !it.isEmpty() }
                            } else {
                                feature.geomWKB?.let { OgrGeometry.fromWkb4326(it) }
                                    ?.intersection(chartInclude)
                                    ?.takeIf { it.isValid && !it.isEmpty() }
                            }
                        }
                        geomOpDuration += td
                        tileGeo?.let { tileGeo ->
                            if (info) {
                                infoFeatures.add(
                                    ChartFeatureInfo(
                                        feature.layer,
                                        props,
                                        tileGeo.geoJson()
                                    )
                                )
                            }
                            val (tileGeo, tpd) = measureTimedValue {
                                transformToTilePixels(tileGeo, x, y, z, tileSystem)
                            }
                            geomOpDuration += tpd

                            encodeDuration += measureTimedValue {
                                vectorTileEncoder.addFeature(
                                    feature.layer,
                                    props,
                                    tileGeo
                                )
                            }.duration
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
            baseMapDuration = measureTimedValue {
                baseFeatureDao.findFeaturesAsync(findBaseMapScale(z), include.wkb)?.forEach { feature ->
                    val props = layerFactory.preTileEncode(feature).props.filtered()
                    feature.geomWKB?.let { OgrGeometry.fromWkb4326(it) }
                        ?.takeIf { it.isValid && !it.isEmpty() }
                        ?.let { tileGeo ->
                            transformToTilePixels(tileGeo, x, y, z, tileSystem)
                            vectorTileEncoder.addFeature(feature.layer, props, tileGeo)
                        }
                }
            }.duration
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
        val (mvt, duration) = measureTimedValue {
            vectorTileEncoder.encode()
        }
        finalEncodeDuration += duration
        return mvt
    }

    fun infoJson() : List<ChartFeatureInfo> {
        return infoFeatures
    }

}
