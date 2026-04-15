@file:OptIn(ExperimentalForeignApi::class)

import cnames.structs.sqlite3
import cnames.structs.sqlite3_stmt
import kotlinx.cinterop.*
import libsqlite.*

/**
 * Thin Kotlin/Native wrapper around the SQLite C API.
 *
 * Designed for write-once region archive export:
 *   SqliteDb.open(path).use { db ->
 *       db.exec("CREATE TABLE IF NOT EXISTS ...")
 *       db.transaction {
 *           db.prepare("INSERT INTO chart VALUES (?,?,?)").use { stmt ->
 *               stmt.bindLong(1, id).bindText(2, name).bindInt(3, scale).step()
 *               stmt.reset()
 *               // repeat ...
 *           }
 *       }
 *   }
 */
class SqliteDb private constructor(
    private val db: CPointer<sqlite3>,
) : AutoCloseable {

    fun exec(sql: String) {
        val rc = sqlite3_exec(db, sql, null, null, null)
        check(rc == SQLITE_OK) { "SQLite exec failed ($rc): ${errmsg()}" }
    }

    fun prepare(sql: String): SqliteStatement {
        val stmt = memScoped {
            val stmtPtr = alloc<CPointerVar<sqlite3_stmt>>()
            val rc = sqlite3_prepare_v2(db, sql, -1, stmtPtr.ptr, null)
            check(rc == SQLITE_OK) { "SQLite prepare failed ($rc): ${errmsg()}" }
            stmtPtr.value ?: error("sqlite3_prepare_v2 returned null stmt")
        }
        return SqliteStatement(stmt, db)
    }

    val lastInsertRowId: Long
        get() = sqlite3_last_insert_rowid(db)

    fun transaction(block: SqliteDb.() -> Unit) {
        exec("BEGIN")
        try {
            block()
            exec("COMMIT")
        } catch (e: Throwable) {
            try { exec("ROLLBACK") } catch (_: Throwable) {}
            throw e
        }
    }

    private fun errmsg(): String = sqlite3_errmsg(db)?.toKString() ?: "unknown error"

    override fun close() {
        sqlite3_close(db)
    }

    companion object {
        fun open(path: String): SqliteDb {
            val db = memScoped {
                val dbPtr = alloc<CPointerVar<sqlite3>>()
                val rc = sqlite3_open(path, dbPtr.ptr)
                val handle = dbPtr.value
                if (rc != SQLITE_OK) {
                    val msg = handle?.let { sqlite3_errmsg(it)?.toKString() } ?: "unknown error"
                    handle?.let { sqlite3_close(it) }
                    error("Cannot open SQLite database at '$path': $msg")
                }
                handle ?: error("sqlite3_open returned null handle")
            }
            return SqliteDb(db)
        }
    }
}

class SqliteStatement(
    private val stmt: CPointer<sqlite3_stmt>,
    private val db: CPointer<sqlite3>,
) : AutoCloseable {

    // SQLITE_TRANSIENT = ((sqlite3_destructor_type)-1)
    // Tells SQLite to copy the data immediately so the caller's buffer can be freed.
    private val sqliteTransient = (-1L).toCPointer<CFunction<(COpaquePointer?) -> Unit>>()

    fun bindText(index: Int, value: String?): SqliteStatement {
        val rc = if (value == null) {
            sqlite3_bind_null(stmt, index)
        } else {
            sqlite3_bind_text(stmt, index, value, -1, sqliteTransient)
        }
        check(rc == SQLITE_OK) { "bindText[$index] failed ($rc): ${errmsg()}" }
        return this
    }

    fun bindBlob(index: Int, value: ByteArray?): SqliteStatement {
        val rc = if (value == null || value.isEmpty()) {
            sqlite3_bind_null(stmt, index)
        } else {
            value.usePinned { pinned ->
                sqlite3_bind_blob(stmt, index, pinned.addressOf(0), value.size, sqliteTransient)
            }
        }
        check(rc == SQLITE_OK) { "bindBlob[$index] failed ($rc): ${errmsg()}" }
        return this
    }

    fun bindInt(index: Int, value: Int?): SqliteStatement {
        val rc = if (value == null) {
            sqlite3_bind_null(stmt, index)
        } else {
            sqlite3_bind_int(stmt, index, value)
        }
        check(rc == SQLITE_OK) { "bindInt[$index] failed ($rc): ${errmsg()}" }
        return this
    }

    fun bindLong(index: Int, value: Long?): SqliteStatement {
        val rc = if (value == null) {
            sqlite3_bind_null(stmt, index)
        } else {
            sqlite3_bind_int64(stmt, index, value)
        }
        check(rc == SQLITE_OK) { "bindLong[$index] failed ($rc): ${errmsg()}" }
        return this
    }

    fun bindDouble(index: Int, value: Double?): SqliteStatement {
        val rc = if (value == null) {
            sqlite3_bind_null(stmt, index)
        } else {
            sqlite3_bind_double(stmt, index, value)
        }
        check(rc == SQLITE_OK) { "bindDouble[$index] failed ($rc): ${errmsg()}" }
        return this
    }

    /**
     * Executes the statement one step. Returns true if a row is available (SELECT),
     * false when done (INSERT/UPDATE/DELETE/DDL). Throws on error.
     */
    fun step(): Boolean {
        return when (val rc = sqlite3_step(stmt)) {
            SQLITE_ROW -> true
            SQLITE_DONE -> false
            else -> error("SQLite step failed ($rc): ${errmsg()}")
        }
    }

    /**
     * Resets the statement so it can be re-executed with new bindings.
     * Clears all bound values.
     */
    fun reset(): SqliteStatement {
        sqlite3_reset(stmt)
        sqlite3_clear_bindings(stmt)
        return this
    }

    private fun errmsg(): String = sqlite3_errmsg(db)?.toKString() ?: "unknown error"

    override fun close() {
        sqlite3_finalize(stmt)
    }
}
