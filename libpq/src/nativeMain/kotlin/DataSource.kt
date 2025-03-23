interface Statement {

    fun executeQuery() : ResultSet
    fun execute() : Long
    fun executeReturning(): ResultSet
    fun setArray(index: Int, value: Array<Any>?) : Statement
    fun setLong(index: Int, value: Long?) :Statement
    fun setBool(index: Int, value: Boolean?) : Statement
    fun setInt(index: Int, value: Int?) : Statement
    fun setFloat(index: Int, value: Float?) : Statement
    fun setDouble(index: Int, value: Double?) : Statement
    fun setString(index: Int, value: String?) : Statement
    fun setAuto(index: Int, json: String?) : Statement
    fun setAuto(index: Int, value: ByteArray?) : Statement
    fun setBytes(index: Int, value: ByteArray?) : Statement
}

interface Connection : AutoCloseable {
    fun statement(
        sql: String
    ) : Statement
    fun prepareStatement(
        sql: String
    ) : Statement

    fun prepareStatement(
        sql: String,
        identifier: Int,
    ) : Statement
}

interface DataSource {
    suspend fun connection(): Connection
}

class SQLException(message: String, throwable: Throwable) : Throwable(message, throwable)