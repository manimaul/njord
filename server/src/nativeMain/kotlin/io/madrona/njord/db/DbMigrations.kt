package io.madrona.njord.db

import io.madrona.njord.Singletons
import io.madrona.njord.util.DistributedLock
import kotlinx.coroutines.*

object DbMigrations : Dao(), CoroutineScope by CoroutineScope(Dispatchers.IO) {
    private const val DB_VERSION = 1
    private const val VERSION_KEY = "version"
    private const val LNAM_BACKFILL_KEY = "lnam_refs_backfill"

    fun run(distributedLock: DistributedLock = Singletons.migrationLock) {
        runBlocking {
            while (true) {
                val version = dbVersion()
                if (version == DB_VERSION) {
                    log.info("DB schema version ready $version")
                    break
                } else {
                    if (distributedLock.tryAcquireLock()) {
                        try {
                            if (version == 0) {
                                initializeSchema()
                            }
                        } finally {
                            distributedLock.tryClearLock()
                        }
                        break
                    }
                    log.info("waiting for schema initialization by another instance")
                    delay(500)
                }
            }
            applySchemaPatches(distributedLock)
        }
    }

    /**
     * Unconditional idempotent patches that run on every startup, independent of [DB_VERSION] —
     * [initializeSchema] only ever runs once (when the version is 0), so it can't reach databases
     * that were already provisioned before these columns/tables/indices existed.
     *
     * Index changes are plain `DROP`/`CREATE`, not `CONCURRENTLY`: this holds [distributedLock]
     * and runs before the server takes traffic, and `CONCURRENTLY` can't run inside a
     * multi-statement block. Expect the first startup after a new index lands here to spend
     * minutes building it over `features`.
     */
    private suspend fun applySchemaPatches(distributedLock: DistributedLock) {
        while (true) {
            if (distributedLock.tryAcquireLock()) {
                try {
                    sqlOpAsync { conn ->
                        conn.statement(
                            """
ALTER TABLE charts ADD COLUMN IF NOT EXISTS ingested_at TIMESTAMPTZ NOT NULL DEFAULT now();

CREATE TABLE IF NOT EXISTS region_export_state
(
    region_name VARCHAR PRIMARY KEY,
    exported_at TIMESTAMPTZ NOT NULL
);

-- `id` is already covered by each table's PRIMARY KEY unique btree; these duplicates only ever
-- cost write amplification and vacuum time. CREATE INDEX IF NOT EXISTS matches on index *name*,
-- which is why they were created alongside the pkey indices in the first place.
DROP INDEX IF EXISTS charts_idx;
DROP INDEX IF EXISTS features_idx;

-- (layer) alone can't serve the layer page query's `layer = ? AND id > ? ORDER BY id LIMIT`.
DROP INDEX IF EXISTS features_layer_idx;
CREATE INDEX IF NOT EXISTS features_layer_id_idx ON features (layer, id);

-- FeatureDao.findFeature looks a feature up by props->>'LNAM'.
CREATE INDEX IF NOT EXISTS features_lnam_expr_idx ON features ((props->>'LNAM'));
                            """.trimIndent()
                        ).execute()
                    }
                    backfillLnamRefs()
                } finally {
                    distributedLock.tryClearLock()
                }
                return
            }
            log.info("waiting for schema patches by another instance")
            delay(500)
        }
    }

    /**
     * One-time backfill of `features.lnam_refs`, which no insert ever wrote until now — leaving
     * the column NULL for every existing row, the `features_lnam_idx` GIN index empty, and the
     * TOPMAR association lookups that read it silently returning nothing. GDAL always emitted the
     * data (`LNAM_REFS=ON`); it just landed in `props` only.
     *
     * Guarded by a [LNAM_BACKFILL_KEY] marker in `meta_data` rather than run unconditionally: an
     * UPDATE over every feature row rewrites row versions, and doing that on every startup would
     * bloat the table without bound. Batched per chart to bound WAL and lock duration per
     * statement — `chart_id` is the leading column of `features_chart_zoom_idx`.
     *
     * Bails without setting the marker if anything fails, so the next startup retries. Run
     * `VACUUM ANALYZE features;` out-of-band afterwards.
     */
    private suspend fun backfillLnamRefs() {
        val alreadyDone = sqlOpAsync { conn ->
            conn.prepareStatement("SELECT value FROM meta_data WHERE key = $1")
                .apply { setString(1, LNAM_BACKFILL_KEY) }
                .executeQuery()
                .use { rs -> rs.next() && rs.getString(1) == "done" }
        } ?: false
        if (alreadyDone) return

        val chartIds = sqlOpAsync { conn ->
            conn.prepareStatement("SELECT id FROM charts ORDER BY id;")
                .executeQuery()
                .use { rs -> generateSequence { if (rs.next()) rs.getLong(1) else null }.toList() }
        } ?: run {
            log.warn("could not list charts for lnam_refs backfill - retrying on next startup")
            return
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
                log.warn("lnam_refs backfill failed on chart $chartId - retrying on next startup")
                return
            }
            updated += rows
        }

        sqlOpAsync { conn ->
            conn.prepareStatement(
                """
INSERT INTO meta_data (key, value) VALUES ($1, 'done')
ON CONFLICT (key) DO UPDATE SET value = EXCLUDED.value;
                """.trimIndent()
            ).apply { setString(1, LNAM_BACKFILL_KEY) }.execute()
        }
        log.info("features.lnam_refs backfill complete - $updated rows updated")
    }

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
        } ?: 0
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
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR UNIQUE           NOT NULL, -- DSID_DSNM
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

-- indices (id is already covered by the charts_pkey unique btree)
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
    id        BIGSERIAL PRIMARY KEY,
    layer     VARCHAR                       NOT NULL,
    geom      GEOMETRY(GEOMETRY, 4326)      NOT NULL,
    props     JSONB                         NOT NULL,
    chart_id  BIGINT REFERENCES charts (id) NOT NULL,
    lnam_refs VARCHAR[]                     NULL,
    z_min     INTEGER                       NOT NULL DEFAULT 0,
    z_max     INTEGER                       NOT NULL DEFAULT 22
);
-- indices (id is already covered by the features_pkey unique btree)
CREATE INDEX IF NOT EXISTS features_gist ON features USING GIST (geom);
CREATE INDEX IF NOT EXISTS features_chart_zoom_idx ON features (chart_id, z_min, z_max);
-- (layer, id) not (layer): the layer page query is `layer = $2 AND id > $1 ORDER BY id LIMIT`
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

            conn.statement(
                """
INSERT INTO meta_data (key, value) VALUES ('$VERSION_KEY', '$DB_VERSION')
ON CONFLICT (key) DO UPDATE SET value = EXCLUDED.value;
                """.trimIndent()
            ).execute()
        }
        log.info("DB schema initialized to version $DB_VERSION")
    }
}
