import io.ktor.util.logging.*

object Singletons {

    lateinit var genLog: Logger

    lateinit var config: ChartsConfig

//    val adminUtil by lazy { AdminUtil() }
//
//    val chartDao by lazy { ChartDao() }
//
//    val featureDao by lazy { FeatureDao() }
//
//    val tileDao by lazy { TileDao() }
//
//    val config by lazy { ChartsConfig() }
//
//    val spriteSheet by lazy { SpriteSheet() }
//
//    val ds: DataSource by lazy {
//        val hc = HikariConfig()
//        hc.jdbcUrl = "jdbc:postgresql://${config.pgHost}:${config.pgPort}/${config.pgDatabase}"
//        hc.username = config.pgUser
//        hc.password = config.pgPassword
//        hc.metricRegistry = metrics
//        hc.maximumPoolSize = config.pgConnectionPoolSize
//        hc.connectionTimeout = 120000
//        hc.leakDetectionThreshold = 300000
//        HikariDataSource(hc)
//    }
//
//    val colorLibrary by lazy { ColorLibrary() }
//
//    val wgs84SpatialRef by lazy { SpatialReference("""GEOGCS["WGS 84",DATUM["WGS_1984",SPHEROID["WGS 84",6378137,298.257223563,AUTHORITY["EPSG","7030"]],AUTHORITY["EPSG","6326"]],PRIMEM["Greenwich",0,AUTHORITY["EPSG","8901"]],UNIT["degree",0.0174532925199433,AUTHORITY["EPSG","9122"]],AXIS["Latitude",NORTH],AXIS["Longitude",EAST],AUTHORITY["EPSG","4326"]]""") }
//
//    val zFinder by lazy { ZFinder() }
//
//    val tileSystem by lazy { TileSystem() }
//
//    val geometryFactory by lazy { GeometryFactory() }
//
//    val metrics by lazy {
//        MetricRegistry().also {
//
//            if (config.consoleMetrics) {
//                ConsoleReporter.forRegistry(it)
//                    .convertRatesTo(TimeUnit.SECONDS)
//                    .convertDurationsTo(TimeUnit.MILLISECONDS)
//                    .build()
//                    .start(1, TimeUnit.MINUTES)
//            }
//
//            JmxReporter.forRegistry(it)
//                .convertRatesTo(TimeUnit.SECONDS)
//                .convertDurationsTo(TimeUnit.MILLISECONDS)
//                .build()
//                .start()
//        }
//    }
//
//    val layerFactory by lazy { LayerFactory() }
//
//    val s57ObjectLibrary by lazy { S57ObjectLibrary() }
}
