import io.madrona.njord.enccron.EncCatalogEntry
import io.madrona.njord.enccron.EncCronConfig
import io.madrona.njord.enccron.EncProdCatParser
import io.madrona.njord.enccron.batches
import io.madrona.njord.enccron.selectStale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Parsing rules for NOAA's ENCProdCat_19115.xml.
 *
 * The fixture mirrors the real document's shape, including the decoy `<title>` under
 * `dataQualityInfo` whose value is always the literal "0". Matching `<title>` by leaf name alone
 * makes every cell come back named "0", which is silent and total - hence the explicit coverage.
 */
class EncProdCatParserTest {

    private fun record(
        cell: String,
        edition: String,
        scale: String = "15000",
        sizeMb: String = "0.483",
        revision: String = "2024-12-18",
        publication: String = "2026-01-21",
    ) = """
        <composedOf><DS_DataSet><has><MD_Metadata>
            <identificationInfo><MD_DataIdentification>
                <citation><CI_Citation>
                    <title><gco:CharacterString>$cell</gco:CharacterString></title>
                    <alternateTitle><gco:CharacterString>Somewhere</gco:CharacterString></alternateTitle>
                    <date><CI_Date>
                        <date><gco:Date>$revision</gco:Date></date>
                        <dateType><CI_DateTypeCode codeListValue="revision">revision</CI_DateTypeCode></dateType>
                    </CI_Date></date>
                    <date><CI_Date>
                        <date><gco:Date>$publication</gco:Date></date>
                        <dateType><CI_DateTypeCode codeListValue="publication">publication</CI_DateTypeCode></dateType>
                    </CI_Date></date>
                    <edition><gco:CharacterString>$edition</gco:CharacterString></edition>
                </CI_Citation></citation>
                <spatialResolution><MD_Resolution><equivalentScale><MD_RepresentativeFraction>
                    <denominator><gco:Integer>$scale</gco:Integer></denominator>
                </MD_RepresentativeFraction></equivalentScale></MD_Resolution></spatialResolution>
            </MD_DataIdentification></identificationInfo>
            <distributionInfo><MD_Distribution><transferOptions><MD_DigitalTransferOptions>
                <transferSize><gco:Real>$sizeMb</gco:Real></transferSize>
                <onLine><CI_OnlineResource>
                    <linkage><URL>https://www.charts.noaa.gov/ENCs/$cell.zip</URL></linkage>
                    <description><gco:CharacterString>zipfile date and time: 2026-07-31T05:20:37Z</gco:CharacterString></description>
                </CI_OnlineResource></onLine>
            </MD_DigitalTransferOptions></transferOptions></MD_Distribution></distributionInfo>
            <dataQualityInfo><DQ_DataQuality><lineage><LI_Lineage><source><LI_Source>
                <sourceCitation><CI_Citation>
                    <title><gco:CharacterString>0</gco:CharacterString></title>
                    <date gco:nilReason="Date uknown"/>
                </CI_Citation></sourceCitation>
            </LI_Source></source></LI_Lineage></lineage></DQ_DataQuality></dataQualityInfo>
        </MD_Metadata></has></DS_DataSet></composedOf>
    """.trimIndent()

    private fun document(
        body: String,
        gmd: String = "http://www.isotc211.org/2005/gmd",
        gco: String = "http://www.isotc211.org/2005/gco",
        root: String = "DS_Series",
    ) = """<?xml version="1.0" encoding="UTF-8"?>
        |<$root xmlns="$gmd" xmlns:gco="$gco" xmlns:gml="http://www.opengis.net/gml/3.2">
        |$body
        |</$root>
    """.trimMargin()

    private fun parse(xml: String, chunkSize: Int = 64 * 1024): List<EncCatalogEntry> {
        val entries = mutableListOf<EncCatalogEntry>()
        val bytes = xml.encodeToByteArray()
        ExpatSax(EncProdCatParser { entries.add(it) }).use { sax ->
            var off = 0
            while (off < bytes.size) {
                val n = minOf(chunkSize, bytes.size - off)
                sax.feed(bytes.copyOfRange(off, off + n), n)
                off += n
            }
            sax.finish()
        }
        return entries
    }

    @Test
    fun `extracts every field from a record`() {
        val entry = parse(document(record("US5WA22M", "44.2"))).single()
        assertEquals("US5WA22M", entry.cell)
        assertEquals("44.2", entry.edition)
        assertEquals("https://www.charts.noaa.gov/ENCs/US5WA22M.zip", entry.url)
        assertEquals(15000, entry.scale)
        assertEquals(0.483, entry.sizeMb)
        assertEquals("20241218", entry.revisionDate, "revision date -> DSID_UADT")
        assertEquals("20260121", entry.publicationDate, "publication date -> DSID_ISDT")
        assertEquals("2", entry.updateNumber)
        assertEquals("2:20241218:20260121", entry.revisionKey)
    }

