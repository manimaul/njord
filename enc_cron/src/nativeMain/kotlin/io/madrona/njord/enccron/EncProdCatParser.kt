@file:OptIn(ExperimentalForeignApi::class)

package io.madrona.njord.enccron

import ElementPath
import ExpatSax
import File
import SaxHandler
import XmlAttrs
import attsToMap
import kotlinx.cinterop.ExperimentalForeignApi

/**
 * One ENC cell as published in NOAA's product catalog.
 *
 * [edition] is the raw `<edition>` string (e.g. `"44.2"`): `DSID_EDTN` and `DSID_UPDN` joined by
 * a dot. Only the update number half of it is used for comparison - see [revisionKey].
 */
data class EncCatalogEntry(
    /** Cell name without extension, e.g. `US5WA22M`. */
    val cell: String,
    val edition: String,
    val url: String,
    val scale: Int?,
    val sizeMb: Double?,
    /** `revision` date as `YYYYMMDD`; matches the base cell's S-57 `DSID_UADT`. */
    val revisionDate: String? = null,
    /** `publication` date as `YYYYMMDD`; matches `DSID_ISDT` after updates are applied. */
    val publicationDate: String? = null,
) {
    /** How this cell appears in `charts.name` once ingested (S-57 `DSID_DSNM`). */
    val chartName: String get() = "$cell.000"

    /** The `<edition>` suffix, i.e. the S-57 update number. `"74.6"` -> `"6"`. */
    val updateNumber: String?
        get() = edition.substringAfterLast('.', missingDelimiterValue = "")
            .takeIf { it.isNotBlank() }

    /**
     * Opaque key matched against `/v1/chart_editions`, in the same
     * `"<UPDN>:<UADT>:<ISDT>"` form the server builds from an ingested chart.
     *
     * Null when NOAA omitted one of the parts, which callers treat as "always stale" - re-fetching
     * unnecessarily is far cheaper than silently pinning a cell at a stale edition.
     *
     * The edition *number* (`DSID_EDTN`) is deliberately excluded: GDAL overwrites it with the
     * value from the last applied `.00N` update file, and NOAA publishes that as `0` for some
     * cells, so an edition-based key never matches for them. Verified against US1GC09M
     * (catalog `74.6`, GDAL reports EDTN 0 / UPDN 6).
     */
    val revisionKey: String?
        get() {
            val updn = updateNumber ?: return null
            val uadt = revisionDate ?: return null
            val isdt = publicationDate ?: return null
            return "$updn:$uadt:$isdt"
        }
}

/**
 * Streams NOAA's `ENCProdCat_19115.xml` and emits one [EncCatalogEntry] per `MD_Metadata` record.
 *
 * Everything here is path scoped rather than leaf-name matched, because the document reuses leaf
 * names across unrelated subtrees. In particular `<title><gco:CharacterString>` appears both
 * under `identificationInfo` (the cell name) and under
 * `dataQualityInfo/.../sourceCitation` (always the literal `"0"`). Matching on the leaf alone
 * yields 7000+ cells all named `0`.
 */
