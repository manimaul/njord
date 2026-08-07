package io.madrona.njord.db

import io.madrona.njord.Singletons
import io.madrona.njord.util.DistributedLock
import kotlinx.coroutines.*

object DbMigrations : Dao(), CoroutineScope by CoroutineScope(Dispatchers.IO) {
    private const val VERSION_KEY = "version"

    /**
     * A single schema revision. Applied only when the database's recorded version is below
     * [version], in ascending order, then the version is stamped into `meta_data`.
     *
     * [sql] is sent as one multi-statement `PQexec`, which PostgreSQL wraps in an implicit
     * transaction - any failure rolls the whole thing back and leaves no half-applied revision
     * behind. That also means [sql] cannot contain statements which are illegal inside a
     * transaction block (`CREATE INDEX CONCURRENTLY`, `VACUUM`); those belong in an [action].
     * It also cannot contain `$1`-`$9`, which `PgStatement` treats as bind parameters.
     *
     * [action] is arbitrary Kotlin run after [sql], for migrations that can't be expressed as one
     * transactional block - long data backfills that need batching, or statements which must run
     * outside a transaction. Returning `false` means "not finished, don't stamp the version": the
     * migration is retried on the next startup, so an [action] must be re-runnable, as must the
     * [sql] of any migration that carries one.
     *
     * When adding a migration, also fold the change into [initializeSchema] so that fresh
     * databases are created at the current version and skip the migration entirely.
     */
    private class Migration(
        val version: Int,
        val sql: String? = null,
        val action: (suspend () -> Boolean)? = null,
    )

    private val migrations = listOf(
        /**
         * Version 1 databases were provisioned before these columns/tables/indices existed, and
         * [initializeSchema] only ever runs against an empty database, so it can't reach them.
         *
         * Index changes are plain `DROP`/`CREATE`, not `CONCURRENTLY`: this runs while holding the
         * distributed lock, before the server takes traffic. Expect the startup that applies this
         * to spend minutes building indices over `features`.
         */
        Migration(
            version = 2,
            sql = """
ALTER TABLE charts ADD COLUMN IF NOT EXISTS ingested_at TIMESTAMPTZ NOT NULL DEFAULT now();

CREATE TABLE IF NOT EXISTS region_export_state
(
    region_name VARCHAR PRIMARY KEY,
    exported_at TIMESTAMPTZ NOT NULL
);

-- `id` is already covered by each table's PRIMARY KEY unique btree; these duplicates only ever
-- cost write amplification and vacuum time.
DROP INDEX IF EXISTS charts_idx;
DROP INDEX IF EXISTS features_idx;

-- (layer) alone can't serve the layer page query's `layer = ? AND id > ? ORDER BY id LIMIT`.
DROP INDEX IF EXISTS features_layer_idx;
CREATE INDEX IF NOT EXISTS features_layer_id_idx ON features (layer, id);

-- FeatureDao.findFeature looks a feature up by props->>'LNAM'.
CREATE INDEX IF NOT EXISTS features_lnam_expr_idx ON features ((props->>'LNAM'));
            """.trimIndent(),
            action = { backfillLnamRefs() },
        ),

        /**
         * `charts.name` (the S-57 `DSID_DSNM`) replaces the surrogate `charts.id` as the primary
         * key, and `features.chart_id` becomes `features.chart_name`.
         *
         * The surrogate never bought anything: `name` was already `UNIQUE NOT NULL`, and it is
         * already the identity everything outside this server uses - enc_cron diffs NOAA's catalog
         * against name-keyed `/v1/chart_editions`, and the mobile region archives key their own
         * `charts`/`chart_tiles` tables on it.
         *
         * This rewrites every row of `features` (a column add plus a full-table `UPDATE`) and
         * rebuilds `features_chart_zoom_idx` over the result, so the startup that applies it can
         * take a long while on a populated database. It also leaves roughly a table's worth of
         * dead tuples behind - run `VACUUM FULL features;` (or `pg_repack`) out-of-band afterwards.
         */
        Migration(
            version = 3,
            sql = "ALTER TABLE features ADD COLUMN IF NOT EXISTS chart_name VARCHAR NULL;",
            action = { migrateFeaturesToChartName() },
        ),
    ).sortedBy { it.version }

    private val DB_VERSION = migrations.last().version

