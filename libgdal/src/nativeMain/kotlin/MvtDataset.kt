@file:OptIn(ExperimentalForeignApi::class, ExperimentalAtomicApi::class)

import Gdal.epsg3857
import Gdal.epsg4326
import Gdal.memDriver
import Gdal.mvtDriver
import kotlinx.cinterop.*
import kotlinx.serialization.json.JsonElement
import libgdal.*
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.incrementAndFetch
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.ref.Cleaner
import kotlin.native.ref.createCleaner


private val uniqueId = AtomicInt(0)
private val transformEpsg4326ToEpsg3857 = OCTNewCoordinateTransformation(epsg4326,  epsg3857);

fun transformToTileGeometry(geometry: OgrGeometry): OgrGeometry {
    OGR_G_Transform(geometry.ptr, transformEpsg4326ToEpsg3857).requireSuccess {
        "failed to transform geometry"
    }
    return geometry
}

private fun mvtPtr(
    minZoom: Int,
    maxZoom: Int,
    outPath: String,
): GDALDatasetH {
    return memScoped {
        var options: CPointer<CPointerVar<ByteVar>>? = null
        options = CSLSetNameValue(options, "MINZOOM", "$minZoom")
        options = CSLSetNameValue(options, "MAXZOOM", "$maxZoom")
        val ptr = GDALCreate(mvtDriver, outPath, 0, 0, 0, GDT_Unknown, options)
            ?: error("failed to reate mvt ds")
        CSLDestroy(options)
        ptr
    }
}

open class MvtDiskDataset (
    minZoom: Int = 0,
    maxZoom: Int = 18,
    outPath: String,
) : GdalDataset(
    ptr = mvtPtr(minZoom, maxZoom, outPath),
    sr = epsg3857,
    autoClose = false,
), AutoCloseable {


    override fun addFeature(layerName: String, props: Map<String, JsonElement>, geometry: OgrGeometry) {
        transformToTileGeometry(geometry)
        super.addFeature(layerName, props, geometry)
    }

    override fun close() {
        OCTDestroyCoordinateTransformation(transformEpsg4326ToEpsg3857)
        GDALClose(ptr)
    }
}

fun memPtr(): GDALDatasetH {
    return requireNotNull(
        GDALCreate(memDriver, "in_memory_ds", 0, 0, 0, GDT_Unknown, null)
    )
}

open class MvtDataset : GdalDataset(
    ptr = memPtr(),
    sr = epsg3857,
) {

//    private val transform = OCTNewCoordinateTransformation(epsg4326,  epsg3857);
//
//    @OptIn(ExperimentalNativeApi::class)
//    private val cleaner: Cleaner = createCleaner(transform) {
//        //println("free mvt dataset coordinate transformation")
//        OCTDestroyCoordinateTransformation(it)
//    }

    override fun addFeature(layerName: String, props: Map<String, JsonElement>, geometry: OgrGeometry) {
        OGR_G_Transform(geometry.ptr, transformEpsg4326ToEpsg3857).requireSuccess {
            "failed to transform geometry ${geometry.wkt}"
        }
        super.addFeature(layerName, props, geometry)
    }

    fun translateMvt(minZoom: Int, maxZoom: Int): MvtVsi {
        return memScoped {
            val outPath = "/vsimem/output_tiles${uniqueId.incrementAndFetch()}"
            val args = allocStringArray(
                "-f", "MVT",
                "-dsco", "MINZOOM=$minZoom",
                "-dsco", "MAXZOOM=$maxZoom",
                "-dsco", "FORMAT=DIRECTORY",
                "-dsco", "COMPRESS=NO",
            )
            val options = GDALVectorTranslateOptionsNew(args, null) ?: error("failed to create translate options")

            println("Starting translation to MVT...")

            // 5. Execute the translation
            // GDALVectorTranslate writes the output to disk based on the options provided
            val sourceDatasets = allocArray<GDALDatasetHVar>(1)
            sourceDatasets[0] = ptr
            val hDstDS = GDALVectorTranslate(
                outPath,
                null,
                1,
                sourceDatasets,
                options,
                null
            ) ?: error("Translation failed")

            println("Successfully wrote tiles to $outPath")

            GDALClose(hDstDS)
            GDALVectorTranslateOptionsFree(options)
            MvtVsi(outPath)
        }
    }
}

class MvtVsi(
    val outPath: String,
) {

    @OptIn(ExperimentalNativeApi::class)
    private val vsiPathCleaner: Cleaner = createCleaner(outPath) {
        println("vsi unlink")
        VSIUnlink(it);
    }

    fun listMvtTiles() : MvtVsi {
        val fileList: CPointer<CPointerVar<ByteVar>>? = VSIReadDirRecursive(outPath)

        if (fileList == null) {
            println("No files found in $outPath")
            return this
        }

        try {
            var i = 0
            while (true) {
                val filePtr = fileList[i] ?: break
                val fileName = filePtr.toKString()
                println("$outPath/$fileName")
                i++
            }
        } finally {
            CSLDestroy(fileList)
        }
        return this
    }

    fun readTileFromVsiMem(tilePath: String): ByteArray? {
        return memScoped {
            // 1. Allocate a variable to hold the size (vsi_l_offset is usually ULong)
            val pBufferSize = alloc<vsi_l_offsetVar>()

            // 2. Call VSIGetMemFileBuffer
            // pszFilename: The path in /vsimem/
            // pnDataLength: Pointer to our allocated size variable
            // bUnlinkAndSeize: FALSE to keep the file in memory, TRUE to delete it and take ownership
            val rawPtr: CPointer<GByteVar>? = VSIGetMemFileBuffer(tilePath, pBufferSize.ptr, 0)

            if (rawPtr == null) {
                println("Failed to read buffer from $tilePath")
                return null
            }

            val size = pBufferSize.value.toInt()

            // 3. Copy the raw C data into a Kotlin ByteArray
            val result = ByteArray(size)
            for (i in 0 until size) {
                result[i] = rawPtr[i].toByte()
            }

            result
        }
    }

    fun getMvt(z: Int, x: Int, y: Int): ByteArray {
        return readTileFromVsiMem("$outPath/$z/$x/$y.pbf") ?: ByteArray(0)
    }
}