class EncProdCatParser(
    private val onRecord: (EncCatalogEntry) -> Unit,
) : SaxHandler {

    private enum class Field { CELL, EDITION, URL, SCALE, SIZE_MB, DATE, DATE_TYPE }

    private class Rule(val tail: Array<String>, val field: Field)

    private val path = ElementPath()
    private val text = StringBuilder(64)
    private var active: Field? = null

    private var cell: String? = null
    private var edition: String? = null
    private var url: String? = null
    private var scale: Int? = null
    private var sizeMb: Double? = null
    private var revisionDate: String? = null
    private var publicationDate: String? = null

    // A CI_Date holds its <gco:Date> *before* the <dateType> that says which date it is, so the
    // value has to be parked until the discriminator arrives at </CI_Date>.
    private var pendingDate: String? = null
    private var pendingDateType: String? = null

    /** Records seen, including any dropped as incomplete. */
    var recordsSeen: Int = 0
        private set

    /** Records dropped because a required field was missing. */
    var recordsSkipped: Int = 0
        private set

    override fun startElement(name: String, atts: XmlAttrs) {
        path.push(name)

        if (path.depth == 1) validateRoot(name, atts)

        if (name == RECORD_ELEMENT) {
            cell = null
            edition = null
            url = null
            scale = null
            sizeMb = null
            revisionDate = null
            publicationDate = null
        }

        if (name == DATE_ELEMENT) {
            pendingDate = null
            pendingDateType = null
        }

        // Indexed by leaf name so only the four interesting element names ever run a tail check.
        active = RULES_BY_LEAF[name]?.firstOrNull { path.endsWith(it.tail) }?.field
        if (active != null) text.setLength(0)
    }

    override fun characters(bytes: ByteArray) {
        if (active != null) text.append(bytes.decodeToString())
    }

    override fun endElement(name: String) {
        active?.let { field ->
            val value = text.toString().trim()
            when (field) {
                Field.CELL -> cell = value
                Field.EDITION -> edition = value
                Field.URL -> url = value
                Field.SCALE -> scale = value.toIntOrNull()
                Field.SIZE_MB -> sizeMb = value.toDoubleOrNull()
                // Normalise "2025-12-09" to the "20251209" form S-57 stores in UADT/ISDT.
                Field.DATE -> pendingDate = value.replace("-", "").takeIf { it.isNotBlank() }
                Field.DATE_TYPE -> pendingDateType = value
            }
            active = null
            text.setLength(0)
        }

        if (name == DATE_ELEMENT) {
            when (pendingDateType) {
                "revision" -> revisionDate = pendingDate
                "publication" -> publicationDate = pendingDate
            }
            pendingDate = null
            pendingDateType = null
        }

        if (name == RECORD_ELEMENT) {
            recordsSeen++
            val c = cell
            val e = edition
            val u = url
            if (!c.isNullOrBlank() && !e.isNullOrBlank() && !u.isNullOrBlank()) {
                onRecord(
                    EncCatalogEntry(
                        cell = c,
                        edition = e,
                        url = u,
                        scale = scale,
                        sizeMb = sizeMb,
                        revisionDate = revisionDate,
                        publicationDate = publicationDate,
                    )
                )
            } else {
                // One malformed record must not abort a 7000 cell catalog.
                recordsSkipped++
            }
        }

        path.pop()
    }

    /**
     * Namespace prefixes are matched literally rather than resolved (see [ExpatSax]), which is
     * safe only as long as NOAA keeps binding them the same way. Assert that here so a change
     * upstream fails loudly instead of silently yielding zero records.
     */
    private fun validateRoot(name: String, atts: XmlAttrs) {
        require(name == ROOT_ELEMENT) {
            "unexpected root element '$name', expected '$ROOT_ELEMENT' - is this really ENCProdCat_19115.xml?"
        }
        val bindings = attsToMap(atts)
        REQUIRED_NAMESPACES.forEach { (prefix, uri) ->
            require(bindings[prefix] == uri) {
                "ENCProdCat namespace binding changed: expected $prefix=$uri but got ${bindings[prefix]}. " +
                        "The element path rules in EncProdCatParser must be revisited."
            }
        }
    }

    companion object {
        private const val ROOT_ELEMENT = "DS_Series"
        private const val RECORD_ELEMENT = "MD_Metadata"
        private const val DATE_ELEMENT = "CI_Date"

        private val REQUIRED_NAMESPACES = mapOf(
            "xmlns" to "http://www.isotc211.org/2005/gmd",
            "xmlns:gco" to "http://www.isotc211.org/2005/gco",
        )

        private val RULES = listOf(
            Rule(
                arrayOf(
                    "identificationInfo", "MD_DataIdentification", "citation",
                    "CI_Citation", "title", "gco:CharacterString",
                ),
                Field.CELL,
            ),
            Rule(
                arrayOf(
                    "identificationInfo", "MD_DataIdentification", "citation",
                    "CI_Citation", "edition", "gco:CharacterString",
                ),
                Field.EDITION,
            ),
            // Scoped to `citation`, not `sourceCitation`, so the dataQualityInfo lineage dates
            // (which carry a nilReason and no value) cannot leak in.
            Rule(
                arrayOf(
                    "citation", "CI_Citation", "date", "CI_Date", "date", "gco:Date",
                ),
                Field.DATE,
            ),
            Rule(
                arrayOf(
                    "citation", "CI_Citation", "date", "CI_Date", "dateType", "CI_DateTypeCode",
                ),
                Field.DATE_TYPE,
            ),
            Rule(
                arrayOf(
                    "spatialResolution", "MD_Resolution", "equivalentScale",
                    "MD_RepresentativeFraction", "denominator", "gco:Integer",
                ),
                Field.SCALE,
            ),
            Rule(
                arrayOf(
                    "distributionInfo", "MD_Distribution", "transferOptions",
                    "MD_DigitalTransferOptions", "transferSize", "gco:Real",
                ),
                Field.SIZE_MB,
            ),
            Rule(
                arrayOf(
                    "distributionInfo", "MD_Distribution", "transferOptions",
                    "MD_DigitalTransferOptions", "onLine", "CI_OnlineResource",
                    "linkage", "URL",
                ),
                Field.URL,
            ),
        )

        private val RULES_BY_LEAF: Map<String, List<Rule>> = RULES.groupBy { it.tail.last() }
    }
}

/**
 * Parses a catalog already on disk. Used by `--from-file` and by tests; the streaming network
 * path in [CatalogClient] drives the same [EncProdCatParser].
 */
fun parseCatalogFile(file: File, chunkSize: Int = 256 * 1024): List<EncCatalogEntry> {
    val entries = mutableListOf<EncCatalogEntry>()
    val parser = EncProdCatParser { entries.add(it) }
    val bytes = file.readData()
    ExpatSax(parser).use { sax ->
        var offset = 0
        while (offset < bytes.size) {
            val n = minOf(chunkSize, bytes.size - offset)
            sax.feed(bytes.copyOfRange(offset, offset + n), n)
            offset += n
        }
        sax.finish()
    }
    return entries
}
