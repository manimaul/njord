package io.madrona.njord.geo

import com.codahale.metrics.Timer
import io.madrona.njord.Singletons
import io.madrona.njord.db.ChartDao
import no.ecc.vectortile.VectorTileEncoder
import org.locationtech.jts.geom.Geometry
import org.locationtech.jts.geom.GeometryFactory
import org.locationtech.jts.geom.Polygon
import org.locationtech.jts.io.WKBReader
import org.locationtech.jts.io.WKBWriter

class TileEncoder(
    val x: Int,
    val y: Int,
    val z: Int,
    private val tileSystem: TileSystem = Singletons.tileSystem,
    private val geometryFactory: GeometryFactory = Singletons.geometryFactory,
    private val encoder: VectorTileEncoder = VectorTileEncoder(4096, 8, false, true),
    private val chartDao: ChartDao = ChartDao(),
    private val timer: Timer = Singletons.metrics.timer("TileEncoder"),
    private val wkbReader: WKBReader = Singletons.wkbReader
) {

    private val tileEnvelope: Polygon = tileSystem.createTileClipPolygon(x, y, z)

    fun addDebug(): TileEncoder {
        val tileGeom = tileSystem.tileGeometry(tileEnvelope, x, y, z)
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
        var include = tileEnvelope.copy() //wgs84
        var covered: Geometry = geometryFactory.createPolygon()
        chartDao.findInfoAsync(tileEnvelope, z).await()?.let { charts ->
            charts.forEach { chart ->
                if (include.isEmpty) {
                    return@forEach
                }
                chartDao.findChartFeaturesAsync(include, z, chart.id).await()?.filter {
                    it.geomWKB != null
                }?.forEach { feature ->
                    val tileGeo = tileSystem.tileGeometry(wkbReader.read(feature.geomWKB), x, y, z)
                    encoder.addFeature(feature.layer, feature.props, tileGeo)
                }
                wkbReader.read(chart.covrWKB)?.let { geo ->
                    covered = covered.union(geo)
                    include = include.difference(covered)
                }
            }
        }
        ctx.stop()
        return this
    }

    fun encode(): ByteArray {
        return encoder.encode()
    }
}