import io.madrona.njord.Singletons
import io.madrona.njord.db.BaseFeatureDao
import io.madrona.njord.db.ChartDao
import io.madrona.njord.db.FeatureDao
import io.madrona.njord.db.TileCache
import io.madrona.njord.db.TileDao
import io.madrona.njord.ingest.ChartIngest
import io.madrona.njord.ingest.IngestStatus
import io.madrona.njord.ingest.NaturalEarthIngest
import io.madrona.njord.model.EncUpload
import io.madrona.njord.model.ws.WsMsg
import io.madrona.njord.testdata.fixtures
import io.madrona.njord.util.DistributedLock
import kotlinx.serialization.json.Json

/**
 * Runs the real ingest pipeline against the test database (TestEnv.ds), the way the ingest
 * worker would: a zip is dropped in a scratch work directory, the distributed lock is taken, and
 * [ChartIngest.ingest] (or [NaturalEarthIngest.ingest]) does the rest - unzip, GDAL, PostGIS
 * inserts, tile cache invalidation, status file, lock release.
 *
 * Nothing here is a fake: the DAOs are the production classes pointed at TestEnv.ds, so these
 * helpers are the IN-1 happy path and the seed for every DB-backed fixture (P-TE-5, P-IN-5,
 * P-DB-3/4, P-RE-3).
 */
class TestIngest(name: String) {
    val workDir: File = TestEnv.tempDir("ingest_$name")
    val lockFile = File(workDir, "lock")
    val statusFile = File(workDir, "status.json")
    val lock = DistributedLock(lockFile = lockFile)
    val ingestStatus = IngestStatus(statusFile = statusFile, distributedLock = lock)
    val featureDao = FeatureDao(TestEnv.ds)
    val chartDao = ChartDao(TestEnv.ds, featureDao)
    val baseFeatureDao = BaseFeatureDao(TestEnv.ds)
    val tileCache = TileCache(File(workDir, "tiles"))
    val tileDao = TileDao(tileCache = tileCache)

    /** The zip the worker would have claimed into `ingest/<uuid>/`; ChartIngest deletes the directory when done. */
    val chartDir: File = File(workDir, "claim").also { it.mkdirs() }

    /** Zips [entries] (`entry name` -> source file) into [chartDir] and returns the [EncUpload] naming it. */
    fun zip(zipName: String, entries: Map<String, File>, directories: List<String> = emptyList()): EncUpload {
        ZipWriter.create(File(chartDir, zipName)).use { w ->
            directories.forEach { w.addDirectory(it) }
            entries.forEach { (entryName, src) -> w.addFile(entryName, src) }
        }
        return EncUpload(zipFile = zipName)
    }

    /** The whole fixture cell directory under `ENC_ROOT/<cell>/`, as NOAA and enc_cron ship it. */
    fun zipCellDir(dirRelativeToTestdata: String, zipName: String = "upload.zip"): EncUpload {
        val dir = fixtures.file(dirRelativeToTestdata)
        val cell = dir.name
        return zip(zipName, dir.listFiles(false).filter { it.isFile() }.associateBy { "ENC_ROOT/$cell/${it.name}" })
    }

    fun chartIngest(): ChartIngest = ChartIngest(
        ingestStatus = ingestStatus,
        distributedLock = lock,
        config = Singletons.config,
        chartDao = chartDao,
        featureDao = featureDao,
        tileDao = tileDao,
        chartDir = chartDir,
    )

    fun naturalEarthIngest(): NaturalEarthIngest = NaturalEarthIngest(
        ingestStatus = ingestStatus,
        distributedLock = lock,
        baseFeatureDao = baseFeatureDao,
        tileDao = tileDao,
        chartDir = chartDir,
    )

    /** Takes the lock the way ChartIngestWorker does before handing the upload to the ingest. */
    fun acquire(): Boolean = lock.tryAcquireLock()

    /** Acquires the lock and runs the chart ingest to completion. */
    suspend fun ingestCharts(upload: EncUpload) {
        check(acquire()) { "could not acquire the test lock ${lockFile.path}" }
        chartIngest().ingest(upload)
    }

    suspend fun ingestNaturalEarth(upload: EncUpload) {
        check(acquire()) { "could not acquire the test lock ${lockFile.path}" }
        naturalEarthIngest().ingest(upload)
    }

    /** The last status the ingest wrote, i.e. what `/v1/ws/enc_process` would be showing. */
    fun status(): WsMsg? = statusFile.takeIf { it.isFile() }?.readContents()?.takeIf { it.isNotBlank() }
        ?.let { Json.decodeFromString(WsMsg.serializer(), it) }
}

/**
 * The three fixture cells and the Natural Earth coastline extract, ingested into the test database
 * once per test process and re-ingested if a destructive test removed them.
 */
object TestCharts {
    const val WA = "US5WA22M.000"
    const val OUTER = "US4ZZ01M.000"
    const val INNER = "US5ZZ02M.000"
    val all = listOf(WA, OUTER, INNER)

    const val NE_COASTLINE = "ne_110m_coastline.shp"
    const val NE_SCALE = 110_000_000

    fun ensureSeeded() {
        TestEnv.init()
        Gdal.initialize()
        runTest {
            val chartDao = ChartDao(TestEnv.ds, FeatureDao(TestEnv.ds))
            val present = all.filter { (chartDao.findAsync(it)?.featureCount ?: 0) > 0 }
            if (present.size != all.size) {
                all.forEach { chartDao.deleteByNameAsync(it) }
                val ingest = TestIngest("seed")
                ingest.ingestCharts(ingest.zipCellDir("fixtures/US5WA22M", "US5WA22M.zip"))
                val synthetic = TestIngest("seed_synthetic")
                synthetic.ingestCharts(synthetic.zipCellDir("fixtures/synthetic", "synthetic.zip"))
                all.forEach { name ->
                    val chart = chartDao.findAsync(name) ?: error("seeding did not insert $name")
                    check(chart.featureCount > 0) { "seeding inserted $name without features" }
                }
            }
            if (baseFeatureCount() == 0L) {
                val ne = TestIngest("seed_ne")
                val dir = File("../libgdal/src/nativeTest/resources/ne_110_extract")
                ne.ingestNaturalEarth(ne.zip("ne.zip", dir.listFiles(false).filter { it.isFile() }.associateBy { it.name }))
                check(baseFeatureCount() > 0L) { "seeding inserted no Natural Earth features" }
            }
        }
    }

    suspend fun baseFeatureCount(): Long = TestEnv.ds.connection()!!.use { conn ->
        conn.prepareStatement("SELECT count(*) FROM base_features WHERE scale = $1 AND name = $2;").apply {
            setInt(1, NE_SCALE)
            setString(2, NE_COASTLINE)
        }.executeQuery().use { if (it.next()) it.getLong(1) else 0L }
    }
}
