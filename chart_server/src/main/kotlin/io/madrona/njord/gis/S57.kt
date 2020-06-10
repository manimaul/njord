package io.madrona.njord.gis

import com.google.common.hash.Hashing
import com.google.common.io.Files
import io.madrona.njord.ds.FileEntry
import io.madrona.njord.ds.FileType
import io.madrona.njord.gis.tilesystem.GeoExtent
import org.gdal.gdal.gdal
import org.gdal.ogr.Feature
import java.io.File

class S57(srcFile: File) : FileEntry {

    companion object {
        init {
            gdal.AllRegister()
            gdal.SetConfigOption("OGR_S57_OPTIONS", "LNAM_REFS:ON,UPDATES:ON,SPLIT_MULTIPOINT:ON,PRESERVE_EMPTY_NUMBERS:ON,RETURN_LINKAGES:ON")
        }
    }

    private val ds = gdal.OpenEx(srcFile.absolutePath)
    private val dsIdFeature: Feature? by lazy {
        ds.GetLayer("DSID")?.GetNextFeature()
    }

    override var id: Int? = null
    override val name: String? = srcFile.name
    override val file: String? = srcFile.absolutePath
    override val type: Int? = FileType.S57.ordinal
    override val checksum: String? by lazy {
        Files.asByteSource(srcFile).hash(Hashing.murmur3_32()).toString()
    }

    /**
     * 4.4 Units
     * Units to be used in an ENC are :
     * - Position : latitude and longitude in decimal degrees (converted into integer values, see below).
     * - Depth : metres.
     * - Height : metres.
     * - Positional accuracy: metres.
     * - Distance : nautical miles and decimal miles, or metres as defined in the IHO Object Catalogue (see
     * S-57, Appendix A ).
     */
    override val depths: String? by lazy {
        dsIdFeature?.GetFieldAsString("DSPM_DUNI")
    }

    override val datum: String? by lazy {
        ds.GetLayer("M_COVR")?.let {
            it.ResetReading()
            it.GetSpatialRef()?.GetName()
        }
    }

    override val updated: String? by lazy {
        dsIdFeature?.GetFieldAsString("DSID_UADT")
    }

    override val scale: Int by lazy {
        dsIdFeature?.GetFieldAsInteger("DSPM_CSCL") ?: 0
    }

    override val z: Int? by lazy {
        ds.GetLayer("M_COVR")?.let {
            it.ResetReading()
            it.GetNextFeature()?.GetGeometryRef()?.wgs84Centroid()?.let { point ->
                getZoom(scale, point.latitude)
            }
        }
    }

    override val min_x: Int? by lazy {
        z?.let { z ->
            extent?.let { extent ->
                vactorTileSystem.latLngToTileXy(extent.south, extent.west, z).x
            }
        }
    }

    override val max_x: Int? by lazy {
        z?.let { z ->
            extent?.let { extent ->
                vactorTileSystem.latLngToTileXy(extent.north, extent.east, z).x
            }
        }
    }

    override val min_y: Int? by lazy {
        z?.let { z ->
            extent?.let { extent ->
                // note tile coordinate system is top, left 0,0
                vactorTileSystem.latLngToTileXy(extent.north, extent.east, z).y
            }
        }
    }

    override val max_y: Int? by lazy {
        z?.let { z ->
            extent?.let { extent ->
                // note tile coordinate system is top, left 0,0
                vactorTileSystem.latLngToTileXy(extent.south, extent.west, z).y
            }
        }
    }

    override val outline_wkt: String? by lazy {
        ds.GetLayer("M_COVR")?.let {
            it.ResetReading()
            it.GetNextFeature()?.GetGeometryRef()?.wgs84Wkt()
        }
    }

    private val extent: GeoExtent? by lazy {
        ds.GetLayer("M_COVR")?.let {
            it.ResetReading()
            it.GetNextFeature()?.GetGeometryRef()?.wgs84Extent()
        }
    }

    override val full_eval: Boolean? = null

    val layers: Set<String> by lazy {
        IntRange(0, ds.GetLayerCount()).mapNotNull {
            ds.GetLayer(it)?.GetName()
        }.toSet()
    }

//    fun printCoverGeoms() {
//        ds.GetLayer("M_COVR")?.let {
//            it.ResetReading()
//            do {
//                val feature = it.GetNextFeature()
//                feature?.printFields()
////                println(feature.GetGeometryRef().ExportToJson())
//                println(feature?.GetGeometryRef()?.ExportToWkt())
//            } while (feature != null) // y is visible here!
//        }
//    }
}

//fun main() {
//    val s57 = S57(File("${System.getenv("HOME")}/Charts/ENC/US_REGION15/US5WA22M/US5WA22M.000"))
//    val miny = s57.min_y
//    val maxy = s57.max_y
//    print("miny=$miny maxy=$maxy")
//    s57.printCoverGeoms()
//}
