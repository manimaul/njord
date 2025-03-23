import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber
import kotlinx.serialization.protobuf.ProtoPacked
import kotlinx.serialization.protobuf.ProtoBuf
import kotlinx.serialization.decodeFromByteArray

@Serializable
data class Tile(
    @ProtoNumber(3) val layers: List<Layer> = emptyList()
) {

    @Serializable
    enum class GeomType {
        @ProtoNumber(0) UNKNOWN,
        @ProtoNumber(1) POINT,
        @ProtoNumber(2) LINESTRING,
        @ProtoNumber(3) POLYGON
    }

    @Serializable
    data class Value(
        @ProtoNumber(1) val stringValue: String? = null,
        @ProtoNumber(2) val floatValue: Float? = null,
        @ProtoNumber(3) val doubleValue: Double? = null,
        @ProtoNumber(4) val intValue: Long? = null,
        @ProtoNumber(5) val uintValue: Long? = null,
        @ProtoNumber(6) val sintValue: Long? = null,
        @ProtoNumber(7) val boolValue: Boolean? = null
    )

    @Serializable
    data class Feature(
        @ProtoNumber(1) val id: Long = 0L,
        @ProtoPacked @ProtoNumber(2) val tags: List<Int> = emptyList(),
        @ProtoNumber(3) val type: GeomType = GeomType.UNKNOWN,
        @ProtoPacked @ProtoNumber(4) val geometry: List<Int> = emptyList()
    )

    @Serializable
    data class Layer(
        @ProtoNumber(15) val version: Int = 1,
        @ProtoNumber(1) val name: String,
        @ProtoNumber(2) val features: List<Feature> = emptyList(),
        @ProtoNumber(3) val keys: List<String> = emptyList(),
        @ProtoNumber(4) val values: List<Value> = emptyList(),
        @ProtoNumber(5) val extent: Int = 4096
    )

    companion object{
        @OptIn(ExperimentalSerializationApi::class)
        fun decodeTile(data: ByteArray): Tile {
            return ProtoBuf.decodeFromByteArray<Tile>(data)
        }

        @OptIn(ExperimentalSerializationApi::class)
        fun encodeTile(tile: Tile): ByteArray {
            return ProtoBuf.encodeToByteArray(Tile.serializer(), tile)
        }

    }
}