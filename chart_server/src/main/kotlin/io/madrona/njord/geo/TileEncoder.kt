package io.madrona.njord.geo

import com.codahale.metrics.Timer
import io.madrona.njord.Singletons
import io.madrona.njord.db.ChartDao
import io.madrona.njord.geo.tile.VectorTileEncoder
import io.madrona.njord.layers.Depare
import org.locationtech.jts.geom.Geometry
import org.locationtech.jts.geom.GeometryFactory
import org.locationtech.jts.geom.Polygon
import org.locationtech.jts.io.WKBReader

class TileEncoder(
    val x: Int,
    val y: Int,
    val z: Int,
    private val tileSystem: TileSystem = Singletons.tileSystem,
    private val geometryFactory: GeometryFactory = Singletons.geometryFactory,
    //todo: self try removing clipping code ... we've already clipped
    private val encoder: VectorTileEncoder = VectorTileEncoder(4096, 8, false, true, 0.0),
    private val chartDao: ChartDao = ChartDao(),
    private val timer: Timer = Singletons.metrics.timer("TileEncoder"),
) {

    private val tileEnvelope: Polygon = tileSystem.createTileClipPolygon(x, y, z)

    fun addDebug(): TileEncoder {
        val tileGeom = tileSystem.tileGeometry(tileEnvelope.exteriorRing, x, y, z)
        encoder.addFeature("DEBUG", emptyMap<String, Any>(), tileGeom)
        encoder.addFeature(
            "DEBUG", mapOf<String, Any>(
                "DMSG" to "z:$z x:$x y:$y"
            ), tileGeom.centroid
        )
        return this
    }

    suspend fun addCharts(): TileEncoder {
        val ctx = timer.time()
        var include: Geometry = tileSystem.createTileClipPolygon(x, y, z) //wgs84
        var covered: Geometry = geometryFactory.createPolygon()
        chartDao.findInfoAsync(tileEnvelope).await()?.let { charts ->
            charts.forEach { chart ->
                val chartGeo = WKBReader().read(chart.covrWKB)
                if (!include.isEmpty && chart.zoom in 0..z) {
                    chartDao.findChartFeaturesAsync(covered, x, y, z, chart.id).await()?.filter {
                        it.geomWKB != null
                    }?.forEach { feature ->
                        val tileGeo = WKBReader().read(feature.geomWKB)
                        Singletons.symbolLayers[feature.layer]?.addSymbol(feature.props)
                        Depare.tileEncodeFillColor(feature)
                        encoder.addFeature(feature.layer, feature.props, tileGeo)
                    }
                    chartGeo?.let { geo ->
                        covered = covered.union(geo)
                        include = include.difference(covered)
                    }
                }
                addPly(chartGeo)
            }
        }
        ctx.stop()
        return this
    }

    private fun addPly(chartGeo: Geometry) {
        (chartGeo as? Polygon)?.let { ply ->
            val plyTile = tileSystem.tileGeometry(ply.exteriorRing, x, y, z)
            encoder.addFeature("PLY", emptyMap<String, Any?>(), plyTile)
        }
    }

    fun encode(): ByteArray {
        return encoder.encode()
    }
}
