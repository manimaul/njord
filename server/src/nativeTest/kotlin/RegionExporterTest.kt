import io.madrona.njord.ingest.RegionExporter
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class RegionExporterTest {

    private val dir = File("/tmp/njord/regionexportertest")

    @AfterTest
    fun cleanup() {
        dir.deleteRecursively()
    }

    @Test
    fun `xyzToTmsRow flips known values`() {
        assertEquals(0, RegionExporter.xyzToTmsRow(0, 0))
        assertEquals(15, RegionExporter.xyzToTmsRow(4, 0))
        assertEquals(0, RegionExporter.xyzToTmsRow(4, 15))
        assertEquals(7, RegionExporter.xyzToTmsRow(4, 8))
    }

    @Test
    fun `xyzToTmsRow is its own inverse`() {
        val z = 10
        val y = 137
        val tmsRow = RegionExporter.xyzToTmsRow(z, y)
        assertEquals(y, RegionExporter.xyzToTmsRow(z, tmsRow))
    }

    /**
     * The catalog's chart -> tile edges are compiled from feature envelopes, so they are a
     * superset of the tiles the encoder actually found content for. Pruning the difference is what
     * keeps the device's refcount from believing in tiles that were never installed, and it only
     * works if `chart_tiles` and `tiles` agree on the TMS row convention — which is exactly the
     * thing a coordinate flip would silently break.
     */
    @Test
    fun `pruning drops chart_tiles edges with no tile`() {
        val db = openCatalogDb("prune.mbtiles")
        db.use {
            insertChart(db, "US5WA22M")

            // Two tiles the encoder found content for, at z4 x1, y = 2 and 3.
            insertTile(db, z = 4, x = 1, y = 2)
            insertTile(db, z = 4, x = 1, y = 3)

            // Three candidate edges — the third (y = 4) never produced a tile.
            insertEdge(db, "US5WA22M", z = 4, x = 1, y = 2)
            insertEdge(db, "US5WA22M", z = 4, x = 1, y = 3)
            insertEdge(db, "US5WA22M", z = 4, x = 1, y = 4)

            db.exec(RegionExporter.PRUNE_ORPHANED_CHART_TILES)
            db.exec(RegionExporter.CREATE_CHART_TILES_INDEX)

            assertEquals(
                listOf(
                    RegionExporter.xyzToTmsRow(4, 3),
                    RegionExporter.xyzToTmsRow(4, 2),
                ).sorted(),
                remainingEdgeRows(db),
            )
        }
    }

    /**
     * Every edge survives when every candidate rendered — the prune statement must not be
     * matching on rowid or on anything other than the coordinate triple.
     */
    @Test
    fun `pruning keeps edges whose tiles exist`() {
        val db = openCatalogDb("keep.mbtiles")
        db.use {
            insertChart(db, "US5WA22M")
            (0..3).forEach { y ->
                insertTile(db, z = 4, x = 1, y = y)
                insertEdge(db, "US5WA22M", z = 4, x = 1, y = y)
            }
            db.exec(RegionExporter.PRUNE_ORPHANED_CHART_TILES)
            assertEquals(4, remainingEdgeRows(db).size)
        }
    }

    /**
     * `foreign_keys` is off by default, so a chart row written after its edges would go unnoticed
     * here but blow up on any connection that turns the pragma on — including the device's.
     */
    private fun openCatalogDb(name: String): SqliteDb {
        dir.mkdirs()
        val db = SqliteDb.open(File(dir, name).getAbsolutePath().toString())
        db.exec("PRAGMA foreign_keys = ON;")
        db.exec(RegionExporter.CREATE_TILES_TABLE)
        db.exec(RegionExporter.CREATE_TILES_INDEX)
        db.exec(RegionExporter.CREATE_CHARTS_TABLE)
        db.exec(RegionExporter.CREATE_CHART_TILES_TABLE)
        return db
    }

    private fun insertChart(db: SqliteDb, name: String) {
        db.prepare(RegionExporter.INSERT_CHART).use { stmt ->
            stmt.bindText(1, name)
                .bindInt(2, 20000)
                .bindText(3, "$name.000")
                .bindText(4, "20240101")
                .bindText(5, "20230101")
                .bindInt(6, 15)
                .bindText(7, """{"type":"Polygon","coordinates":[]}""")
                .bindText(8, """{"DSID_UPDN":"3"}""")
                .bindText(9, """{"$name.TXT":"notice"}""")
                .bindText(10, "2026-01-01T00:00:00Z")
                .step()
        }
    }

    private fun insertTile(db: SqliteDb, z: Int, x: Int, y: Int) {
        db.prepare(RegionExporter.INSERT_TILE).use { stmt ->
            stmt.bindInt(1, z)
                .bindInt(2, x)
                .bindInt(3, RegionExporter.xyzToTmsRow(z, y))
                .bindBlob(4, byteArrayOf(1, 2, 3))
                .step()
        }
    }

    private fun insertEdge(db: SqliteDb, chartName: String, z: Int, x: Int, y: Int) {
        db.prepare(RegionExporter.INSERT_CHART_TILE).use { stmt ->
            stmt.bindText(1, chartName)
                .bindInt(2, z)
                .bindInt(3, x)
                .bindInt(4, RegionExporter.xyzToTmsRow(z, y))
                .step()
        }
    }

    private fun remainingEdgeRows(db: SqliteDb): List<Int> {
        return db.prepare("SELECT tile_row FROM chart_tiles ORDER BY tile_row;").use { stmt ->
            buildList { while (stmt.step()) add(stmt.columnInt(0)) }
        }
    }
}