    fun run(distributedLock: DistributedLock = Singletons.migrationLock) {
        runBlocking {
            while (true) {
                val version = dbVersion()
                if (version >= DB_VERSION) {
                    log.info("DB schema version ready $version")
                    break
                }
                if (distributedLock.tryAcquireLock()) {
                    try {
                        if (version == 0) {
                            initializeSchema()
                        } else {
                            applyMigrations(version)
                        }
                    } finally {
                        distributedLock.tryClearLock()
                    }
                    break
                }
                log.info("waiting for schema migration by another instance")
                delay(500)
            }
        }
    }

    /**
     * Applies every migration above [fromVersion] in order, stamping [VERSION_KEY] after each so a
     * failure part way through leaves the completed ones behind and resumes from there.
     *
     * A failing statement throws: the server can't serve against a schema it doesn't match, and
     * silently continuing would surface as query errors on every request instead. A [Migration.action]
     * returning `false` is not fatal - it stops the run without stamping so the next startup retries.
     */
    private suspend fun applyMigrations(fromVersion: Int) {
        migrations.filter { it.version > fromVersion }.forEach { migration ->
            log.info("applying DB migration ${migration.version}")

            // With no action to run in between, the version stamp rides along in the same implicit
            // transaction as the DDL, making the whole migration atomic.
            val stampWithSql = migration.sql != null && migration.action == null

            migration.sql?.let { sql ->
                val statement = if (stampWithSql) "$sql\n${stampVersionSql(migration.version)}" else sql
                sqlOpAsync { conn -> conn.statement(statement).execute() }
                    ?: throw IllegalStateException("DB migration ${migration.version} failed")
            }

            if (!stampWithSql) {
                migration.action?.let { action ->
                    if (!action()) {
                        log.warn("DB migration ${migration.version} incomplete - retrying on next startup")
                        return
                    }
                }
                sqlOpAsync { conn -> conn.statement(stampVersionSql(migration.version)).execute() }
                    ?: throw IllegalStateException("DB migration ${migration.version} version stamp failed")
            }

            log.info("DB schema migrated to version ${migration.version}")
        }
    }

    private fun stampVersionSql(version: Int) = """
INSERT INTO meta_data (key, value) VALUES ('$VERSION_KEY', '$version')
ON CONFLICT (key) DO UPDATE SET value = EXCLUDED.value;
    """.trimIndent()

    /**
     * One-time backfill of `features.lnam_refs`, which no insert ever wrote until now - leaving
     * the column NULL for every existing row, the `features_lnam_idx` GIN index empty, and the
     * TOPMAR association lookups that read it silently returning nothing. GDAL always emitted the
     * data (`LNAM_REFS=ON`); it just landed in `props` only.
     *
     * Batched per chart to bound WAL and lock duration per statement - `chart_id` is the leading
     * column of `features_chart_zoom_idx`. Returns false without finishing if anything fails, so
     * the migration is retried on the next startup. Run `VACUUM ANALYZE features;` out-of-band
     * afterwards.
     */
    private suspend fun backfillLnamRefs(): Boolean {
        val chartIds = sqlOpAsync { conn ->
            conn.prepareStatement("SELECT id FROM charts ORDER BY id;")
                .executeQuery()
                .use { rs -> generateSequence { if (rs.next()) rs.getLong(1) else null }.toList() }
        } ?: run {
            log.warn("could not list charts for lnam_refs backfill")
            return false
        }

        log.info("backfilling features.lnam_refs across ${chartIds.size} charts")
        var updated = 0L
        chartIds.forEach { chartId ->
            val rows = sqlOpAsync { conn ->
                conn.prepareStatement(
                    """
UPDATE features SET lnam_refs = ARRAY(SELECT jsonb_array_elements_text(props->'LNAM_REFS'))
WHERE chart_id = $1
  AND lnam_refs IS NULL
  AND jsonb_typeof(props->'LNAM_REFS') = 'array';
                    """.trimIndent()
                ).apply { setLong(1, chartId) }.execute()
            } ?: run {
                log.warn("lnam_refs backfill failed on chart $chartId")
                return false
            }
            updated += rows
        }

        log.info("features.lnam_refs backfill complete - $updated rows updated")
        return true
    }

