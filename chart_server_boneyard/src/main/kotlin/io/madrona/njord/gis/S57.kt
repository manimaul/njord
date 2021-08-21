package io.madrona.njord.gis

import com.google.common.hash.Hashing
import com.google.common.io.Files
import io.madrona.njord.ds.FileEntry
import io.madrona.njord.ds.FileType
import io.madrona.njord.gis.tilesystem.GeoExtent
import org.gdal.gdal.gdal
import java.io.File

class S57(srcFile: File) : FileEntry {

    companion object {
        init {
            gdal.AllRegister()
            gdal.SetConfigOption("OGR_S57_OPTIONS", "LNAM_REFS:ON,UPDATES:ON,SPLIT_MULTIPOINT:ON,PRESERVE_EMPTY_NUMBERS:ON,RETURN_LINKAGES:ON")
        }
    }

    private val ds = gdal.OpenEx(srcFile.absolutePath)
    private val dsidLayer = S57LayerDSID.fromDs(ds)

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
    override val depths: String? = dsidLayer.depths

    override val datum: String? by lazy {
        ds.GetLayer("M_COVR")?.let {
            it.ResetReading()
            it.GetSpatialRef()?.ExportToProj4()
        }
    }

    override val updated: String? = dsidLayer.updated

    override val issue_date: String? = dsidLayer.issueDate

    override val scale: Int = dsidLayer.scale?.toInt() ?: 0

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

    val outline_json: String? by lazy {
        ds.GetLayer("M_COVR")?.let {
            it.ResetReading()
            it.GetNextFeature()?.GetGeometryRef()?.geoJson()
        }
    }

    val extent: GeoExtent? by lazy {
        ds.GetLayer("M_COVR")?.let {
            it.ResetReading()
            it.GetNextFeature()?.GetGeometryRef()?.wgs84Extent()
        }
    }

    override val full_eval: Boolean? = null

    val layers: List<S57Layer> by lazy {
        IntRange(0, ds.GetLayerCount()).mapNotNull {
            ds.GetLayer(it)?.let { S57Layer(it) }
        }.toList()
    }

    fun layer(name: String) : S57Layer? {
        return ds.GetLayer(name)?.let {
            S57Layer(it)
        }
    }
}

/*
tippecanoe -zg -o chart.mbtiles --coalesce-densest-as-needed --extend-zooms-if-still-dropping \
ACHARE.json BOYSPP.json C_ASSO.json DEPARE.json FERYRT.json LNDMRK.json M_NPUB.json M_SDAT.json PILPNT.json RIVERS.json SLCONS.json WATTUR.json \
ADMARE.json BUAARE.json CBLARE.json DEPCNT.json LIGHTS.json LNDRGN.json M_NSYS.json M_VDAT.json PRCARE.json SBDARE.json SOUNDG.json WEDKLP.json \
BCNSPP.json BUISGL.json COALNE.json DMPGRD.json LNDARE.json MAGVAR.json MORFAC.json OBSTRN.json RDOCAL.json SEAARE.json TWRTPT.json WRECKS.json \
BOYLAT.json C_AGGR.json CTNARE.json DSID.json LNDELV.json M_COVR.json M_QUAL.json OFSPLF.json RESARE.json SILTNK.json UWTROC.json
 */

fun main() {
    val outDir = File("chart_server/src/main/resources/geojson")
    outDir.mkdirs()
    val s57 = S57(File("${System.getenv("HOME")}/charts/ENC_ROOT/US5WA44M/US5WA44M.000"))
    s57.layers.forEach {
        File("$outDir/${it.name}.json").writeText(it.geoJsonStr)
    }

//    println(s57.layer("DEPARE")?.geoJsonStr)
//    println(s57.layer("LNDARE")?.geoJsonStr)
//    println(s57.layer("M_COVR")?.geoJsonStr)
//    s57.layer("DSID")?.features?.forEachIndexed { i, feat ->
//        println("feature num: ${i+1}")
//        feat.fieldNames.forEach {
//            println(it)
//        }
//    }
//    println(s57.outline_json)
//    println("checksum: ${s57.checksum}")
//    s57.layers.forEach {
//        println("layer: ${it.name}")
//        println("feature count geometry: ${it.features.filter { !it.hasGeometry }.size}")
//        println("feature count zero geometry: ${it.features.filter { it.hasGeometry }.size}")
////        it.features.forEach {
////            println("wkt: ${it.wkt}")
////        }
//    }
//
//    println("datum: ${s57.datum}")
//    println("depths: ${s57.depths}")
//    println("updated: ${s57.updated}")
//    println("issue date: ${s57.issue_date}")
//    println("scale: ${s57.scale}")
//    println("outline wkt: ${s57.outline_wkt}")
//    println("outline json: ${s57.outline_json}")
//    println("outline extent: ${s57.extent}")
//    println("z : ${s57.z}")
//    println("min y : ${s57.min_y}")
//    println("max y : ${s57.max_y}")
//    println("min x : ${s57.min_x}")
//    println("max x : ${s57.max_x}")
}
