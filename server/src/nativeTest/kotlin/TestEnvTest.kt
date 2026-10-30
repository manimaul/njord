import io.madrona.njord.Singletons
import io.madrona.njord.db.DbMigrator
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The shared test environment itself: config override in place, test database provisioned and migrated. */
class TestEnvTest {

    @BeforeTest
    fun before() = TestEnv.init()

    @Test
    fun `config points at the test database and a scratch chartTempData`() {
        assertEquals(TestEnv.testDbUrl, Singletons.config.pgConnectionInfo)
        assertTrue(Singletons.config.chartTempData.endsWith("build/tmp/njord_test"), Singletons.config.chartTempData)
        // everything else is what application.json ships
        assertEquals(3.0f, Singletons.config.shallowDepth)
        assertEquals(9000, Singletons.config.port)
    }

    @Test
    fun `the test database is migrated to the current schema version`() = runTest {
        val migrator = DbMigrator(TestEnv.ds)
        assertEquals(migrator.currentVersion, migrator.dbVersion())
    }

    @Test
    fun `a fresh database starts at version 0 and migrates to current`() = runTest {
        val ds = TestEnv.freshDatabase("envtest")
        try {
            assertEquals(0, DbMigrator(ds).dbVersion())
            TestEnv.migrate(ds)
            assertEquals(DbMigrator(ds).currentVersion, DbMigrator(ds).dbVersion())
        } finally {
            TestEnv.dropDatabase("envtest")
        }
    }
}
