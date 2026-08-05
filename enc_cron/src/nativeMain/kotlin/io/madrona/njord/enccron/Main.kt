package io.madrona.njord.enccron

import File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlin.system.exitProcess
import kotlin.time.Clock

/**
 * Nightly NOAA ENC updater.
 *
 * Replaces a cron that blindly re-downloaded `OneDay_ENCs.zip`. Instead this parses NOAA's
 * product catalog, asks Njord which chart editions it already holds, and fetches only the cells
 * whose edition actually moved - which also means anything missed while the cluster was down is
 * picked up on the next run rather than lost.
 *
 * Usage: `enc_cron [resourcesDir] [--from-file <catalog.xml>] [--dry-run]`
 *
 * Configuration comes from `<resourcesDir>/config/enc_cron.json` overlaid with the
 * `ENC_CRON_OPTS` environment variable; see [EncCronConfig].
 */
fun main(args: Array<String>) {
    val options = CommandLine.parse(args)
    val config = EncCronConfig.load(options.resourcesDir)

    val exitCode = runBlocking {
        try {
            withContext(Dispatchers.IO) { run(config, options) }
            0
        } catch (t: Throwable) {
            // Non-zero so the CronJob's restartPolicy: OnFailure retries.
            log.error("enc_cron failed", t)
            1
        }
    }
    exitProcess(exitCode)
}

private suspend fun run(config: EncCronConfig, options: CommandLine) {
    log.info("catalog=${options.catalogFile?.path ?: config.catalogUrl}")
    log.info("editions=${config.chartEditionsUrl}")
    log.info("save dir=${config.saveDir.path}")

    EncHttp(config).use { http ->
        val have = http.fetchChartEditions()
        log.info("njord holds ${have.size} charts")

        val catalog = options.catalogFile
            ?.let { parseCatalogFile(it) }
            ?: http.fetchCatalog()
        log.info("noaa catalog lists ${catalog.size} cells")
        check(catalog.isNotEmpty()) { "product catalog yielded no cells" }

        val orphans = selectOrphans(catalog, have)
        if (orphans.isNotEmpty()) {
            log.info("${orphans.size} chart(s) held but not listed in the catalog:")
            orphans.take(ORPHAN_LOG_LIMIT).forEach { log.info("  $it") }
            if (orphans.size > ORPHAN_LOG_LIMIT) log.info("  ... and ${orphans.size - ORPHAN_LOG_LIMIT} more")
        }

        val stale = selectStale(catalog, have, config)
        if (stale.isEmpty()) {
            log.info("all cells up to date, nothing to do")
            return
        }

        val capped = stale.take(config.maxCellsPerRun)
        val sizeMb = capped.sumOf { it.sizeMb ?: 0.0 }
        log.info(
            "${stale.size} cell(s) out of date; fetching ${capped.size} this run " +
                    "(~${(sizeMb * 10).toInt() / 10.0} MB)" +
                    if (stale.size > capped.size) ", ${stale.size - capped.size} deferred to a later run" else ""
        )

        if (options.dryRun) {
            capped.take(20).forEach {
                log.info(
                    "  would fetch ${it.cell} edition ${it.edition}: " +
                            "${have[it.chartName] ?: "<absent>"} -> ${it.revisionKey ?: "<incomplete>"}"
                )
            }
            if (capped.size > 20) log.info("  ... and ${capped.size - 20} more")
            return
        }

        publishBundles(capped, config, http)
    }
}

/**
 * A cell is stale when Njord's stored revision key differs from NOAA's in *either* direction.
 *
 * Keys are compared as opaque strings and never parsed as numbers: `"1.10"` and `"1.1"` are
 * distinct S-57 states but equal as floats, and any mismatch warrants a re-download regardless of
 * which side looks newer.
 *
 * A cell whose catalog entry is missing a key part has [EncCatalogEntry.revisionKey] == null,
 * which never equals a stored key, so it is always re-fetched. Wasted bandwidth is much cheaper
 * than pinning a cell at a stale edition forever.
 */
internal fun selectStale(
    catalog: List<EncCatalogEntry>,
    have: Map<String, String>,
    config: EncCronConfig,
): List<EncCatalogEntry> = catalog
    .asSequence()
    .filter { config.scaleFilter.isEmpty() || it.scale in config.scaleFilter }
    .filter { it.revisionKey == null || have[it.chartName] != it.revisionKey }
    // Deterministic order so a capped run makes the same progress every time rather than
    // re-shuffling which cells get deferred.
    .sortedBy { it.cell }
    .toList()

