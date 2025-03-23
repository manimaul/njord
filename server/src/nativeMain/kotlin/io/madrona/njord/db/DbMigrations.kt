package io.madrona.njord.db

import kotlinx.coroutines.*
import kotlinx.serialization.json.Json.Default.decodeFromString

object DbMigrations : Dao(), CoroutineScope by CoroutineScope(Dispatchers.IO) {

    const val currentVersion = 1

    fun checkVersion() {
        val v = version()
        if (v != currentVersion) {
            log.info("db migration version = $v expected $currentVersion")
            if (v == 0) {
                launch {
                    version0()
                }
            }
            if (v < 1) {
                launch {
                    version1Migration()
                }
            }
            log.info("db migrated to version = ${version()}")
        }
    }

    fun version(): Int {
        return runBlocking {
            sqlOpAsync { conn ->
                println("checking for meta table")
                val metaExists =
                    conn.statement("SELECT EXISTS (SELECT FROM information_schema.tables WHERE table_name = 'meta');")
                        .executeQuery().use { rs ->
                            rs.next() && rs.getBoolean(0)
                        }
                if (!metaExists) {
                    conn.statement(
                        "CREATE TABLE meta (key VARCHAR UNIQUE NOT NULL, value VARCHAR UNIQUE NULL);" +
                                "INSERT INTO meta VALUES ('version', '0');"
                    ).execute()
                }
                conn.statement("SELECT value from meta WHERE key = 'version';").executeQuery().use { rs ->
                    if (rs.next()) {
                        rs.getString(0).toIntOrNull()
                    } else null
                } ?: 0
            }!!
        }
    }


    private suspend fun version0() {
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
            conn.statement("UPDATE meta SET value ='1' WHERE key = 'version';").execute()
        }
    }

    private suspend fun version1Migration() {
        sqlOpAsync { conn ->
            conn.statement(
                "ALTER TABLE features ADD COLUMN IF NOT EXISTS lnam_refs VARCHAR[] NULL;" +
                        "CREATE INDEX IF NOT EXISTS features_lnam_idx ON features USING GIN (lnam_refs);"
            ).execute()
            conn.statement("SELECT id, props->'LNAM_REFS' AS lnam_refs FROM features WHERE lnam_refs IS NULL AND jsonb_array_length(props->'LNAM_REFS') > 0;")
                .executeQuery().use {
                    var num = 0
                    while (it.next()) {
                        log.info("lnam ref migration update ${++num}")
                        val id = it.getLong(1)
                        val ra: Array<Any> = decodeFromString<List<String>>(it.getString(2)).toTypedArray()
                        conn.statement("UPDATE features SET lnam_refs =$1 WHERE id =$2")
                            .setArray(1, ra)
                            .setLong(2, id)
                            .execute()
                    }
                }
            conn.statement("UPDATE meta SET value ='1' WHERE key = 'version';").execute()
        }

    }
}