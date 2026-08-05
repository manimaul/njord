@file:OptIn(ExperimentalForeignApi::class)

package io.madrona.njord.enccron

import ExpatSax
import File
import io.ktor.client.*
import io.ktor.client.engine.curl.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.utils.io.readAvailable
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import platform.posix.fclose
import platform.posix.fopen
import platform.posix.fwrite
import kotlin.io.encoding.Base64

private const val DOWNLOAD_CHUNK = 8 * 1024 * 1024 // 8 MB
private const val PARSE_CHUNK = 64 * 1024

/**
 * All outbound HTTP for the cron job: the NOAA catalog, Njord's edition listing, and cell zips.
 */
class EncHttp(private val config: EncCronConfig) : AutoCloseable {

    private val json = Json { ignoreUnknownKeys = true }

    private val client = HttpClient(Curl) {
        install(HttpTimeout) {
            requestTimeoutMillis = config.requestTimeoutSeconds * 1000
            socketTimeoutMillis = 60_000
        }
        expectSuccess = true
    }

    /**
     * Charts Njord already has, as `chartName -> "<EDTN>.<UPDN>"`.
     *
     * Failure here must propagate: with no baseline every cell looks stale, and silently falling
     * back to "download everything" would hammer NOAA and flood the ingest queue.
     */
    suspend fun fetchChartEditions(): Map<String, String> = withRetry("chart editions") {
        val body = client.get(config.chartEditionsUrl).bodyAsText()
        json.decodeFromString<ChartEditionsResponse>(body).editions
    }

    /**
     * Streams the product catalog through expat without ever materialising the 52 MB document.
     *
     * A truncated body cannot be resumed mid-stream, so the retry wraps the whole fetch-and-parse
     * and starts over with a fresh parser.
     */
    suspend fun fetchCatalog(): List<EncCatalogEntry> = withRetry("product catalog") {
        val entries = mutableListOf<EncCatalogEntry>()
        val parser = EncProdCatParser { entries.add(it) }
        ExpatSax(parser).use { sax ->
            client.prepareGet(config.catalogUrl) {
                // Pin identity encoding: if a proxy hands back undecoded gzip, expat fails with a
                // baffling "not well-formed (invalid token)" at line 1.
                header(HttpHeaders.AcceptEncoding, "identity")
            }.execute { response ->
                val channel = response.bodyAsChannel()
                val buffer = ByteArray(PARSE_CHUNK)
                while (!channel.isClosedForRead) {
                    val count = channel.readAvailable(buffer)
                    if (count > 0) sax.feed(buffer, count)
                }
            }
            sax.finish()
        }
        if (parser.recordsSkipped > 0) {
            log.warn("catalog: skipped ${parser.recordsSkipped} of ${parser.recordsSeen} records missing required fields")
        }
        entries
    }

    /**
     * A short lived admin signature from `GET /v1/admin`, which every mutating endpoint requires.
     *
     * The signature is an HMAC over the base URL the server saw on this request, so it is only
     * valid for calls made against the same [baseUrl].
     */
    suspend fun fetchAdminSignature(baseUrl: String, user: String, pass: String): String =
        withRetry("admin signature") {
            val credentials = Base64.encode("$user:$pass".encodeToByteArray())
            val body = client.get("$baseUrl/v1/admin") {
                header(HttpHeaders.Authorization, "Basic $credentials")
            }.bodyAsText()
            json.decodeFromString<AdminSignatureResponse>(body).signatureEncoded
        }

    /**
     * Deletes a chart by name. True when a row was removed, false when the server had nothing by
     * that name - a benign race with a concurrent ingest, not a failure.
     *
     * The URL is assembled by hand because [signature] arrives already URL encoded and ktor's
     * parameter builder would encode it a second time.
     */
    suspend fun deleteChart(baseUrl: String, name: String, signature: String): Boolean =
        withRetry("delete chart $name") {
            client.delete("$baseUrl/v1/chart?name=$name&signature=$signature")
                .status == HttpStatusCode.Accepted
        }

    /**
     * Downloads [url] to [dest] via a sibling `.tmp` file, so a crashed or truncated transfer
     * never leaves a plausible looking zip behind. Returns bytes written.
     */
    suspend fun download(url: String, dest: File): Long = withRetry("download $url") {
        val tmp = File(dest.parentFile()!!, "${dest.name}.tmp")
        val tmpPath = tmp.getAbsolutePath().toString()
        try {
            val fp = fopen(tmpPath, "wb")
                ?: throw IllegalStateException("cannot open $tmpPath for writing")
            var total = 0L
            try {
                client.prepareGet(url).execute { response ->
                    val channel = response.bodyAsChannel()
                    val buffer = ByteArray(DOWNLOAD_CHUNK)
                    while (!channel.isClosedForRead) {
                        val count = channel.readAvailable(buffer)
                        if (count > 0) {
                            buffer.usePinned { pinned ->
                                fwrite(pinned.addressOf(0), 1u, count.toULong(), fp)
                            }
                            total += count
                        }
                    }
                }
            } finally {
                fclose(fp)
            }
            tmp.renameTo(dest.getAbsolutePath().toString())
                ?: throw IllegalStateException("cannot publish $tmpPath -> ${dest.path}")
            total
        } catch (t: Throwable) {
            if (tmp.exists()) tmp.deleteRecursively()
            throw t
        }
    }

    private suspend fun <T> withRetry(what: String, block: suspend () -> T): T {
        var lastFailure: Throwable? = null
        repeat(config.maxRetries) { attempt ->
            try {
                return block()
            } catch (t: Throwable) {
                lastFailure = t
                val remaining = config.maxRetries - attempt - 1
                if (remaining > 0) {
                    val backoffMs = 2_000L * (attempt + 1)
                    log.warn("$what failed (${t.message}); $remaining attempt(s) left, retrying in ${backoffMs}ms")
                    delay(backoffMs)
                }
            }
        }
        throw IllegalStateException("$what failed after ${config.maxRetries} attempts", lastFailure)
    }

    override fun close() = client.close()
}

@kotlinx.serialization.Serializable
private data class ChartEditionsResponse(val editions: Map<String, String>)

@kotlinx.serialization.Serializable
private data class AdminSignatureResponse(val signatureEncoded: String)
