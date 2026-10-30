@file:OptIn(ExperimentalForeignApi::class)

import io.madrona.njord.Singletons
import io.madrona.njord.db.DbMigrator
import io.madrona.njord.resources
import io.madrona.njord.util.DistributedLock
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import platform.posix.setenv

/**
 * Shared environment for `server` tests (TEST_COVERAGE_AUDIT.md §7 items 2 and 3).
 *
 * Every test that touches [Singletons] must call [init] first (normally from `@BeforeTest`). It
 * points `resources` at the module's resource directory and, through `CHART_SERVER_OPTS`, sends
 * the database connection to a dedicated test database ([TEST_DB]) and `chartTempData` to a
 * scratch directory under `build/`, leaving every other config value (depth thresholds, region
 * exports, …) at what `config/application.json` ships - the fixtures pin those shipped values.
 *
 * [Singletons.config] is read once per process, so the override has to be in place before the
 * first access. [init] checks that it won, and fails with a pointer here if a test got there first.
 *
 * Databases are created on the dev PostGIS (`chart_server_db/`, port 6432): [ds] is the shared,
 * migrated `njord_test`; [freshDatabase] makes a throwaway one for tests that need to control the
 * schema themselves (P-DB-1).
 */
object TestEnv {
    private const val PG = "postgresql://admin:mysecretpassword@localhost:6432"
    const val ADMIN_DB = "s57server"
    const val TEST_DB = "njord_test"
    val testDbUrl = "$PG/$TEST_DB"

    /** Scratch root (`chartTempData` for the config); relative to the module dir Gradle runs tests in. */
    val tempRoot: File = File("./build/tmp/njord_test")

    private var initialized = false

    fun init() {
        if (!initialized) {
            resources = File("./src/nativeMain/resources").getAbsolutePath().toString()
            tempRoot.mkdirs()
            val overrides = """{"pgConnectionInfo":"$testDbUrl","chartTempData":"${tempRoot.getAbsolutePath()}"}"""
            setenv("CHART_SERVER_OPTS", overrides, 1)
            initialized = true
        }
        check(Singletons.config.pgConnectionInfo == testDbUrl) {
            "Singletons.config was loaded before TestEnv.init(); every server test must call TestEnv.init() in @BeforeTest"
        }
    }

    /** The shared test database, created and migrated to the current schema version once per process. */
    val ds: DataSource by lazy {
        init()
        ensureDatabase(TEST_DB)
        Singletons.ds.also { migrate(it) }
    }

    /** A fresh, empty database (PostGIS enabled, no njord schema) named `njord_test_<suffix>`. */
    fun freshDatabase(suffix: String): PgDataSource {
        init()
        val name = "${TEST_DB}_$suffix"
        admin { conn -> conn.statement("DROP DATABASE IF EXISTS $name WITH (FORCE);").execute() }
        ensureDatabase(name)
        return PgDataSource("$PG/$name")
    }

    fun dropDatabase(suffix: String) {
        admin { conn -> conn.statement("DROP DATABASE IF EXISTS ${TEST_DB}_$suffix WITH (FORCE);").execute() }
    }

    fun migrate(ds: DataSource) {
        DbMigrator(ds).run(DistributedLock(lockFile = File(tempRoot, "migration-lock")))
    }

    /** An empty scratch directory under [tempRoot]. */
    fun tempDir(name: String): File = File(tempRoot, name).also {
        it.deleteRecursively()
        it.mkdirs()
    }

    private fun ensureDatabase(name: String) {
        admin { conn ->
            val exists = conn.prepareStatement("SELECT 1 FROM pg_database WHERE datname = $1;")
                .apply { setString(1, name) }
                .executeQuery().use { it.next() }
            if (!exists) conn.statement("CREATE DATABASE $name;").execute()
        }
        PgDb.connect("$PG/$name")?.use { db ->
            PgConnection(db) {}.statement("CREATE EXTENSION IF NOT EXISTS postgis;").execute()
        } ?: error("cannot connect to $PG/$name - is chart_server_db running?")
    }

    private fun <T> admin(block: (Connection) -> T): T {
        val db = PgDb.connect("$PG/$ADMIN_DB") ?: error("cannot connect to $PG/$ADMIN_DB - start chart_server_db (podman-compose up)")
        return db.use { PgConnection(it) {}.use(block) }
    }
}

fun runTest(block: suspend CoroutineScope.() -> Unit): Unit = runBlocking { block() }
