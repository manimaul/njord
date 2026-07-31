package io.madrona.njord.model

import kotlinx.serialization.Serializable

@Serializable
data class ChartItem(
    val id: Long,
    val name: String,
)

@Serializable
data class ChartCatalog(
    val totalChartCount: Int,
    val nextId: Long?,
    val page: List<ChartItem>,
)

/**
 * Every ingested chart keyed by `charts.name` (the S-57 `DSID_DSNM`, e.g. `US5WA22M.000`), valued
 * by an opaque revision key of the form `"<DSID_UPDN>:<DSID_UADT>:<DSID_ISDT>"`
 * (e.g. `"6:20251209:20260520"`).
 *
 * The enc_cron job rebuilds the same key from NOAA's `ENCProdCat_19115.xml` - update number from
 * the `<edition>` suffix, `UADT` from the `revision` date, `ISDT` from the `publication` date -
 * and re-downloads any cell whose key differs. Keys are compared as opaque strings and never
 * ordered; a difference in either direction means re-fetch.
 *
 * `DSID_EDTN` is deliberately NOT part of the key. GDAL overwrites it with the value carried by
 * the last applied `.00N` update file, which NOAA publishes as `0` for some cells - so an
 * edition-based key reports a permanent false mismatch and re-downloads those cells forever.
 * `UPDN`, `UADT` and `ISDT` all survive update application intact.
 */
@Serializable
data class ChartEditions(
    val editions: Map<String, String>,
)