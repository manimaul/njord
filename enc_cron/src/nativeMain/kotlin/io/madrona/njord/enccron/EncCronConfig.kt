package io.madrona.njord.enccron

import File
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.toKString
import platform.posix.getenv

@Serializable
data class EncCronConfig(
    /** NOAA's ISO-19115 product catalog. ~52 MB, ~7k cells. */
    val catalogUrl: String,

    /** Njord's bulk edition listing. In cluster this is the chart service, not the ingest pod. */
    val chartEditionsUrl: String,

    /** Ingest worker's shared volume. Bundles are published to `<chartTempData>/save`. */
    val chartTempData: String,

    /**
     * Upper bound on cells fetched per invocation. A cold catalog is ~7k cells / ~818 MB, which
     * would be a multi hour run and a large burst of ingest work; capping lets it converge over
     * several nightly runs. Steady state deltas are far below this, so it normally never binds.
     */
    val maxCellsPerRun: Int,

    /**
     * Cells per bundle zip. Kept small because ChartIngest opens every .000 in a bundle up front
     * and pre-counts all their features before inserting anything.
     */
    val bundleSizeCells: Int,

    /** Secondary bundle cap for batches that happen to be large harbour cells. */
    val maxBundleUncompressedBytes: Long,

    /** Stop publishing when this many bundles are already queued in `save/`. */
    val maxQueuedBundles: Int = 3,

    /** Restrict to these S-57 compilation scales. Empty means all scales. */
    val scaleFilter: List<Int>,

    /** Per request timeout for cell downloads and the catalog fetch. */
    val requestTimeoutSeconds: Long,

    /** Attempts per HTTP resource before giving up on it. */
    val maxRetries: Int,

    /**
     * Delete charts Njord holds that NOAA's catalog no longer lists, rather than only reporting
     * them. Silently inert without [adminUser] / [adminPass] - the delete endpoint is signed.
     */
    val deleteOrphans: Boolean = true,

    /**
     * Ceiling on charts deleted in one run. A NOAA outage that serves a truncated catalog looks
     * exactly like a mass withdrawal, so past this many the whole deletion pass is abandoned and
     * the orphans are only reported. Real withdrawals arrive a handful at a time.
     */
    val maxOrphanDeletes: Int = 25,

    /**
     * Basic auth credentials for `GET /v1/admin`, which mints the signature every mutating
     * endpoint requires. Prefer the `ENC_CRON_ADMIN_USER` / `ENC_CRON_ADMIN_PASS` environment
     * variables so a Kubernetes secret can supply them without a password in a JSON blob.
     */
    val adminUser: String = "",
    val adminPass: String = "",
) {
    /** Where ChartIngestWorker polls for `*.zip`. */
    val saveDir: File get() = File(chartTempData, "save")

    /** Scratch space for downloads, staging and bundle assembly. Same filesystem as [saveDir]. */
    val workDir: File get() = File(chartTempData, "enc_cron")

    /**
     * Njord's API root, derived from [chartEditionsUrl] so one setting names the host. Null when
     * that URL has no `/v1/` in it, in which case nothing that needs a second endpoint can run.
     */
    val njordBaseUrl: String?
        get() = chartEditionsUrl.takeIf { it.contains("/v1/") }?.substringBefore("/v1/")

    companion object {
        private const val ENV_OVERRIDES = "ENC_CRON_OPTS"
        private const val ENV_ADMIN_USER = "ENC_CRON_ADMIN_USER"
        private const val ENV_ADMIN_PASS = "ENC_CRON_ADMIN_PASS"
        private val json = Json { ignoreUnknownKeys = true }

        /**
         * Loads `<resourcesDir>/config/enc_cron.json` if present, then overlays the
         * [ENV_OVERRIDES] environment variable as a shallow JSON object merge - the same
         * layering the server uses for CHART_SERVER_OPTS. Every field has a default, so both
         * layers are optional.
         */
        @OptIn(ExperimentalForeignApi::class)
        fun load(resourcesDir: File?): EncCronConfig {
            val base = resourcesDir
                ?.let { File(it, "config/enc_cron.json") }
                ?.takeIf { it.exists() }
                ?.let { json.parseToJsonElement(it.readContents()).jsonObject }
                ?: JsonObject(emptyMap())

            val merged = getenv(ENV_OVERRIDES)?.toKString()
                ?.takeIf { it.isNotBlank() }
                ?.let { JsonObject(base + json.parseToJsonElement(it).jsonObject) }
                ?: base

            return json.decodeFromJsonElement<EncCronConfig>(merged).withCredentialsFromEnv()
        }

        /**
         * Overlays [ENV_ADMIN_USER] / [ENV_ADMIN_PASS] on top of whatever the JSON layers set, so
         * the admin password can come from a secret keyed on its own rather than embedded in the
         * [ENV_OVERRIDES] blob.
         */
        @OptIn(ExperimentalForeignApi::class)
        private fun EncCronConfig.withCredentialsFromEnv(): EncCronConfig = copy(
            adminUser = getenv(ENV_ADMIN_USER)?.toKString()?.takeIf { it.isNotBlank() } ?: adminUser,
            adminPass = getenv(ENV_ADMIN_PASS)?.toKString()?.takeIf { it.isNotBlank() } ?: adminPass,
        )
    }
}
