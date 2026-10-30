import io.madrona.njord.db.DbMigrator
import io.madrona.njord.testdata.FixtureHeader
import io.madrona.njord.testdata.fixtures
import io.madrona.njord.util.DistributedLock
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * P-DB-1 / DB-1: the schema contract. A fresh database is provisioned at the current version; a
 * version-1 database (the shape every migration comment in DbMigrations.kt describes: surrogate
 * `charts.id`, `features.chart_id`, no `ingested_at`, no `region_export_state`, `lnam_refs` never
 * written) is migrated in order with its data preserved; and the two end up with the same schema.
 *
 * `db/schema.json` is the fresh schema (columns, indexes, constraints per table) the Rust
 * migration runner must produce byte for byte, and the `meta_data.version` row is the handshake
 * that lets a database provisioned by either implementation be accepted by the other.
 */
class DbMigrationsTest {

    private val suffixes = listOf("f2_mig_empty", "f2_mig_v1")

    @BeforeTest
    fun before() = TestEnv.init()

    @AfterTest
    fun after() = suffixes.forEach { TestEnv.dropDatabase(it) }

    private fun lock(suffix: String) = DistributedLock(lockFile = File(TestEnv.tempDir("lock_$suffix"), "lock"))

    @Serializable
    data class Column(val name: String, val type: String, val nullable: Boolean, val default: String?)

    @Serializable
    data class TableSchema(val columns: List<Column>, val indexes: List<String>, val constraints: List<String>)

    private val tables = listOf("meta_data", "charts", "features", "base_features", "region_export_state")

    private suspend fun schemaOf(ds: DataSource, table: String): TableSchema = ds.connection()!!.use { conn ->
        val columns = conn.prepareStatement(
            """
            SELECT column_name, udt_name, is_nullable, column_default FROM information_schema.columns
            WHERE table_schema = 'public' AND table_name = $1 ORDER BY column_name;
            """.trimIndent()
        ).apply { setString(1, table) }.executeQuery().use { rs ->
            buildList {
                while (rs.next()) add(Column(rs.getString(1), rs.getString(2), rs.getString(3) == "YES", rs.getString(4).ifEmpty { null }))
            }
        }
        val indexes = conn.prepareStatement(
            "SELECT indexdef FROM pg_indexes WHERE schemaname = 'public' AND tablename = $1 ORDER BY indexdef;"
        ).apply { setString(1, table) }.executeQuery().use { rs ->
            buildList { while (rs.next()) add(rs.getString(1)) }
        }
        val constraints = conn.prepareStatement(
            """
            SELECT conname || ': ' || pg_get_constraintdef(oid) FROM pg_constraint
            WHERE conrelid = ('public.' || $1)::regclass ORDER BY 1;
            """.trimIndent()
        ).apply { setString(1, table) }.executeQuery().use { rs ->
            buildList { while (rs.next()) add(rs.getString(1)) }
        }
        TableSchema(columns, indexes, constraints)
    }

    private suspend fun version(ds: DataSource) = DbMigrator(ds).dbVersion()

    @Test
    fun `an empty database is provisioned at the current version and matches the schema fixture`() = runTest {
        val ds = TestEnv.freshDatabase(suffixes[0])
        assertEquals(0, version(ds), "no meta_data table means version 0")
        DbMigrator(ds).run(lock(suffixes[0]))
        val migrator = DbMigrator(ds)
        assertEquals(migrator.currentVersion, version(ds))
        assertEquals(3, migrator.currentVersion, "bump this (and re-record db/schema.json) when a migration is added")

        fixtures.table(
            "db/schema.json",
            FixtureHeader(
                pins = "the PostGIS schema DbMigrator.initializeSchema provisions: columns (udt, nullability, default), pg_indexes definitions and constraints per table, each sorted; the migration runner of the port must produce exactly this and stamp meta_data.version",
                decisions = listOf("DB-1"),
                generator = "DbMigrationsTest",
                source = "DbMigrations.kt initializeSchema, schema version ${migrator.currentVersion}",
            ),
            tables,
        ) { runBlocking { schemaOf(ds, it) } }

        // running again is a no-op
        DbMigrator(ds).run(lock(suffixes[0]))
        assertEquals(migrator.currentVersion, version(ds))
    }

    @Test
    fun `a version 1 database migrates in order with its data preserved and ends with the fresh schema`() = runTest {
        val fresh = TestEnv.freshDatabase(suffixes[0])
        DbMigrator(fresh).run(lock(suffixes[0]))

        val ds = TestEnv.freshDatabase(suffixes[1])
        ds.connection()!!.use { conn -> conn.statement(VERSION_1_SCHEMA).execute() }
        ds.connection()!!.use { conn -> conn.statement(VERSION_1_DATA).execute() }
        assertEquals(1, version(ds))

        DbMigrator(ds).run(lock(suffixes[1]))
        assertEquals(DbMigrator(ds).currentVersion, version(ds))

        tables.forEach { table ->
            assertEquals(schemaOf(fresh, table), schemaOf(ds, table), "schema of $table after migration")
        }

        ds.connection()!!.use { conn ->
            val charts = conn.prepareStatement("SELECT name, scale FROM charts ORDER BY name;").executeQuery().use { rs ->
                buildList { while (rs.next()) add(rs.getString(1) to rs.getInt(2)) }
            }
            assertEquals(listOf("A.000" to 10000, "B.000" to 20000), charts)

            val features = conn.prepareStatement(
                "SELECT chart_name, layer, props->>'LNAM', array_to_string(lnam_refs, ','), z_min, z_max FROM features ORDER BY id;"
            ).executeQuery().use { rs ->
                buildList { while (rs.next()) add(listOf(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getInt(5).toString(), rs.getInt(6).toString())) }
            }
            assertEquals(
                listOf(
                    listOf("A.000", "BOYLAT", "L1", "R1,R2", "0", "22"),
                    listOf("A.000", "TOPMAR", "L2", "", "3", "15"),
                    listOf("B.000", "LIGHTS", "L3", "", "0", "22"),
                ),
                features,
                "chart_name backfilled from chart_id; lnam_refs backfilled from props.LNAM_REFS; everything else untouched",
            )
            val ingestedAt = conn.prepareStatement("SELECT count(*) FROM charts WHERE ingested_at IS NOT NULL;").executeQuery().use { rs -> rs.next(); rs.getInt(1) }
            assertEquals(2, ingestedAt, "ingested_at defaults to now() for pre-existing rows")
        }
    }

