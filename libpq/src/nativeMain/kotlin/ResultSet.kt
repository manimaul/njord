import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.get
import kotlinx.cinterop.toKString
import libpq.PGconn
import libpq.PGresult
import libpq.PQexec
import libpq.PQgetisnull
import libpq.PQgetlength
import libpq.PQgetvalue
import libpq.PQntuples

interface ResultSet : AutoCloseable {
    fun next() : Boolean
    fun getString(index: Int) : String
    fun getBoolean(index: Int) : Boolean
    fun getByte(index: Int) : Byte
    fun getBytes(index: Int) : ByteArray
    fun getShort(index: Int) : Short
    fun getInt(index: Int) : Int
    fun getLong(index: Int) : Long
    fun getFloat(index: Int) : Float
    fun getDouble(index: Int) : Double
}

class EmptyResultSet : ResultSet {
    override fun next(): Boolean {
        return false
    }

    override fun getString(index: Int): String {
        throw IllegalStateException("empty result set")
    }

    override fun getBoolean(index: Int): Boolean {
        throw IllegalStateException("empty result set")
    }

    override fun getByte(index: Int): Byte {
        throw IllegalStateException("empty result set")
    }

    override fun getBytes(index: Int): ByteArray {
        throw IllegalStateException("empty result set")
    }

    override fun getShort(index: Int): Short {
        throw IllegalStateException("empty result set")
    }

    override fun getInt(index: Int): Int {
        throw IllegalStateException("empty result set")
    }

    override fun getLong(index: Int): Long {
        throw IllegalStateException("empty result set")
    }

    override fun getFloat(index: Int): Float {
        throw IllegalStateException("empty result set")
    }

    override fun getDouble(index: Int): Double {
        throw IllegalStateException("empty result set")
    }

    override fun close() { }
}

@ExperimentalForeignApi
class PgResultSet(
    private val cursorName: String,
    private var result: CPointer<PGresult>,
    private val conn: CPointer<PGconn>,
) : ResultSet {
    private var currentRowIndex = -1
    private var maxRowIndex = -1

    override fun next(): Boolean {
        if (currentRowIndex == maxRowIndex) {
            currentRowIndex = -1
        }
        if (currentRowIndex == -1) {
            result = PQexec(conn, "FETCH ALL IN $cursorName").check(conn)
            maxRowIndex = PQntuples(result) - 1
        }
        return if (currentRowIndex < maxRowIndex) {
            currentRowIndex += 1
           true
        } else {
            false
        }
    }

    override fun getString(index: Int): String {
        val isNull = PQgetisnull(result, tup_num = currentRowIndex, field_num = index) == 1
        return if (isNull) {
           null
        } else {
            val value = PQgetvalue(result, tup_num = currentRowIndex, field_num = index)
            value?.toKString()
        } ?: ""
    }

    override fun getBoolean(index: Int): Boolean {
        return getString(index).toBoolean()
    }

    override fun getByte(index: Int): Byte {
        return getString(index).toByte()
    }

    private fun Int.fromHex(): Int = if (this in 48..57) {
        this - 48
    } else {
        this - 87
    }
    private fun CPointer<ByteVar>.fromHex(length: Int): ByteArray {
        val array = ByteArray((length - 2) / 2)
        var index = 0
        for (i in 2 until length step 2) {
            val first = this[i].toInt().fromHex()
            val second = this[i + 1].toInt().fromHex()
            val octet = first.shl(4).or(second)
            array[index] = octet.toByte()
            index++
        }
        return array
    }

    override fun getBytes(index: Int): ByteArray {
        val isNull = PQgetisnull(result, tup_num = currentRowIndex, field_num = index) == 1
        return if (isNull) {
            null
        } else {
            val bytes = PQgetvalue(result, tup_num = currentRowIndex, field_num = index)!!
            val length = PQgetlength(result, tup_num = currentRowIndex, field_num = index)
            bytes.fromHex(length)
        } ?: ByteArray(0)
    }

    override fun getShort(index: Int): Short {
        return getString(index).toShort()
    }

    override fun getInt(index: Int): Int {
        return getString(index).toInt()
    }

    override fun getLong(index: Int): Long {
        return getString(index).toLong()
    }

    override fun getFloat(index: Int): Float {
        return getString(index).toFloat()
    }

    override fun getDouble(index: Int): Double {
        return getString(index).toDouble()
    }

    override fun close() {
        result.clear()
        pgDbLogD("closing cursor '$cursorName' and ending transaction")
        conn.exec("CLOSE $cursorName")
        conn.exec("END")
    }

}