    @Test
    fun `revision and publication dates are told apart by their dateType`() {
        // The <gco:Date> precedes the <dateType> that identifies it, so the parser has to park
        // the value until the discriminator arrives. Distinct dates prove they do not swap.
        val entry = parse(
            document(record("US1GC09M", "74.6", revision = "2025-12-09", publication = "2026-05-20"))
        ).single()
        assertEquals("20251209", entry.revisionDate)
        assertEquals("20260520", entry.publicationDate)
    }

    @Test
    fun `revisionKey excludes the edition number so an EDTN of zero cannot break matching`() {
        // Real case: catalog says US1GC09M is edition 74.6, but GDAL reports DSID_EDTN=0 because
        // the last applied .00N update file carries 0. Only the ".6" half is used.
        val entry = parse(
            document(record("US1GC09M", "74.6", revision = "2025-12-09", publication = "2026-05-20"))
        ).single()
        assertEquals("6:20251209:20260520", entry.revisionKey)
        assertTrue(!entry.revisionKey!!.contains("74"), "the edition number must not be in the key")
    }

    @Test
    fun `a record with no dates has a null revisionKey and is therefore always stale`() {
        val noDates = """
            <composedOf><DS_DataSet><has><MD_Metadata>
                <identificationInfo><MD_DataIdentification><citation><CI_Citation>
                    <title><gco:CharacterString>US5NODATE</gco:CharacterString></title>
                    <edition><gco:CharacterString>1.0</gco:CharacterString></edition>
                </CI_Citation></citation></MD_DataIdentification></identificationInfo>
                <distributionInfo><MD_Distribution><transferOptions><MD_DigitalTransferOptions>
                    <onLine><CI_OnlineResource><linkage>
                        <URL>https://www.charts.noaa.gov/ENCs/US5NODATE.zip</URL>
                    </linkage></CI_OnlineResource></onLine>
                </MD_DigitalTransferOptions></transferOptions></MD_Distribution></distributionInfo>
            </MD_Metadata></has></DS_DataSet></composedOf>
        """.trimIndent()
        assertEquals(null, parse(document(noDates)).single().revisionKey)
    }

    @Test
    fun `cell name is never the dataQualityInfo decoy`() {
        val entries = parse(document(record("US5WA22M", "44.2") + record("US1EEZ1M", "10.0")))
        assertEquals(listOf("US5WA22M", "US1EEZ1M"), entries.map { it.cell })
        assertTrue(entries.none { it.cell == "0" }, "the sourceCitation title must never win")
    }

    @Test
    fun `chartName appends the S-57 base cell extension`() {
        // charts.name is DSID_DSNM, which GDAL reports as the .000 file name.
        assertEquals("US5WA22M.000", parse(document(record("US5WA22M", "44.2"))).single().chartName)
    }

    @Test
    fun `edition with a zero update keeps its trailing zero`() {
        assertEquals("10.0", parse(document(record("US1EEZ1M", "10.0"))).single().edition)
    }

    @Test
    fun `records missing a required field are skipped not fatal`() {
        val incomplete = """
            <composedOf><DS_DataSet><has><MD_Metadata>
                <identificationInfo><MD_DataIdentification><citation><CI_Citation>
                    <title><gco:CharacterString>US5NOURL</gco:CharacterString></title>
                    <edition><gco:CharacterString>1.0</gco:CharacterString></edition>
                </CI_Citation></citation></MD_DataIdentification></identificationInfo>
            </MD_Metadata></has></DS_DataSet></composedOf>
        """.trimIndent()

        val entries = parse(document(incomplete + record("US5WA22M", "44.2")))
        assertEquals(listOf("US5WA22M"), entries.map { it.cell }, "the URL-less record is dropped")
    }

    @Test
    fun `parses identically when fed in tiny chunks`() {
        val xml = document(record("US5WA22M", "44.2") + record("US1EEZ1M", "10.0"))
        assertEquals(parse(xml), parse(xml, chunkSize = 13))
    }

    @Test
    fun `a changed namespace binding fails loudly`() {
        val ex = assertFailsWith<IllegalArgumentException> {
            parse(document(record("US5WA22M", "44.2"), gco = "http://example.com/other"))
        }
        assertTrue(
            ex.message!!.contains("namespace binding changed"),
            "should name the real problem: ${ex.message}",
        )
    }

    @Test
    fun `an unexpected root element fails loudly`() {
        val ex = assertFailsWith<IllegalArgumentException> {
            parse(document(record("US5WA22M", "44.2"), root = "NotACatalog"))
        }
        assertTrue(ex.message!!.contains("unexpected root element"), ex.message!!)
    }
}

class StaleSelectionTest {

    private val config = EncCronConfig()

    private fun entry(
        cell: String,
        edition: String,
        scale: Int? = 15000,
        sizeMb: Double? = 0.1,
        revision: String? = "20240101",
        publication: String? = "20240102",
    ) = EncCatalogEntry(cell, edition, "https://example.com/$cell.zip", scale, sizeMb, revision, publication)

