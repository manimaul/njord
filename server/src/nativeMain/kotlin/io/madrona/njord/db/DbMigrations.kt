package io.madrona.njord.db

import kotlinx.coroutines.*

object DbMigrations : Dao(), CoroutineScope by CoroutineScope(Dispatchers.IO) {
    fun run() {
        runBlocking {
            initializeSchema()
            sqlOpAsync { conn ->
                conn.statement("ALTER TABLE charts DROP COLUMN IF EXISTS base_map;").execute()
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
    z_range   INT4RANGE                     NOT NULL
);
CREATE INDEX IF NOT EXISTS features_gist ON features USING GIST (geom);
CREATE INDEX IF NOT EXISTS features_idx ON features (id);
CREATE INDEX IF NOT EXISTS features_layer_idx ON features (layer);
CREATE INDEX IF NOT EXISTS features_zoom_idx ON features USING GIST (z_range);
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