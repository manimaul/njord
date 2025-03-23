@file:OptIn(ExperimentalForeignApi::class)

import io.madrona.njord.geojson.Position
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.toKString
import libgdal.CPLSetConfigOption
import libgdal.GDALAllRegister
import libgdal.GDALGetDriver
import libgdal.GDALGetDriverByName
import libgdal.GDALGetDriverCount
import libgdal.GDALGetDriverShortName
import libgdal.OGRERR_NONE
import libgdal.OGRErr
import libgdal.OGRSpatialReferenceH
import libgdal.OGR_G_AddGeometry
import libgdal.OGR_G_AddPoint_2D
import libgdal.OGR_G_CreateGeometry
import libgdal.OGR_G_IsRing
import libgdal.OSRAxisMappingStrategy
import libgdal.OSRImportFromEPSG
import libgdal.OSRNewSpatialReference
import libgdal.OSRSetAxisMappingStrategy
import libgdal.wkbGeometryCollection
import libgdal.wkbLineString
import libgdal.wkbLinearRing
import libgdal.wkbMultiLineString
import libgdal.wkbMultiPoint
import libgdal.wkbMultiPolygon
import libgdal.wkbPoint
import libgdal.wkbPolygon
import kotlin.getValue

object Gdal {

    val memDriver by lazy {
        val driver = GDALGetDriverByName("Memory")
        requireNotNull(driver)
    }

    val mvtDriver by lazy {
        val driver = GDALGetDriverByName("MVT")
        requireNotNull(driver)
    }

    val epsg4326 by lazy {
        (OSRNewSpatialReference(null) as OGRSpatialReferenceH).also {
            OSRImportFromEPSG(it, 4326)
            OSRSetAxisMappingStrategy(it, OSRAxisMappingStrategy.OAMS_TRADITIONAL_GIS_ORDER)
        }
    }

    val epsg3857 by lazy {
        (OSRNewSpatialReference(null) as OGRSpatialReferenceH).also {
            OSRImportFromEPSG(it, 3857)
        }
    }

    /**
     * https://gdal.org/drivers/vector/s57.html
     */
    private const val OGR_S57_OPTIONS_K = "OGR_S57_OPTIONS"

    /**
     * https://gdal.org/drivers/vector/s57.html#s-57-export
     */
    private const val OGR_S57_OPTIONS_V =
        "RETURN_PRIMITIVES=OFF,RETURN_LINKAGES=OFF,LNAM_REFS=ON,UPDATES=APPLY,SPLIT_MULTIPOINT=ON,RECODE_BY_DSSI=ON:ADD_SOUNDG_DEPTH=ON"

    fun initialize(debug: Boolean = false) {
        GDALAllRegister()
        CPLSetConfigOption(OGR_S57_OPTIONS_K, OGR_S57_OPTIONS_V)
        if (debug) {
            val count = GDALGetDriverCount()
            (0 until count).forEach {
                val drv = GDALGetDriver(it)
                val name = GDALGetDriverShortName(drv)?.toKString()
                println("registered driver $name")
            }
        }
    }

    fun createGeometryCollection() : OgrGeometry {
        return OgrGeometry(requireNotNull(OGR_G_CreateGeometry(wkbGeometryCollection)))
    }

    fun createMultiPolygon(vararg polygon: OgrGeometry) : OgrGeometry {
        val geo = OgrGeometry(requireNotNull(OGR_G_CreateGeometry(wkbMultiPolygon)))
        polygon.forEach {
            OGR_G_AddGeometry(geo.ptr, it.ptr).requireSuccess {
                "error adding polygon to multipolygon"
            }
        }
        return geo
    }

    fun createPolygon(shell: OgrGeometry, vararg holes: OgrGeometry) : OgrGeometry {
        return OgrGeometry(requireNotNull(OGR_G_CreateGeometry(wkbPolygon))).also { poly ->
            poly.addGeometryDirectly(shell)
            holes.forEach {
                poly.addGeometryDirectly(it)
            }
        }
    }

    fun createPolygon(vararg position: Position) : OgrGeometry {
        return OgrGeometry(requireNotNull(OGR_G_CreateGeometry(wkbPolygon))).also { poly ->
            position.forEach {
                OGR_G_AddPoint_2D(poly.ptr, it.x, it.y)
            }
        }
    }

    fun createLineString(vararg position: Position): OgrGeometry {
        return OgrGeometry(requireNotNull(OGR_G_CreateGeometry(wkbLineString))).also { ls ->
            position.forEach {
                OGR_G_AddPoint_2D(ls.ptr, it.x, it.y)
            }
        }
    }

    fun createMultiLineString(vararg geo: OgrGeometry): OgrGeometry {
        return OgrGeometry(requireNotNull(OGR_G_CreateGeometry(wkbMultiLineString))).also { mls ->
            geo.forEach { mls.addGeometryDirectly(it) }
        }
    }

    fun createPoint(position: Position? = null): OgrGeometry {
        return OgrGeometry(requireNotNull(OGR_G_CreateGeometry(wkbPoint))).also { point ->
            position?.let {
                OGR_G_AddPoint_2D(point.ptr, position.x, position.y)
            }
        }
    }

    fun createMultiPointFromCoords(vararg position: Position): OgrGeometry {
        return OgrGeometry(requireNotNull(OGR_G_CreateGeometry(wkbMultiPoint))).also { mp ->
            position.map { createPoint(it) }.forEach { mp.addGeometryDirectly(it) }
        }
    }

    fun createLinearRing(vararg position: Position): OgrGeometry {
        return OgrGeometry(requireNotNull(OGR_G_CreateGeometry(wkbLinearRing))).also { lr ->
            position.forEach {
                OGR_G_AddPoint_2D(lr.ptr, it.x, it.y)
            }
            require(OGR_G_IsRing(lr.ptr) == 1) {
                "error linear ring was not closed"
            }
        }
    }
}

fun OGRErr.requireSuccess(onFail: () -> String) {
    takeIf {
        it != OGRERR_NONE
    }?.let {
        throw RuntimeException("${onFail()} error: $it")
    }
}