    @Test
    fun `unchanged keys are skipped and changed ones selected`() {
        val catalog = listOf(entry("A", "1.0"), entry("B", "2.5"), entry("C", "3.0"))
        val have = mapOf("A.000" to "0:20240101:20240102", "B.000" to "4:20240101:20240102")

        // A matches; B's update number moved; C is absent entirely.
        assertEquals(listOf("B", "C"), selectStale(catalog, have, config).map { it.cell })
    }

    @Test
    fun `a changed publication date alone makes a cell stale`() {
        // Same update number, NOAA re-published: the ISDT half of the key catches it.
        val catalog = listOf(entry("A", "1.0", publication = "20240909"))
        assertEquals(1, selectStale(catalog, mapOf("A.000" to "0:20240101:20240102"), config).size)
    }

    @Test
    fun `a changed revision date alone makes a cell stale`() {
        // New base edition resets UPDN to 0, so only UADT distinguishes it from the old one.
        val catalog = listOf(entry("A", "1.0", revision = "20250505"))
        assertEquals(1, selectStale(catalog, mapOf("A.000" to "0:20240101:20240102"), config).size)
    }

    @Test
    fun `keys compare as strings so update 10 differs from update 1`() {
        // As numbers "1.10" and "1.1" both yield 1.1; as keys they must stay distinct.
        val catalog = listOf(entry("A", "1.10"))
        assertEquals(1, selectStale(catalog, mapOf("A.000" to "1:20240101:20240102"), config).size)
        assertEquals(0, selectStale(catalog, mapOf("A.000" to "10:20240101:20240102"), config).size)
    }

    @Test
    fun `a locally newer key is still treated as stale`() {
        // Any mismatch warrants a re-fetch, in either direction.
        val catalog = listOf(entry("A", "1.0"))
        assertEquals(1, selectStale(catalog, mapOf("A.000" to "9:20990101:20990102"), config).size)
    }

    @Test
    fun `a cell with an incomplete catalog entry is always stale`() {
        val catalog = listOf(entry("A", "1.0", publication = null))
        assertEquals(1, selectStale(catalog, mapOf("A.000" to "0:20240101:20240102"), config).size)
    }

    @Test
    fun `scaleFilter restricts the selection`() {
        val catalog = listOf(entry("A", "1.0", scale = 15000), entry("B", "1.0", scale = 3000000))
        val filtered = config.copy(scaleFilter = listOf(15000))
        assertEquals(listOf("A"), selectStale(catalog, emptyMap(), filtered).map { it.cell })
    }

    @Test
    fun `selection order is deterministic so capped runs make steady progress`() {
        val catalog = listOf(entry("C", "1.0"), entry("A", "1.0"), entry("B", "1.0"))
        assertEquals(listOf("A", "B", "C"), selectStale(catalog, emptyMap(), config).map { it.cell })
    }
}

class BatchingTest {

    private fun entry(cell: String, sizeMb: Double?) =
        EncCatalogEntry(cell, "1.0", "https://example.com/$cell.zip", 15000, sizeMb, "20240101", "20240102")

    @Test
    fun `splits on the cell count cap`() {
        val config = EncCronConfig(bundleSizeCells = 2)
        val cells = (1..5).map { entry("C$it", 0.01) }
        assertEquals(listOf(2, 2, 1), batches(cells, config).map { it.size })
    }

    @Test
    fun `splits early when the byte cap is hit first`() {
        // 1 MB compressed is estimated at 4 MB uncompressed, so two cells blow a 5 MB cap.
        val config = EncCronConfig(bundleSizeCells = 100, maxBundleUncompressedBytes = 5L * 1024 * 1024)
        val cells = (1..4).map { entry("C$it", 1.0) }
        assertEquals(listOf(1, 1, 1, 1), batches(cells, config).map { it.size })
    }

    @Test
    fun `an oversized single cell still gets its own bundle rather than being dropped`() {
        val config = EncCronConfig(bundleSizeCells = 100, maxBundleUncompressedBytes = 1024)
        val batches = batches(listOf(entry("HUGE", 500.0)), config)
        assertEquals(listOf(listOf("HUGE")), batches.map { b -> b.map { it.cell } })
    }

    @Test
    fun `cells with no published size are still batched`() {
        val config = EncCronConfig(bundleSizeCells = 2)
        val cells = (1..3).map { entry("C$it", null) }
        assertEquals(listOf(2, 1), batches(cells, config).map { it.size })
    }

    @Test
    fun `empty input yields no batches`() {
        assertTrue(batches(emptyList(), EncCronConfig()).isEmpty())
    }

    @Test
    fun `every cell appears exactly once across batches`() {
        val config = EncCronConfig(bundleSizeCells = 7)
        val cells = (1..50).map { entry("C$it", 0.1) }
        val flattened = batches(cells, config).flatten()
        assertEquals(cells, flattened)
    }
}
