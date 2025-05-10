interface Statement : AutoCloseable {
    fun executeQuery() : ResultSet
    fun execute() : Boolean
    fun executeUpdate() : Int
    fun <T> executeUpdateGeneratedKeys(handler: (Int, ResultSet) -> T) : T

    fun setArray(index: Int, value: Array<String>?)
    fun setLong(index: Int, value: Long?)
    fun setInt(index: Int, value: Int?)
    fun setString(index: Int, value: String?)
    fun setObject(index: Int, json: String?)
    fun setBytes(index: Int, value: ByteArray)
}

interface Connection : AutoCloseable {
    fun prepareStatement(sql: String) : Statement
    fun createArrayOf(type: String, items: Array<String>) : Array<String>
}

interface DataSource {
    suspend fun connection(): Connection
}

class SQLException(message: String, throwable: Throwable) : Throwable(message, throwable)