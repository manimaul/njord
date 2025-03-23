package io.madrona.njord.geojson

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.*
import kotlinx.serialization.json.Json.Default.decodeFromJsonElement

object GeometrySerializer : KSerializer<Geometry> {

    @OptIn(InternalSerializationApi::class, ExperimentalSerializationApi::class)
    override val descriptor: SerialDescriptor
        get() = buildSerialDescriptor("Geometry", StructureKind.OBJECT)

    override fun deserialize(decoder: Decoder): Geometry {
        val input = decoder as? JsonDecoder ?: throw SerializationException("This class can only be loaded from JSON")
        val element = input.decodeJsonElement()
        val geom = element.jsonObject["type"]?.jsonPrimitive?.content?.let { type ->
            when (GeometryType.valueOf(type)) {
                GeometryType.Point -> decodeFromJsonElement(Point.serializer(), element)
                GeometryType.MultiPoint -> decodeFromJsonElement(MultiPoint.serializer(), element)
                GeometryType.LineString -> decodeFromJsonElement(LineString.serializer(), element)
                GeometryType.MultiLineString -> decodeFromJsonElement(MultiLineString.serializer(), element)
                GeometryType.Polygon -> decodeFromJsonElement(Polygon.serializer(), element)
                GeometryType.MultiPolygon -> decodeFromJsonElement(MultiPolygon.serializer(), element)
                GeometryType.GeometryCollection -> decodeFromJsonElement(GeometryCollection.serializer(), element)
            }
        }
        return geom ?: throw SerializationException("This class can only be loaded from JSON")
    }

    override fun serialize(encoder: Encoder, value: Geometry) {
        when (value) {
            is Point -> Point.serializer().serialize(encoder, value)
            is MultiPoint -> MultiPoint.serializer().serialize(encoder, value)
            is LineString -> LineString.serializer().serialize(encoder, value)
            is MultiLineString -> MultiLineString.serializer().serialize(encoder, value)
            is Polygon -> Polygon.serializer().serialize(encoder, value)
            is MultiPolygon -> MultiPolygon.serializer().serialize(encoder, value)
        }
    }
}

interface DoubleListSerializer<T> : KSerializer<T> {

    fun coordinates(value: T): List<Double>
    fun create(coords: List<Double>): T

    @OptIn(InternalSerializationApi::class, ExperimentalSerializationApi::class)
    override val descriptor: SerialDescriptor
        get() = buildSerialDescriptor("DoubleList", StructureKind.LIST)

    override fun deserialize(decoder: Decoder): T {
        val input = decoder as? JsonDecoder ?: throw SerializationException("This class can only be loaded from JSON")
        val array = input.decodeJsonElement().jsonArray.map { it.jsonPrimitive.double }
        return create(array)
    }

    override fun serialize(encoder: Encoder, value: T) {
        encoder as? JsonEncoder ?: throw SerializationException("This class can only be saved as JSON")
        val array = JsonArray(coordinates(value).map { JsonPrimitive(it) })
        encoder.encodeJsonElement(array)
    }
}

object PositionSerializer : DoubleListSerializer<Position> {
    override fun coordinates(value: Position) = value.coordinates
    override fun create(coords: List<Double>) = Position(coords)
}

object BoundingBoxSerializer : DoubleListSerializer<BoundingBox> {
    override fun coordinates(value: BoundingBox) = value.coordinates
    override fun create(coords: List<Double>) = BoundingBox(coords)
}
