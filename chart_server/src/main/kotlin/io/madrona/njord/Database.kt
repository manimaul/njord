package io.madrona.njord

import java.sql.Connection
import java.sql.DriverManager

val database = Database("${System.getenv("HOME")}/njord.s3db")

class Database(
        private val dbPath: String
) {

    private fun connection() : Connection {
        return DriverManager.getConnection("jdbc:sqlite:$dbPath")
    }

    init {
        connection().use { conn ->
            conn.createStatement().apply {
                executeUpdate(resourceString("schema/files_table.sql"))
            }
        }
    }

    fun query(name: String) : List<FileEntry> {
        connection().use { conn ->
            conn.createStatement().apply {
                val results = conn.prepareStatement("SELECT * FROM files WHERE name IS ?").apply {
                    setString(1, name)
                }.executeQuery()
                val retVal = ArrayList<FileEntry>(results.fetchSize)
                while (results.next()) {
                    retVal.add(FileEntry(
                            id = results.getInt("id"),
                            name = results.getString("name"),
                            file = results.getString("file")
                    ))
                }
                return retVal
            }
        }
        return emptyList()
    }

    fun insert(entry: FileEntry) : Int {
        connection().use { conn ->
            conn.prepareStatement("""INSERT INTO files(
                name,
                file,
                type,
                md5sum,
                depths,
                soundings,
                datum,
                projection,
                updated,
                scale,
                z,
                min_x,
                max_x,
                min_y,
                max_y,
                outline_wkt,
                full_eval
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);""").apply {
                setString(1, entry.name)
                setString(2, entry.file)
                setInt(3, entry.type ?: -1)
                setString(4, entry.md5sum)
                setString(5, entry.depths)
                setString(6, entry.soundings)
                setString(7, entry.datum)
                setString(8, entry.projection)
                setString(9, entry.updated)
                setInt(10, entry.scale ?: -1)
                setInt(11, entry.z ?: -1)
                setInt(12, entry.min_x ?: -1)
                setInt(13, entry.max_x ?: -1)
                setInt(14, entry.min_y ?: -1)
                setInt(15, entry.max_y ?: -1)
                setString(16, entry.outline_wkt)
                setInt(17, if (entry.full_eval == true) 1 else 0)
                return executeUpdate()
            }
        }
        return 0
    }
}

data class FileEntry(
        val id: Int? = null,
        val name: String? = null,
        val file: String? = null,
        val type: Int? = null,
        val md5sum: String? = null,
        val depths: String? = null,
        val soundings: String? = null,
        val datum: String? = null,
        val projection: String? = null,
        val updated: String? = null,
        val scale: Int? = null,
        val z: Int? = null,
        val min_x: Int? = null,
        val max_x: Int? = null,
        val min_y: Int? = null,
        val max_y: Int? = null,
        val outline_wkt: String? = null,
        val full_eval: Boolean? = null
)

fun main() {
    val numInserted = database.insert(FileEntry(name = "foo", file = "foofile", type = 10))
    print("inserted $numInserted")
    database.query("foo").forEach {
        println(it)
    }
}