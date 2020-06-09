package io.madrona.njord.gis

import com.google.common.hash.Hashing
import com.google.common.io.Files
import io.madrona.njord.ds.FileEntry
import io.madrona.njord.ds.FileType
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
        ds.GetLayer("M_COVR")?.GetSpatialRef()?.GetName()
    }

    override val updated: String? by lazy {
        dsIdFeature?.GetFieldAsString("DSID_UADT")
    }

    override val scale: Int by lazy {
        dsIdFeature?.GetFieldAsInteger("DSPM_CSCL") ?: 0
    }

    override val z: Int? = null
    override val min_x: Int? = null
    override val max_x: Int? = null
    override val min_y: Int? = null
    override val max_y: Int? = null
    override val outline_wkt: String? by lazy {
        ds.GetLayer("M_COVR")?.GetNextFeature()?.GetGeometryRef()?.wgs84Wkt()
    }
    override val full_eval: Boolean? = null

    val layers: Set<String> by lazy {
        IntRange(0, ds.GetLayerCount()).mapNotNull {
            ds.GetLayer(it)?.GetName()
        }.toSet()
    }
}
