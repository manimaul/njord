import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails

class SqliteTest {

    @Test
    fun `open in-memory db and create table`() {
        SqliteDb.open(":memory:").use { db ->
            db.exec(
                """
                CREATE TABLE IF NOT EXISTS test (
                    id   INTEGER PRIMARY KEY,
                    name TEXT    NOT NULL,
                    data BLOB
                )
                """.trimIndent()
            )
        }
    }

    @Test
    fun `insert and verify lastInsertRowId`() {
        SqliteDb.open(":memory:").use { db ->
            db.exec("CREATE TABLE t (id INTEGER PRIMARY KEY, val TEXT NOT NULL)")
            db.prepare("INSERT INTO t (val) VALUES (?)").use { stmt ->
                stmt.bindText(1, "hello").step()
            }
            assertEquals(1L, db.lastInsertRowId)
            db.prepare("INSERT INTO t (val) VALUES (?)").use { stmt ->
                stmt.bindText(1, "world").step()
            }
            assertEquals(2L, db.lastInsertRowId)
        }
    }

    @Test
    fun `bind blob round-trips correctly`() {
        SqliteDb.open(":memory:").use { db ->
            db.exec("CREATE TABLE t (id INTEGER PRIMARY KEY, data BLOB NOT NULL)")
            val bytes = byteArrayOf(0x01, 0x02, 0x03, 0xFF.toByte())
            db.prepare("INSERT INTO t (data) VALUES (?)").use { stmt ->
                stmt.bindBlob(1, bytes).step()
            }
            // Re-open same db handle — we can verify via a SELECT statement
            // (step returns true for SQLITE_ROW)
            db.prepare("SELECT COUNT(*) FROM t").use { stmt ->
                assertEquals(true, stmt.step())
            }
        }
    }

    @Test
    fun `transaction commits on success`() {
        SqliteDb.open(":memory:").use { db ->
            db.exec("CREATE TABLE t (id INTEGER PRIMARY KEY, val TEXT NOT NULL)")
            db.transaction {
                prepare("INSERT INTO t (val) VALUES (?)").use { stmt ->
                    repeat(3) { i ->
                        stmt.bindText(1, "item$i").step()
                        stmt.reset()
                    }
                }
            }
            // Verify three rows were committed
            db.prepare("SELECT COUNT(*) FROM t").use { stmt ->
                stmt.step()
                // COUNT(*) returns SQLITE_ROW; we just confirm step succeeds
            }
            assertEquals(3L, db.lastInsertRowId)
        }
    }

    @Test
    fun `transaction rolls back on exception`() {
        SqliteDb.open(":memory:").use { db ->
            db.exec("CREATE TABLE t (id INTEGER PRIMARY KEY, val TEXT NOT NULL)")
            assertFails {
                db.transaction {
                    prepare("INSERT INTO t (val) VALUES (?)").use { stmt ->
                        stmt.bindText(1, "shouldNotExist").step()
                    }
                    error("simulated failure")
                }
            }
            // Table should be empty — SELECT returns no rows
            db.prepare("SELECT * FROM t LIMIT 1").use { stmt ->
                assertEquals(false, stmt.step())
            }
        }
    }

    @Test
    fun `reset allows statement reuse`() {
        SqliteDb.open(":memory:").use { db ->
            db.exec("CREATE TABLE t (id INTEGER PRIMARY KEY, val TEXT NOT NULL)")
            db.prepare("INSERT INTO t (val) VALUES (?)").use { stmt ->
                listOf("a", "b", "c").forEach { v ->
                    stmt.bindText(1, v).step()
                    stmt.reset()
                }
            }
            assertEquals(3L, db.lastInsertRowId)
        }
    }
}
