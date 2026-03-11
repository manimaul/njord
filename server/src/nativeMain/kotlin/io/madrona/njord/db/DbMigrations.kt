package io.madrona.njord.db

import kotlinx.coroutines.*

object DbMigrations : Dao(), CoroutineScope by CoroutineScope(Dispatchers.IO) {
    fun run() {
        runBlocking {
            initializeSchema()
            sqlOpAsync { conn ->
                conn.statement("ALTER TABLE charts DROP COLUMN IF EXISTS base_map;").execute()

                // Migrate z_range → z_min / z_max
                conn.statement("ALTER TABLE features ADD COLUMN IF NOT EXISTS z_min INTEGER NOT NULL DEFAULT 0;").execute()
                conn.statement("ALTER TABLE features ADD COLUMN IF NOT EXISTS z_max INTEGER NOT NULL DEFAULT 22;").execute()
                // Populate from z_range if the column still exists (use EXECUTE to avoid planning errors)
                conn.statement(
                    "DO \$\$ BEGIN IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='features' AND column_name='z_range') THEN EXECUTE 'UPDATE features SET z_min = lower(z_range), z_max = upper(z_range) - 1'; END IF; END; \$\$"
                ).execute()
                conn.statement("ALTER TABLE features DROP COLUMN IF EXISTS z_range;").execute()
                conn.statement("CREATE INDEX IF NOT EXISTS features_chart_zoom_idx ON features (chart_id, z_min, z_max);").execute()
                conn.statement("DROP INDEX IF EXISTS features_chart_id_idx;").execute()
                conn.statement("DROP INDEX IF EXISTS features_zoom_idx;").execute()
            }
        }
    }

    private suspend fun initializeSchema() {
        sqlOpAsync { conn ->
            conn.statement(
                """
CREATE TABLE IF NOT EXISTS charts
(
    id         BIGSERIAL PRIMARY KEY,
    name       VARCHAR UNIQUE           NOT NULL, -- DSID_DSNM
    scale      INTEGER                  NOT NULL, -- DSPM_CSCL
    file_name  VARCHAR                  NOT NULL, -- actual file name
    updated    VARCHAR                  NOT NULL, -- DSID_UADT
    issued     VARCHAR                  NOT NULL, -- DSID_ISDT

    -- Although these could be stored in th features table these we need some of this meta data in order to
    -- derive MINZ and MAXX when SCAMIN and SCAMAX are not defined. This allows us to NOT have to rely on insertion
    -- order.
    zoom       INTEGER                  NOT NULL, -- Best display zoom level derived from scale and center latitude
    covr       GEOMETRY(GEOMETRY, 4326) NOT NULL, -- Coverage area from "M_COVR" layer feature with "CATCOV" = 1
    dsid_props JSONB                    NOT NULL, -- DSID
    chart_txt  JSONB                    NOT NULL  -- Chart text file contents e.g. { "US5WA22A.TXT": "<file contents>" }
);

-- indices
CREATE INDEX IF NOT EXISTS charts_gist ON charts USING GIST (covr);
CREATE INDEX IF NOT EXISTS charts_idx ON charts (id);
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
CREATE INDEX IF NOT EXISTS features_gist ON features USING GIST (geom);
CREATE INDEX IF NOT EXISTS features_idx ON features (id);
CREATE INDEX IF NOT EXISTS features_chart_zoom_idx ON features (chart_id, z_min, z_max);
CREATE INDEX IF NOT EXISTS features_layer_idx ON features (layer);
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
        }
    }
}