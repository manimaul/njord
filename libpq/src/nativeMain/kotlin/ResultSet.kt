import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.get
import kotlinx.cinterop.toKString
import libpq.PGconn
import libpq.PGresult
import libpq.PQcmdTuples
import libpq.PQexec
import libpq.PQfname
import libpq.PQfnumber
import libpq.PQgetisnull
import libpq.PQgetlength
import libpq.PQgetvalue
import libpq.PQnfields
import libpq.PQntuples

interface ResultSet : AutoCloseable {

    val numberOfFields: Int
    val keys: List<String>
    val totalRows: Long
    val cursorRows: Long
    fun next(): Boolean

    fun getArray(index: Int): Array<String?>
    fun getArray(key: String): Array<String?>
    fun getString(index: Int): String
    fun getString(key: String): String

    fun getBoolean(index: Int): Boolean
    fun getBoolean(key: String): Boolean

    fun getByte(index: Int): Byte
    fun getByte(key: String): Byte

    fun getBytes(index: Int): ByteArray
    fun getBytes(key: String): ByteArray

    fun getShort(index: Int): Short
    fun getShort(key: String): Short

    fun getInt(index: Int): Int
    fun getInt(key: String): Int

    fun getLong(index: Int): Long
    fun getLong(key: String): Long

    fun getFloat(index: Int): Float
    fun getFloat(key: String): Float
    fun getDouble(index: Int): Double
    fun getDouble(key: String): Double
}

@ExperimentalForeignApi
class PgResultSet(
    private val cursorName: String? = null,
    private var result: CPointer<PGresult>?,
    private val conn: CPointer<PGconn>,
) : ResultSet {
    private var currentRowIndex: Int = -1
    private var maxRowIndex: Int = -1

    override val numberOfFields: Int
        get() = PQnfields(result)

    override val keys: List<String> by lazy {
        val n = PQnfields(result)
        val arr = arrayOfNulls<String>(n)
        for (i in 0 until PQnfields(result)) {
            arr[i] = PQfname(result, i)?.toKString()
        }
        arr.filterNotNull()
    }

    override val totalRows: Long = cursorRows

    override val cursorRows: Long
        get() =
            if (cursorName != null) {
                result?.let { PQcmdTuples(it)?.toKString()?.toLongOrNull() } ?: 0L
            } else {
                result?.let { PQntuples(it).toLong() } ?: 0L
            }


    private fun nextWithCursor(): Boolean {
        if (currentRowIndex == maxRowIndex) {
            currentRowIndex = -1
        }
        if (currentRowIndex == -1) {
            clearResult()
            result = PQexec(conn, "FETCH FORWARD 100 FROM $cursorName").check(conn)
            maxRowIndex = PQntuples(result) - 1
        }
        return if (currentRowIndex < maxRowIndex) {
            currentRowIndex += 1
            true
        } else {
            false
        }
    }

    override fun next(): Boolean {
        return cursorName?.let {
            nextWithCursor()
        } ?: run {
            if (currentRowIndex == -1) {
                maxRowIndex = PQntuples(result) - 1
            }
            return if (currentRowIndex < maxRowIndex) {
                currentRowIndex += 1
                true
            } else {
                false
            }
        }
    }

    /**
     * [index] 1 based index
     */
    override fun getArray(index: Int): Array<String?> {
        val strArr = getString(index)
        return parsePqArray(strArr).toTypedArray()
    }

    override fun getArray(key: String): Array<String?> {
        return getArray(PQfnumber(result, key) + 1)
    }

    /**
     * [index] 1 based index
     */
    override fun getString(index: Int): String {
        val i = index - 1
        if (i < numberOfFields) {
            val isNull = PQgetisnull(result, tup_num = currentRowIndex, field_num = i) == 1
            return if (isNull) {
                null
            } else {
                val value = PQgetvalue(result, tup_num = currentRowIndex, field_num = i)
                val str = value?.toKString()
                str
            } ?: ""
        } else {
            return ""
        }
    }

    /**
     * [index] 1 based index
     */
    override fun getBoolean(index: Int): Boolean {
        return getString(index) == "t"
    }

    override fun getString(key: String): String {
        return getString(PQfnumber(result, key) + 1)
    }

    override fun getBoolean(key: String): Boolean {
        return getBoolean(PQfnumber(result, key) + 1)
    }

    override fun getByte(key: String): Byte {
        return getByte(PQfnumber(result, key) + 1)
    }

    override fun getBytes(key: String): ByteArray {
        return getBytes(PQfnumber(result, key) + 1)
    }

    override fun getShort(key: String): Short {
        return getShort(PQfnumber(result, key) + 1)
    }

    override fun getInt(key: String): Int {
        return getInt(PQfnumber(result, key) + 1)
    }

    override fun getLong(key: String): Long {
        return getLong(PQfnumber(result, key) + 1)
    }

    override fun getFloat(key: String): Float {
        return getFloat(PQfnumber(result, key) + 1)
    }

    override fun getDouble(key: String): Double {
        return getDouble(PQfnumber(result, key) + 1)
    }

    /**
     * [index] 1 based index
     */
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
        for ((index, i) in (2 until length step 2).withIndex()) {
            val first = this[i].toInt().fromHex()
            val second = this[i + 1].toInt().fromHex()
            val octet = first.shl(4).or(second)
            array[index] = octet.toByte()
        }
        return array
    }

    /**
     * [index] 1 based index
     */
    override fun getBytes(index: Int): ByteArray {
        val i = index - 1
        if (i < numberOfFields) {
            val isNull = PQgetisnull(result, tup_num = currentRowIndex, field_num = i) == 1
            return if (isNull) {
                null
            } else {
                val bytes = PQgetvalue(result, tup_num = currentRowIndex, field_num = i)!!
                val length = PQgetlength(result, tup_num = currentRowIndex, field_num = i)
                bytes.fromHex(length)
            } ?: ByteArray(0)
        } else {
            return ByteArray(0)
        }
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

    fun clearResult() {
        result?.clear()
        result = null
    }

    override fun close() {
        clearResult()
        if (cursorName != null) {
            conn.exec("CLOSE $cursorName")
            conn.exec("END")
        }
    }

}