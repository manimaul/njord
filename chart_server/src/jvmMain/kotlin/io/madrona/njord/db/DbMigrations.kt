package io.madrona.njord.db

import com.fasterxml.jackson.module.kotlin.readValue
import kotlinx.coroutines.*

object DbMigrations : Dao(), CoroutineScope by CoroutineScope(Dispatchers.IO) {

    const val currentVersion = 1

    fun checkVersion() {
        val v = version()
        if (v != currentVersion) {
            log.info("db migration version = $v expected $currentVersion")
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
                val metaExists =
                    conn.prepareStatement("SELECT EXISTS (SELECT FROM information_schema.tables WHERE table_name = 'meta');")
                        .executeQuery().let {
                            it.next() && it.getBoolean(1)
                        }
                if (!metaExists) {
                    conn.prepareStatement(
                        "CREATE TABLE meta (key VARCHAR UNIQUE NOT NULL, value VARCHAR UNIQUE NULL);" +
                                "INSERT INTO meta VALUES ('version', '0');"
                    ).execute()
                }
                conn.prepareStatement("SELECT value from meta WHERE key = 'version';").executeQuery().let {
                    if (it.next()) {
                        it.getString(1).toIntOrNull()
                    } else null
                }
            }!!
        }
    }


    private suspend fun version1Migration() {
        sqlOpAsync { conn ->
            conn.prepareStatement(
                "ALTER TABLE features ADD COLUMN IF NOT EXISTS lnam_refs VARCHAR[] NULL;" +
                        "CREATE INDEX IF NOT EXISTS features_lnam_idx ON features USING GIN (lnam_refs);"
            ).execute()
            conn.prepareStatement("SELECT id, props->'LNAM_REFS' AS lnam_refs FROM features WHERE lnam_refs IS NULL AND jsonb_array_length(props->'LNAM_REFS') > 0;")
                .executeQuery().let {
                    var num = 0
                    while (it.next()) {
                        log.info("lnam ref migration update ${++num}")
                        val id = it.getLong(1)
                        val ra = objectMapper.readValue<List<String>>(it.getString(2))
                        val refs = conn.createArrayOf("VARCHAR", ra.toTypedArray())
                        val stmt = conn.prepareStatement("UPDATE features SET lnam_refs =? WHERE id =?").apply {
                            setArray(1, refs)
                            setLong(2, id)
                        }
                        stmt.executeUpdate()
                    }
                }
            conn.prepareStatement("UPDATE meta SET value ='1' WHERE key = 'version';").execute()
        }

    }
}