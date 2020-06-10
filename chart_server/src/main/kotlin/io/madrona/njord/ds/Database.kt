package io.madrona.njord.ds

import io.madrona.njord.resourceString
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
                    retVal.add(FileRecord(
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
            var i = 0
            conn.prepareStatement("""INSERT INTO files(
                name,
                file,
                type,
                checksum,
                depths,
                datum,
                updated,
                scale,
                z,
                min_x,
                max_x,
                min_y,
                max_y,
                outline_wkt,
                full_eval
                ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?);""").apply {
                setString(++i, entry.name)
                setString(++i, entry.file)
                setInt(++i, entry.type ?: -1)
                setString(++i, entry.checksum)
                setString(++i, entry.depths)
                setString(++i, entry.datum)
                setString(++i, entry.updated)
                setInt(++i, entry.scale ?: -1)
                setInt(++i, entry.z ?: -1)
                setInt(++i, entry.min_x ?: -1)
                setInt(++i, entry.max_x ?: -1)
                setInt(++i, entry.min_y ?: -1)
                setInt(++i, entry.max_y ?: -1)
                setString(++i, entry.outline_wkt)
                setInt(++i, if (entry.full_eval == true) 1 else 0)
                return executeUpdate()
            }
        }
        return 0
    }
}

//todo: (delete/ clean me up)
//fun main() {
//    val s57 = S57(File("${System.getenv("HOME")}/Charts/ENC/US_REGION15/US5WA22M/US5WA22M.000"))
//    s57.layers.forEach {
//        println("layer $it")
//    }
//    println("scale = ${s57.scale}")
//    println("updated = ${s57.updated}")
//    println("outline wkt = ${s57.outline_wkt}")
//
//    val numInserted = database.insert(s57)
//    print("inserted $numInserted")
//    database.query("US5WA22M.000").forEach {
//        println(it)
//    }
//}