    /**
     * Fills `features.chart_name` from the chart each row already points at, then swaps the schema
     * over to it: `charts.id` goes away, `charts.name` becomes the primary key, and
     * `features.chart_id` is replaced by a `chart_name` foreign key.
     *
     * Batched per chart like [backfillLnamRefs] - `chart_id` is the leading column of
     * `features_chart_zoom_idx`, so each statement is an index scan over one chart's rows rather
     * than a single table-wide `UPDATE` holding one enormous transaction open.
     *
     * The schema swap itself is one multi-statement `PQexec`, so PostgreSQL's implicit transaction
     * makes it all-or-nothing - there is no window where `charts` has lost its id but `features`
     * still references it. Returns false without finishing if anything fails, so the migration is
     * retried on the next startup.
     */
    private suspend fun migrateFeaturesToChartName(): Boolean {
        // The swap is not expressible as a re-runnable statement, so a crash between it and the
        // version stamp would otherwise wedge here forever: the backfill below reads `charts.id`,
        // which by then no longer exists.
        val needsMigration = sqlOpAsync { conn ->
            conn.prepareStatement(
                """
SELECT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_name = 'features' AND column_name = 'chart_id'
);
                """.trimIndent()
            ).executeQuery().use { rs -> rs.next() && rs.getBoolean(1) }
        } ?: run {
            log.warn("could not determine whether features.chart_id is still present")
            return false
        }
        if (!needsMigration) {
            log.info("features.chart_name migration already applied")
            return true
        }

        val charts = sqlOpAsync { conn ->
            conn.prepareStatement("SELECT id, name FROM charts ORDER BY id;")
                .executeQuery()
                .use { rs ->
                    generateSequence { if (rs.next()) rs.getLong(1) to rs.getString(2) else null }.toList()
                }
        } ?: run {
            log.warn("could not list charts for chart_name backfill")
            return false
        }

        log.info("backfilling features.chart_name across ${charts.size} charts")
        var updated = 0L
        charts.forEach { (chartId, chartName) ->
            val rows = sqlOpAsync { conn ->
                conn.prepareStatement(
                    "UPDATE features SET chart_name = $1 WHERE chart_id = $2 AND chart_name IS NULL;"
                ).apply {
                    setString(1, chartName)
                    setLong(2, chartId)
                }.execute()
            } ?: run {
                log.warn("chart_name backfill failed on chart $chartName")
                return false
            }
            updated += rows
        }
        log.info("features.chart_name backfill complete - $updated rows updated")

        sqlOpAsync { conn ->
            conn.statement(
                """
-- Anything another instance ingested while the batched pass was running.
UPDATE features f SET chart_name = c.name FROM charts c
WHERE c.id = f.chart_id AND f.chart_name IS NULL;

ALTER TABLE features ALTER COLUMN chart_name SET NOT NULL;

-- Has to precede dropping charts.id, which it references. Takes features_chart_id_fkey and
-- features_chart_zoom_idx down with it, both of which are rebuilt against chart_name below.
ALTER TABLE features DROP COLUMN chart_id;

ALTER TABLE charts DROP CONSTRAINT IF EXISTS charts_name_key;
ALTER TABLE charts DROP COLUMN id;
ALTER TABLE charts ADD PRIMARY KEY (name);

CREATE INDEX IF NOT EXISTS features_chart_zoom_idx ON features (chart_name, z_min, z_max);
ALTER TABLE features ADD CONSTRAINT features_chart_name_fkey
    FOREIGN KEY (chart_name) REFERENCES charts (name);
                """.trimIndent()
            ).execute()
        } ?: run {
            log.warn("features.chart_name schema swap failed")
            return false
        }

        log.info("charts.name is now the primary key; features keyed by chart_name")
        return true
    }

    /**
     * The recorded schema version, or 0 for a database that has never been provisioned. Throws
     * rather than reporting 0 when the database can't be reached, so a connection failure isn't
     * mistaken for an empty database and answered by replaying every migration.
     */
    private suspend fun dbVersion(): Int {
        return sqlOpAsync { conn ->
            val tableExists = conn.prepareStatement("SELECT to_regclass('public.meta_data') IS NOT NULL")
                .executeQuery()
                .use { rs -> rs.next() && rs.getBoolean(1) }
            if (!tableExists) return@sqlOpAsync 0
            conn.prepareStatement("SELECT value FROM meta_data WHERE key = $1")
                .apply { setString(1, VERSION_KEY) }
                .executeQuery()
                .use { rs -> if (rs.next()) rs.getString(1).toIntOrNull() else null }
                ?: 0
        } ?: throw IllegalStateException("could not read DB schema version")
    }

