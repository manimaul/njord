@file:OptIn(ExperimentalForeignApi::class)

import io.madrona.njord.geojson.Feature
import io.madrona.njord.geojson.MultiPolygon
import io.madrona.njord.geojson.Polygon
import io.madrona.njord.geojson.intValue
import io.madrona.njord.geojson.stringValue
import io.madrona.njord.model.ChartInsert
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.toKString
import kotlinx.serialization.json.JsonObject
import libgdal.*
import kotlin.collections.emptyMap
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.ref.Cleaner
import kotlin.native.ref.createCleaner


class OgrS57Dataset(val file: File) {
    private val ptr: GDALDatasetH = requireNotNull(
        GDALOpenEx(
            file.getAbsolutePath().toString(),
            GDAL_OF_VECTOR.toUInt(),
            null,
            null,
            null
        )
    )

    @OptIn(ExperimentalNativeApi::class)
    private val cleaner: Cleaner = createCleaner(ptr) {
        GDALClose(it)
    }

    val layerCount: Int = OGR_DS_GetLayerCount(ptr)

    private fun JsonObject.findCharsetFromDsidProps(): Charset {
        return when (intValue("DSSI_NALL") ?: intValue("DSSI_AALL")) {
            0 -> Charset.US_ASCII
            1 -> Charset.ISO_8859_1
            else -> Charset.UTF_8
        }
    }

    fun layerNames(): List<String> {
        return if (layerCount > 0) {
            (0 until layerCount).mapNotNull { index ->
                OGR_DS_GetLayer(ptr, index)?.let { lp ->
                    OGR_L_GetName(lp)?.toKString()
                }
            }
        } else {
            emptyList()
        }
    }

    fun getLayer(name: String): OgrLayer? {
        return OGR_DS_GetLayerByName(ptr, name)?.let {
            OgrLayer(it, this)
        }
    }

    fun featureCount(exLayers: Set<String> = emptySet()): Long {
        return layerNames().filter { !exLayers.contains(it) }.sumOf {
            getLayer(it)?.featureCount ?: 0
        }
    }

    fun chartInsertInfo(): Insertable<ChartInsert> {
        val dsid = getLayer("DSID") ?: return InsertError("dsid is missing")

        val props = dsid.features.firstOrNull()?.properties ?: return InsertError("DSID props are missing")
        val charSet = props.findCharsetFromDsidProps()

        val chartTxt = file.parentFile()?.listFiles(false)?.filter { ea ->
            ea.name.endsWith(".TXT", true)
        }?.associate {
            it.name to it.readContents(charSet)
        } ?: emptyMap()

        val mcover = getLayer("M_COVR")
        val mcovr = mcover?.features?.filter {
            it.properties.intValue("CATCOV") == 1
        } ?: return InsertError("M_COVR is missing")
        val combinedCoverage = if (mcovr.size == 1) {
            Feature(
                geometry = mcovr.first().geometry?.geoJson()
            )
        } else {
            // Multiple coverage polygons - merge them into a single multipolygon
            val polygons = mcovr.mapNotNull { feature ->
                feature.geometry?.geoJson() as? Polygon
            }
            val multiPolygon = MultiPolygon(polygons)
            Feature(geometry = multiPolygon)
        }
        val scale = props.intValue("DSPM_CSCL") ?: return InsertError("DSID DSPM_CSCL is missing")

        return InsertSuccess(
            ChartInsert(
                name = props.stringValue("DSID_DSNM") ?: "",
                scale = scale,
                fileName = file.name,
                updated = props.stringValue("DSID_UADT") ?: "",
                issued = props.stringValue("DSID_ISDT") ?: "",
                zoom = zFinder.findZoom(scale),
                covr = combinedCoverage,
                dsidProps = props,
                chartTxt = chartTxt
            )
        )
    }
}


sealed class Insertable<T>
class InsertError<T>(
    val msg: String
) : Insertable<T>()

class InsertSuccess<T>(
    val value: T
) : Insertable<T>()

val zFinder = ZFinder()

class ZFinder(
    private val oneToOneZoom: Int = 28
) {

    init {
        if (oneToOneZoom !in 1..40) {
            throw IllegalArgumentException("oneToOneZoom must be between 1 and 40")
        }
    }

    /**
     * Scale is the ratio of distances of a map. ex 17999 means 17999:1
     */
    fun findZoom(scale: Int): Int {
        var zoom = oneToOneZoom
        var zScale = scale.toDouble()
        while (zScale > 1.0) {
            zScale /= 2.0
            zoom -= 1
        }
        return zoom
    }
}