    @Test
    fun `migration lock is released even when the schema is already current`() = runTest {
        val ds = TestEnv.freshDatabase(suffixes[0])
        val lock = lock(suffixes[0])
        DbMigrator(ds).run(lock)
        assertTrue(lock.lockFile.isEmpty(), "lock file emptied after provisioning")
        DbMigrator(ds).run(lock)
        assertTrue(lock.lockFile.isEmpty())
    }

    companion object {
        /** The schema a database provisioned before versioned migrations has (see the migration 2/3 comments). */
        val VERSION_1_SCHEMA = """
CREATE TABLE meta_data (key VARCHAR PRIMARY KEY, value VARCHAR NOT NULL);
INSERT INTO meta_data (key, value) VALUES ('version', '1');

CREATE TABLE charts
(
    id         BIGSERIAL PRIMARY KEY,
    name       VARCHAR UNIQUE           NOT NULL,
    scale      INTEGER                  NOT NULL,
    file_name  VARCHAR                  NOT NULL,
    updated    VARCHAR                  NOT NULL,
    issued     VARCHAR                  NOT NULL,
    zoom       INTEGER                  NOT NULL,
    covr       GEOMETRY(GEOMETRY, 4326) NOT NULL,
    dsid_props JSONB                    NOT NULL,
    chart_txt  JSONB                    NOT NULL
);
CREATE INDEX charts_gist ON charts USING GIST (covr);
CREATE INDEX charts_idx ON charts (id);

CREATE TABLE features
(
    id        BIGSERIAL PRIMARY KEY,
    layer     VARCHAR                       NOT NULL,
    geom      GEOMETRY(GEOMETRY, 4326)      NOT NULL,
    props     JSONB                         NOT NULL,
    chart_id  BIGINT REFERENCES charts (id) NOT NULL,
    lnam_refs VARCHAR[]                     NULL,
    z_min     INTEGER                       NOT NULL DEFAULT 0,
    z_max     INTEGER                       NOT NULL DEFAULT 22
);
CREATE INDEX features_gist ON features USING GIST (geom);
CREATE INDEX features_idx ON features (id);
CREATE INDEX features_layer_idx ON features (layer);
CREATE INDEX features_chart_zoom_idx ON features (chart_id, z_min, z_max);
CREATE INDEX features_lnam_idx ON features USING GIN (lnam_refs);

CREATE TABLE base_features
(
    id    BIGSERIAL PRIMARY KEY,
    geom  GEOMETRY(GEOMETRY, 4326) NOT NULL,
    props JSONB                    NOT NULL,
    name  VARCHAR                  NOT NULL,
    scale INTEGER                  NOT NULL,
    layer VARCHAR                  NOT NULL
);
CREATE INDEX base_features_gist ON base_features USING GIST (geom);
CREATE INDEX base_features_scale_idx ON base_features (scale);
        """.trimIndent()

        val VERSION_1_DATA = """
INSERT INTO charts (name, scale, file_name, updated, issued, zoom, covr, dsid_props, chart_txt) VALUES
 ('A.000', 10000, 'A.000', '20240101', '20240101', 14, ST_GeomFromText('POLYGON((-1 -1, -1 1, 1 1, 1 -1, -1 -1))', 4326), '{"DSID_UPDN":"1"}', '{}'),
 ('B.000', 20000, 'B.000', '20240101', '20240101', 13, ST_GeomFromText('POLYGON((10 10, 10 11, 11 11, 11 10, 10 10))', 4326), '{"DSID_UPDN":"2"}', '{}');
INSERT INTO features (layer, geom, props, chart_id, z_min, z_max) VALUES
 ('BOYLAT', ST_GeomFromText('POINT(0 0)', 4326), '{"LNAM":"L1","LNAM_REFS":["R1","R2"]}', (SELECT id FROM charts WHERE name = 'A.000'), 0, 22),
 ('TOPMAR', ST_GeomFromText('POINT(0 0)', 4326), '{"LNAM":"L2"}', (SELECT id FROM charts WHERE name = 'A.000'), 3, 15),
 ('LIGHTS', ST_GeomFromText('POINT(10.5 10.5)', 4326), '{"LNAM":"L3","LNAM_REFS":[]}', (SELECT id FROM charts WHERE name = 'B.000'), 0, 22);
        """.trimIndent()
    }
}
