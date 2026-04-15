package io.madrona.njord.ingest

import File
import SqliteDb
import io.madrona.njord.ChartsConfig
import io.madrona.njord.RegionExportConfig
import io.madrona.njord.Singletons
import io.madrona.njord.db.RegionDao
import io.madrona.njord.util.logger
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class RegionManifestEntry(
    val name: String,
    val description: String,
    val coverage: String,
    val archive: String,
)

class RegionExporter(
    private val config: ChartsConfig = Singletons.config,
    private val regionDir: File = Singletons.regionDir,
    private val regionDao: RegionDao = RegionDao(),
) {
    private val log = logger()
    private val json = Json { prettyPrint = true }

    suspend fun exportAll() {
        if (config.regionExports.isEmpty()) {
            log.info("no region exports configured, skipping")
            return
        }
        regionDir.mkdirs()
        config.regionExports.forEach { regionConfig ->
            runCatching { exportRegion(regionConfig) }
                .onFailure { log.error("region export failed for ${regionConfig.name}: ${it.message}") }
        }
        updateManifest()
    }

    private suspend fun exportRegion(regionConfig: RegionExportConfig) {
        log.info("exporting region ${regionConfig.name}")

        val charts = regionDao.findChartsInRegion(regionConfig.coverage) ?: run {
            log.warn("no charts found for region ${regionConfig.name}")
            return
        }
        if (charts.isEmpty()) {
            log.info("no charts intersect region ${regionConfig.name}, skipping")
            return
        }

        // Check if any chart is newer than our latest archive for this region
        if (!needsRebuild(regionConfig)) {
            log.info("region ${regionConfig.name} is up-to-date, skipping")
            return
        }

        val archiveName = "${regionConfig.name}_${currentTimestamp()}.sqlite"
        val archiveFile = File(regionDir, archiveName)
        val tmpFile = File(regionDir, "$archiveName.tmp")

        log.info("writing ${charts.size} charts to ${archiveFile.getAbsolutePath()}")
        writeArchive(tmpFile.getAbsolutePath().toString(), charts.map { it.id }, regionConfig.coverage, charts)

        // Atomic rename
        if (tmpFile.renameTo(archiveFile.getAbsolutePath().toString()) == null) {
            log.error("failed to rename temp archive to ${archiveFile.getAbsolutePath()}")
            tmpFile.deleteRecursively()
            return
        }

        log.info("region ${regionConfig.name} archive created: $archiveName")
        pruneOldArchives(regionConfig.name)
    }

    private suspend fun writeArchive(
        path: String,
        chartIds: List<Long>,
        coverageWkt: String,
        charts: List<io.madrona.njord.db.RegionChart>,
    ) {
        SqliteDb.open(path).use { db ->
            db.exec(CREATE_CHART_TABLE)
            db.exec(CREATE_FEATURE_TABLE)
            db.exec(CREATE_LNAM_REFS_TABLE)

            db.prepare(INSERT_CHART).use { stmt ->
                db.transaction {
                    charts.forEach { chart ->
                        stmt.reset()
                            .bindLong(1, chart.id)
                            .bindText(2, chart.name)
                            .bindInt(3, chart.scale)
                            .bindText(4, chart.fileName)
                            .bindText(5, chart.updated)
                            .bindText(6, chart.issued)
                            .bindInt(7, chart.zoom)
                            .bindBlob(8, chart.covrWkb)
                            .bindText(9, chart.dsidPropsJson)
                            .bindText(10, chart.chartTxtJson)
                            .step()
                    }
                }
            }

            db.prepare(INSERT_FEATURE).use { featureStmt ->
                db.prepare(INSERT_LNAM_REF).use { lnamStmt ->
                    chartIds.forEach { chartId ->
                        val features = regionDao.findFeaturesForChart(chartId) ?: return@forEach
                        if (features.isEmpty()) return@forEach

                        db.transaction {
                            features.forEach { feature ->
                                featureStmt.reset()
                                    .bindLong(1, feature.id)
                                    .bindText(2, feature.layer)
                                    .bindBlob(3, feature.geomWkb)
                                    .bindText(4, feature.propsJson)
                                    .bindLong(5, feature.chartId)
                                    .step()

                                feature.lnamRefs.forEach { lnamRef ->
                                    lnamStmt.reset()
                                        .bindLong(1, feature.id)
                                        .bindText(2, lnamRef)
                                        .step()
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun needsRebuild(regionConfig: RegionExportConfig): Boolean {
        val existing = archivesForRegion(regionConfig.name)
        if (existing.isEmpty()) return true
        // Always rebuild — the caller (RegionExportWorker) only runs when new charts were ingested
        return true
    }

    private fun pruneOldArchives(regionName: String) {
        val archives = archivesForRegion(regionName)
            .sortedByDescending { it.name }
        if (archives.size > MAX_ARCHIVES) {
            archives.drop(MAX_ARCHIVES).forEach { old ->
                log.info("pruning old archive ${old.name}")
                old.deleteRecursively()
            }
        }
    }

    private fun archivesForRegion(regionName: String): List<File> {
        return regionDir.listFiles(false)
            .filter { it.name.startsWith(regionName) && it.name.endsWith(".sqlite") }
    }

    private fun updateManifest() {
        val entries = config.regionExports.mapNotNull { regionConfig ->
            val latestArchive = archivesForRegion(regionConfig.name)
                .sortedByDescending { it.name }
                .firstOrNull() ?: return@mapNotNull null
            RegionManifestEntry(
                name = regionConfig.name,
                description = regionConfig.description,
                coverage = regionConfig.coverage,
                archive = latestArchive.name,
            )
        }
        val manifestFile = File(regionDir, MANIFEST_FILE)
        manifestFile.writeBytes(json.encodeToString(entries).encodeToByteArray())
        log.info("manifest updated with ${entries.size} region(s)")
    }

    private fun currentTimestamp(): String {
        // Use a filesystem-safe ISO-8601-style timestamp (colons replaced with hyphens)
        val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        fun Int.pad2() = toString().padStart(2, '0')
        return "${now.year}-${now.monthNumber.pad2()}-${now.dayOfMonth.pad2()}T${now.hour.pad2()}-${now.minute.pad2()}-${now.second.pad2()}"
    }

    companion object {
        const val MANIFEST_FILE = "manifest.json"
        const val MAX_ARCHIVES = 2

        private val CREATE_CHART_TABLE = """
            CREATE TABLE IF NOT EXISTS chart (
                id         INTEGER PRIMARY KEY,
                name       TEXT UNIQUE NOT NULL,
                scale      INTEGER     NOT NULL,
                file_name  TEXT        NOT NULL,
                updated    TEXT        NOT NULL,
                issued     TEXT        NOT NULL,
                zoom       INTEGER     NOT NULL,
                covr_wkb   BLOB        NOT NULL,
                dsid_props TEXT        NOT NULL,
                chart_txt  TEXT        NOT NULL
            );
        """.trimIndent()

        private val CREATE_FEATURE_TABLE = """
            CREATE TABLE IF NOT EXISTS feature (
                id       INTEGER PRIMARY KEY,
                layer    TEXT    NOT NULL,
                geom     BLOB    NOT NULL,
                props    TEXT    NOT NULL,
                chart_id INTEGER NOT NULL,
                FOREIGN KEY(chart_id) REFERENCES chart(id)
            );
        """.trimIndent()

        private val CREATE_LNAM_REFS_TABLE = """
            CREATE TABLE IF NOT EXISTS lnam_refs (
                fid      INTEGER NOT NULL,
                lnam_ref TEXT    NOT NULL,
                PRIMARY KEY (fid, lnam_ref),
                FOREIGN KEY (fid) REFERENCES feature(id)
            );
        """.trimIndent()

        private const val INSERT_CHART = """
            INSERT OR REPLACE INTO chart
                (id, name, scale, file_name, updated, issued, zoom, covr_wkb, dsid_props, chart_txt)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?);
        """

        private const val INSERT_FEATURE = """
            INSERT OR REPLACE INTO feature (id, layer, geom, props, chart_id)
            VALUES (?, ?, ?, ?, ?);
        """

        private const val INSERT_LNAM_REF = """
            INSERT OR IGNORE INTO lnam_refs (fid, lnam_ref) VALUES (?, ?);
        """
    }
}
