@file:OptIn(NativeRuntimeApi::class, ExperimentalForeignApi::class)

import Gdal.epsg4326
import io.madrona.njord.db.ChartDao
import io.madrona.njord.db.FeatureDao
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import kotlin.native.runtime.GC
import kotlin.native.runtime.NativeRuntimeApi
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals


class ChartQueryTest {

    val testSql = """
    drop table if exists testing;
    create table testing 
    (
        id      BIGSERIAL PRIMARY KEY, 
        name    VARCHAR   UNIQUE NOT NULL,
        json_b  JSONB     NULL,
        array_t VARCHAR[] NULL,
        data_b  BYTEA     NULL,
        truth   BOOLEAN   DEFAULT FALSE
    );
"""
    lateinit var ds: PgDataSource
    lateinit var chartDao: ChartDao
    lateinit var featureDao: FeatureDao
    val testData = File("./build/tmp/test_data")

    @BeforeTest
    fun beforeEach() {
        ds = PgDataSource("postgresql://admin:mysecretpassword@localhost:5432/s57server", 1)
        assertEquals(1, ds.readyCount)
        runBlocking {
            ds.connection().use { conn ->
                conn.statement(testSql).execute()
            }
        }
        featureDao = FeatureDao(ds)
        chartDao = ChartDao(ds, featureDao)
        testData.deleteRecursively()
        testData.mkdirs()
    }

    @AfterTest
    fun afterEach() {
        ds.close()
        GC.collect()
    }

    @Test
    fun testMvtPoint() {
        Gdal.initialize()
        val ds = MvtDataset()
        val geom = OgrGeometry.fromWkt("POINT(-122 48)", epsg4326) ?: error("failed to create geometry")
        val layer = ds.getOrCreateLayer("my_points_layer")
        layer.addFeature(geom)
        ds.translateMvt(0, 3).listMvtTiles()
        println("MVT tiles created")
    }


    @Test
    fun testMvtDataset() {
        Gdal.initialize()
        val ds = MvtDataset()
        ds.addFeature(
            "my_points_layer",
            emptyMap(),
            OgrGeometry.fromWkt("POINT(-122 48)", epsg4326) ?: error("")
        )

        println("layer count = ${ds.layerCount}")
        println("layersInternal = ${ds.layerNames}")
        ds.translateMvt(0, 3).listMvtTiles()
    }

    @Test
    fun testMvt() = runBlockingTest {
        Gdal.initialize()
        val x = 2621
        val y = 5743
        val z = 14
        var poly = TileSystem().createTileClipPolygon(x, y, z)
        val mvtDataset = MvtDataset()

        run loop@ {
            chartDao.findInfoAsync(poly.wkb)?.forEach { info ->
                val chartCoverage = OgrGeometry.fromWkb(info.covrWKB, epsg4326) ?: error("error parsing chart coverage")
                chartDao.findChartFeaturesAsync4326(poly.wkb, info.id, z)
                    ?.forEach { feature ->
                        feature.geomWKB?.let { OgrGeometry.fromWkb(it, epsg4326) }?.let { geo ->
                            mvtDataset.addFeature(feature.layer, feature.props, geo)
                        }
                    }
                poly.difference(chartCoverage)?.takeIf { !it.isEmpty() }?.let {
                    poly = it
                } ?: run {
                    print("required chart coverage empty - break")
                    return@loop
                }
            }
        }
        println("layersInternal = ${mvtDataset.layerNames}")
        mvtDataset.layerNames.forEach {
            val layer = mvtDataset.getLayer(it)
            println("layer name = ${layer?.name} features = ${layer?.featureCount}")
        }
        val mvt = mvtDataset.translateMvt(14, 14)
            .listMvtTiles()
            .getMvt(z, x, y)
        println("mvt size = ${mvt.size}")
    }

    @Test
    fun testMvtDisk() = runBlockingTest {
        Gdal.initialize()
        val x = 2621
        val y = 5743
        val z = 14
        var poly = TileSystem().createTileClipPolygon(x, y, z)
        val mvtDataset = MvtDiskDataset(
            minZoom = z,
            maxZoom = z,
            outPath = "${testData.getAbsolutePath()}/mvt"
        )

        run loop@ {
            chartDao.findInfoAsync(poly.wkb)?.forEach { info ->
                val chartCoverage = OgrGeometry.fromWkb(info.covrWKB, epsg4326) ?: error("error parsing chart coverage")
                chartDao.findChartFeaturesAsync4326(poly.wkb, info.id, z)
                    ?.forEach { feature ->
                        feature.geomWKB?.let { OgrGeometry.fromWkb(it, epsg4326) }?.let { geo ->
                            mvtDataset.addFeature(feature.layer, feature.props, geo)
                        }
                    }
                poly.difference(chartCoverage)?.takeIf { !it.isEmpty() }?.let {
                    poly = it
                } ?: run {
                    print("required chart coverage empty - break")
                    return@loop
                }
            }
        }
        println("layersInternal = ${mvtDataset.layerNames}")
        mvtDataset.layerNames.forEach {
            val layer = mvtDataset.getLayer(it)
            println("layer name = ${layer?.name} features = ${layer?.featureCount}")
        }
        //write to disk
        mvtDataset.close()
    }
}

fun runBlockingTest(block: suspend CoroutineScope.() -> Unit)  = runBlocking {
    block()
}
