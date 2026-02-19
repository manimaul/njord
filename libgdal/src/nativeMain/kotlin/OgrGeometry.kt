@file:OptIn(ExperimentalForeignApi::class)

import Gdal.epsg4326
import io.madrona.njord.geojson.BoundingBox
import io.madrona.njord.geojson.Geometry
import io.madrona.njord.geojson.Position
import kotlinx.cinterop.*
import kotlinx.serialization.json.Json.Default.decodeFromString
import libgdal.*
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.ref.Cleaner
import kotlin.native.ref.createCleaner

open class OgrGeometry(
    val ptr: OGRGeometryH,
) {

    @OptIn(ExperimentalNativeApi::class)
    private val cleaner: Cleaner = createCleaner(ptr) {
        //println("free geometry")
        OGR_G_DestroyGeometry(it)
    }

    private val children: MutableMap<Long, OgrGeometry> = mutableMapOf()

    val area: Double
        get() = OGR_G_GetArea(ptr)

    val length: Double
        get() = OGR_G_Length(ptr)

    val numGeometries: Int
        get() = OGR_G_GetGeometryCount(ptr)

    val isValid: Boolean
        get() = OGR_G_IsValid(ptr) == 1

    val numCoordinates: Int
        get() = OGR_G_GetPointCount(ptr)

    fun coordinateSequence(): List<Position> {
        val count = numCoordinates
        val xCoords = DoubleArray(count)
        val yCoords = DoubleArray(count)

        xCoords.usePinned { xPinned ->
            yCoords.usePinned { yPinned ->
                // nXStride/nYStride is the byte offset between elements (sizeof(Double) = 8)
                OGR_G_GetPoints(
                    ptr,
                    xPinned.addressOf(0), 8, yPinned.addressOf(0), 8, null, 0
                )
            }
        }
        return (0 until count).map {
            Position(xCoords[it], yCoords[it])
        }
    }

    fun envelope(): BoundingBox {
        return memScoped {
            val envelope = alloc<OGREnvelope>()
            OGR_G_GetEnvelope(ptr, envelope.ptr)
            BoundingBox(west = envelope.MinX, south = envelope.MinY, east = envelope.MaxX, north = envelope.MaxY)
        }
    }

    fun contains(other: OgrGeometry?): Boolean {
        return other?.let {
            OGR_G_Contains(ptr, other.ptr) == 1
        } ?: false
    }

    fun intersection(other: OgrGeometry?): OgrGeometry? {
        return other?.let {
            OGR_G_Intersection(ptr, other.ptr)?.let { OgrGeometry(it) }
        }
    }

    fun centroid(): OgrGeometry {
        val centroid = Gdal.createPoint()
        OGR_G_Centroid(ptr, centroid.ptr).requireSuccess {
            "failed to get centroid"
        }
        return centroid
    }

    fun addGeometryDirectly(child: OgrGeometry) {
        OGR_G_AddGeometryDirectly(ptr, child.ptr).requireSuccess {
            "error adding geometry directly"
        }
        children[child.ptr.rawValue.toLong()] = child
    }

    val wkb by lazy {
        memScoped {
            val wkbSize = OGR_G_WkbSize(ptr)
            val wkbBuffer = allocArray<UByteVar>(wkbSize)
            val err = OGR_G_ExportToWkb(ptr, wkbNDR, wkbBuffer)
            if (err != OGRERR_NONE) {
                throw RuntimeException("Failed to export geometry to WKB: $err")
            }
            wkbBuffer.reinterpret<ByteVar>().readBytes(wkbSize)
        }
    }

    val wkt by lazy {
        memScoped {
            val wkt = alloc<CPointerVar<ByteVar>>()
            val err = OGR_G_ExportToWkt(ptr, wkt.ptr)
            if (err != OGRERR_NONE) {
                throw RuntimeException("Failed to export geometry to WKB: $err")
            }
            wkt.value?.toKString()
        }
    }

    fun difference(other: OgrGeometry): OgrGeometry? {
        return if (other.isValid) {
            OGR_G_Difference(ptr, other.ptr)?.let {
                OgrGeometry(it)
            }
        } else {
            null
        }
    }

    fun union(other: OgrGeometry): OgrGeometry? {
        return OGR_G_Union(ptr, other.ptr)?.let {
            OgrGeometry(it)
        }
    }

    fun isEmpty(): Boolean {
        return OGR_G_IsEmpty(ptr) == 1
    }

    fun geoJson(): Geometry? {
        return OGR_G_ExportToJson(ptr)?.let { jsonPtr: CPointer<ByteVar> ->
            val geo = decodeFromString<Geometry>(jsonPtr.toKString())
            VSIFree(jsonPtr)
            geo
        }
    }

    companion object {

        fun fromWkb4326(wkb: ByteArray): OgrGeometry? {
            return fromWkb(wkb, epsg4326)
        }

        fun fromWkb(wkb: ByteArray, sr: OGRSpatialReferenceH): OgrGeometry? {
            if (wkb.isEmpty()) {
                return null
            }
            return memScoped {
                val geoInput = alloc<OGRGeometryHVar>()
                wkb.usePinned {
                    val wkbInput = it.addressOf(0).reinterpret<UByteVar>()
                    OGR_G_CreateFromWkb(wkbInput, sr, geoInput.ptr, wkb.size)
                }
                geoInput.value?.let {
                    OgrGeometry(it)
                }
            }
        }

        fun fromWkt4326(wkt: String): OgrGeometry? {
            return fromWkt(wkt, epsg4326)
        }

        fun fromWkt(wkt: String, sr: OGRSpatialReferenceH): OgrGeometry? {
            if (wkt.isBlank()) {
                return null
            }
            return memScoped {
                val geoInput = alloc<OGRGeometryHVar>()
                val wktInput = alloc<CPointerVar<ByteVar>>()
                wktInput.value = wkt.cstr.ptr
                OGR_G_CreateFromWkt(wktInput.ptr, sr, geoInput.ptr)
                geoInput.value?.let {
                    OgrGeometry(it)
                }
            }
        }
    }
}