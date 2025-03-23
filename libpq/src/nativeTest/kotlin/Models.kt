import kotlinx.serialization.Serializable

@Serializable
data class KvString(
    val key: String,
)

@Serializable
data class KvDouble(
    val key: Double,
)
