package io.madrona.njord.enccron

import File
import ZipFile
import ZipWriter

/**
 * Assembles downloaded NOAA cells into bundle zips and publishes them into the ingest worker's
 * `save/` directory.
 *
 * Bundling is required rather than an optimisation: `ChartIngestWorker` claims exactly one zip
 * per distributed-lock cycle and polls every 5 s, so shipping cells individually would mean one
 * full lock / extract / GDAL open / insert / tile-cache-invalidate cycle per cell.
 *
 * Bundles stay small for the opposite reason: `ChartIngest` opens every `.000` in a bundle up
 * front, holds them all alive for the run, and pre-counts features across all of them before
 * inserting a single row.
 */
class BundleBuilder(
    private val config: EncCronConfig,
    private val http: EncHttp,
) {

    /**
     * Downloads [cells], stages them, writes one bundle zip and publishes it. Returns the
     * published bundle, or null if nothing could be downloaded.
     *
     * Cells that fail to download are logged and skipped - a single 404 must not lose the rest of
     * the batch. Since nothing is recorded locally, a skipped cell is simply retried next run.
     */
    suspend fun buildAndPublish(cells: List<EncCatalogEntry>, bundleName: String): File? {
        val stagingRoot = File(config.workDir, "staging")
        val downloadDir = File(config.workDir, "download")
        stagingRoot.deleteRecursively()
        downloadDir.deleteRecursively()
        stagingRoot.mkdirs()
        downloadDir.mkdirs()

        var staged = 0
        for (cell in cells) {
            val cellZip = File(downloadDir, "${cell.cell}.zip")
            try {
                http.download(cell.url, cellZip)
                extractCell(cellZip, cell.cell, stagingRoot)
                staged++
            } catch (t: Throwable) {
                log.warn("skipping ${cell.cell}: ${t.message}")
            } finally {
                if (cellZip.exists()) cellZip.deleteRecursively()
            }
        }

        if (staged == 0) {
            log.warn("no cells staged for $bundleName, nothing to publish")
            stagingRoot.deleteRecursively()
            return null
        }

        val bundle = File(config.workDir, bundleName)
        val stagedFiles = stagingRoot.listFiles(true)
        val stagingPrefix = "${stagingRoot.getAbsolutePath()}/"

        ZipWriter.create(bundle).use { writer ->
            stagedFiles.forEach { file ->
                val absolute = file.getAbsolutePath().toString()
                val entryName = absolute.removePrefix(stagingPrefix)
                check(entryName != absolute) { "staged file escaped the staging root: $absolute" }
                writer.addFile(entryName, file)
            }
        }
        // Only now are the sources safe to remove - ZipWriter reads them during close().
        stagingRoot.deleteRecursively()
        downloadDir.deleteRecursively()

        val published = bundle.renameTo(File(config.saveDir, bundleName).getAbsolutePath().toString())
        if (published == null) {
            log.error("failed to publish $bundleName into ${config.saveDir.path}")
            bundle.deleteRecursively()
            return null
        }
        log.info("published $bundleName ($staged cells, ${stagedFiles.size} files)")
        return published
    }

    /**
     * Extracts one NOAA cell zip into the shared staging root, keeping only `ENC_ROOT/<CELL>/`.
     *
     * Entry names pass through untouched, so the `.000`, its `.001..` updates and the sibling
     * `.TXT` files land in one directory - exactly the layout GDAL's S57 driver needs to apply
     * updates.
     *
     * The other entries are dropped deliberately: `ENC_ROOT/CATALOG.031` is per cell and would
     * collide across every cell in the bundle, while `README.TXT` and `USERAGREEMENT.TXT` are
     * duplicated boilerplate. None is read by `OgrS57Dataset`, which opens the `.000` directly.
     */
    private fun extractCell(cellZip: File, cell: String, stagingRoot: File) {
        val prefix = "ENC_ROOT/$cell/"
        var extracted = 0
        ZipFile(cellZip).let { zip ->
            zip.entries()
                .filter { !it.isDirectory() && it.name().startsWith(prefix) }
                .forEach {
                    it.unzipToPath(stagingRoot)
                    extracted++
                }
        }
        check(extracted > 0) { "cell zip for $cell contained no $prefix entries" }
    }
}