    private suspend fun initializeSchema() {
        sqlOpAsync { conn ->
            conn.statement(
                """
CREATE TABLE IF NOT EXISTS meta_data
(
    key   VARCHAR PRIMARY KEY,
    value VARCHAR NOT NULL
);
                """.trimIndent()
            ).execute()

            conn.statement(
                """
CREATE TABLE IF NOT EXISTS charts
(
    name        VARCHAR PRIMARY KEY, -- DSID_DSNM - the chart's identity, here and in every client
    scale       INTEGER                  NOT NULL, -- DSPM_CSCL
    file_name   VARCHAR                  NOT NULL, -- actual file name
    updated     VARCHAR                  NOT NULL, -- DSID_UADT
    issued      VARCHAR                  NOT NULL, -- DSID_ISDT

    -- Although these could be stored in th features table these we need some of this meta data in order to
    -- derive MINZ and MAXX when SCAMIN and SCAMAX are not defined. This allows us to NOT have to rely on insertion
    -- order.
    zoom        INTEGER                  NOT NULL, -- Best display zoom level derived from scale and center latitude
    covr        GEOMETRY(GEOMETRY, 4326) NOT NULL, -- Coverage area from "M_COVR" layer feature with "CATCOV" = 1
    dsid_props  JSONB                    NOT NULL, -- DSID
    chart_txt   JSONB                    NOT NULL, -- Chart text file contents e.g. { "US5WA22A.TXT": "<file contents>" }
    ingested_at TIMESTAMPTZ              NOT NULL DEFAULT now() -- when this row was written to our DB (distinct from DSID_UADT)
);

-- indices (name is already covered by the charts_pkey unique btree)
CREATE INDEX IF NOT EXISTS charts_gist ON charts USING GIST (covr);

CREATE TABLE IF NOT EXISTS region_export_state
(
    region_name VARCHAR PRIMARY KEY,
    exported_at TIMESTAMPTZ NOT NULL
);
                """.trimIndent()
            ).execute()

            conn.statement(
                """
CREATE TABLE IF NOT EXISTS features
(
    id         BIGSERIAL PRIMARY KEY,
    layer      VARCHAR                          NOT NULL,
    geom       GEOMETRY(GEOMETRY, 4326)         NOT NULL,
    props      JSONB                            NOT NULL,
    chart_name VARCHAR REFERENCES charts (name) NOT NULL,
    lnam_refs  VARCHAR[]                        NULL,
    z_min      INTEGER                          NOT NULL DEFAULT 0,
    z_max      INTEGER                          NOT NULL DEFAULT 22
);
-- indices (id is already covered by the features_pkey unique btree)
CREATE INDEX IF NOT EXISTS features_gist ON features USING GIST (geom);
CREATE INDEX IF NOT EXISTS features_chart_zoom_idx ON features (chart_name, z_min, z_max);
-- (layer, id) not (layer): the layer page query is `layer = ? AND id > ? ORDER BY id LIMIT`
CREATE INDEX IF NOT EXISTS features_layer_id_idx ON features (layer, id);
CREATE INDEX IF NOT EXISTS features_lnam_expr_idx ON features ((props->>'LNAM'));
CREATE INDEX IF NOT EXISTS features_lnam_idx ON features USING GIN (lnam_refs);
                """.trimIndent()
            ).execute()

            conn.statement(
                """
CREATE TABLE IF NOT EXISTS base_features
(
    id    BIGSERIAL PRIMARY KEY,
    geom  GEOMETRY(GEOMETRY, 4326) NOT NULL,
    props JSONB                    NOT NULL,
    name  VARCHAR                  NOT NULL,  -- shapefile file name e.g. ne_10m_land.shp
    scale INTEGER                  NOT NULL,  -- NE scale: 10_000_000 / 50_000_000 / 110_000_000
    layer VARCHAR                  NOT NULL   -- S-57 layer name e.g. LNDARE
);
CREATE INDEX IF NOT EXISTS base_features_gist ON base_features USING GIST (geom);
CREATE INDEX IF NOT EXISTS base_features_scale_idx ON base_features (scale);
                """.trimIndent()
            ).execute()

            conn.statement(stampVersionSql(DB_VERSION)).execute()
        } ?: throw IllegalStateException("DB schema initialization failed")
        log.info("DB schema initialized to version $DB_VERSION")
    }
}
