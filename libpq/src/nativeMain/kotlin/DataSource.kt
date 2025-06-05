interface PreparedStatement : AutoCloseable {
    fun executeQuery() : ResultSet
    fun execute() : Long
    fun executeUpdate() : Int
    fun <T> executeUpdateGeneratedKeys(handler: (Int, ResultSet) -> T) : T

    fun setArray(index: Int, value: Array<String>?)
    fun setLong(index: Int, value: Long?)
    fun setBool(index: Int, value: Boolean?)
    fun setInt(index: Int, value: Int?)
    fun setFloat(index: Int, value: Float?)
    fun setDouble(index: Int, value: Double?)
    fun setString(index: Int, value: String?)
    fun setJsonb(index: Int, json: String?)
    fun setBytes(index: Int, value: ByteArray?)
}

interface Connection : AutoCloseable {
    fun prepareStatement(
        sql: String,
        identifier: Int? = null
    ) : PreparedStatement
    fun createArrayOf(type: String, items: Array<String>) : Array<String>
}

interface DataSource {
    suspend fun connection(): Connection
}

class SQLException(message: String, throwable: Throwable) : Throwable(message, throwable)