/**
 * Chart names Njord holds that NOAA's catalog no longer lists.
 *
 * Reported only, never acted on: an absent cell may be genuinely withdrawn, or it may have been
 * ingested from another hydrographic office, so removal stays a human decision.
 *
 * [EncCronConfig.scaleFilter] deliberately does not apply - membership in the catalog is what makes
 * a chart an orphan, not whether this run would have fetched it.
 */
internal fun selectOrphans(catalog: List<EncCatalogEntry>, have: Map<String, String>): List<String> {
    val listed = catalog.mapTo(mutableSetOf()) { it.chartName }
    return have.keys.filterNot { it in listed }.sorted()
}

private suspend fun publishBundles(cells: List<EncCatalogEntry>, config: EncCronConfig, http: EncHttp) {
    config.workDir.mkdirs()
    config.saveDir.mkdirs()

    val builder = BundleBuilder(config, http)
    val stamp = Clock.System.now().toString().substringBefore('T').replace("-", "")
    var bundleNumber = 0
    var published = 0

    for (batch in batches(cells, config)) {
        val queued = queuedBundleCount(config)
        if (queued >= config.maxQueuedBundles) {
            log.info(
                "$queued bundle(s) already queued in ${config.saveDir.path} " +
                        "(max ${config.maxQueuedBundles}); stopping so ingest can catch up"
            )
            break
        }

        bundleNumber++
        val name = "enc_bundle-$stamp-${bundleNumber.toString().padStart(4, '0')}.zip"
        log.info("building $name from ${batch.size} cell(s)")
        if (builder.buildAndPublish(batch, name) != null) published++
    }

    log.info("done: published $published bundle(s)")
}

/**
 * Splits [cells] into batches bounded by both cell count and estimated uncompressed size.
 *
 * NOAA's `transferSize` is the compressed figure; S-57 deflates to roughly a quarter of its raw
 * size, so scale it up to approximate what ChartIngest will actually have to hold open.
 */
internal fun batches(cells: List<EncCatalogEntry>, config: EncCronConfig): List<List<EncCatalogEntry>> {
    val out = mutableListOf<List<EncCatalogEntry>>()
    var current = mutableListOf<EncCatalogEntry>()
    var currentBytes = 0L

    for (cell in cells) {
        val estimated = ((cell.sizeMb ?: 0.0) * 1024 * 1024 * UNCOMPRESSED_RATIO).toLong()
        val full = current.size >= config.bundleSizeCells ||
                (current.isNotEmpty() && currentBytes + estimated > config.maxBundleUncompressedBytes)
        if (full) {
            out.add(current)
            current = mutableListOf()
            currentBytes = 0L
        }
        current.add(cell)
        currentBytes += estimated
    }
    if (current.isNotEmpty()) out.add(current)
    return out
}

private const val UNCOMPRESSED_RATIO = 4.0

/** Cap on orphan names logged per run, so a catalog hiccup can't bury the rest of the output. */
private const val ORPHAN_LOG_LIMIT = 50

private fun queuedBundleCount(config: EncCronConfig): Int =
    config.saveDir.listFiles(false).count { it.name.endsWith(".zip", ignoreCase = true) }

private class CommandLine(
    val resourcesDir: File?,
    val catalogFile: File?,
    val dryRun: Boolean,
) {
    companion object {
        fun parse(args: Array<String>): CommandLine {
            var resourcesDir: File? = null
            var catalogFile: File? = null
            var dryRun = false
            var i = 0
            while (i < args.size) {
                when (val arg = args[i]) {
                    "--from-file" -> {
                        val path = args.getOrNull(++i)
                            ?: throw IllegalArgumentException("--from-file requires a path")
                        catalogFile = File(path).also {
                            require(it.exists()) { "catalog file does not exist: $path" }
                        }
                    }

                    "--dry-run" -> dryRun = true

                    else -> {
                        require(!arg.startsWith("--")) { "unknown option: $arg" }
                        resourcesDir = File(arg).takeIf { it.exists() }
                    }
                }
                i++
            }
            return CommandLine(resourcesDir, catalogFile, dryRun)
        }
    }
}
