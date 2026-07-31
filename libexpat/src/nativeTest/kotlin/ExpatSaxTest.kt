@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Covers [ExpatSax] + [ElementPath] against the shapes that matter for parsing NOAA's
 * ENCProdCat_19115.xml: a decoy `<title>` on a different ancestor path, and text nodes that
 * arrive split across two [ExpatSax.feed] calls.
 */
class ExpatSaxTest {

    /**
     * Miniature of the real catalog. Note the second `<title>` under `dataQualityInfo` with the
     * value "0" - that is verbatim what NOAA emits, and a naive first-match extractor picks it
     * up instead of the cell name.
     */
    private val catalogFixture = """
        <?xml version="1.0" encoding="UTF-8"?>
        <DS_Series xmlns="http://www.isotc211.org/2005/gmd"
                   xmlns:gco="http://www.isotc211.org/2005/gco">
            <composedOf>
                <DS_DataSet><has><MD_Metadata>
                    <identificationInfo><MD_DataIdentification><citation><CI_Citation>
                        <title><gco:CharacterString>US5WA22M</gco:CharacterString></title>
                        <edition><gco:CharacterString>44.2</gco:CharacterString></edition>
                    </CI_Citation></citation></MD_DataIdentification></identificationInfo>
                    <dataQualityInfo><DQ_DataQuality><lineage><LI_Lineage><source><LI_Source>
                        <sourceCitation><CI_Citation>
                            <title><gco:CharacterString>0</gco:CharacterString></title>
                            <date gco:nilReason="Date uknown"/>
                        </CI_Citation></sourceCitation>
                    </LI_Source></source></LI_Lineage></lineage></DQ_DataQuality></dataQualityInfo>
                </MD_Metadata></has></DS_DataSet>
            </composedOf>
        </DS_Series>
    """.trimIndent()

    private val cellTail = arrayOf(
        "identificationInfo", "MD_DataIdentification", "citation",
        "CI_Citation", "title", "gco:CharacterString"
    )

    /** Collects the value at [tail] using path scoping, exactly like EncProdCatParser does. */
    private class TailCollector(private val tail: Array<String>) : SaxHandler {
        private val path = ElementPath()
        private val text = StringBuilder()
        private var capturing = false
        val values = mutableListOf<String>()

        override fun startElement(name: String, atts: XmlAttrs) {
            path.push(name)
            capturing = path.endsWith(tail)
            if (capturing) text.setLength(0)
        }

        override fun characters(bytes: ByteArray) {
            if (capturing) text.append(bytes.decodeToString())
        }

        override fun endElement(name: String) {
            if (capturing) {
                values.add(text.toString().trim())
                capturing = false
            }
            path.pop()
        }
    }

    private fun parse(xml: String, handler: SaxHandler, chunkSize: Int = Int.MAX_VALUE) {
        val bytes = xml.encodeToByteArray()
        ExpatSax(handler).use { sax ->
            var off = 0
            while (off < bytes.size) {
                val n = minOf(chunkSize, bytes.size - off)
                sax.feed(bytes.copyOfRange(off, off + n), n)
                off += n
            }
            sax.finish()
        }
    }

    @Test
    fun `path scoping picks the citation title and not the dataQualityInfo decoy`() {
        val h = TailCollector(cellTail)
        parse(catalogFixture, h)
        assertEquals(listOf("US5WA22M"), h.values, "the sourceCitation title '0' must not match")
    }

    @Test
    fun `character data split across feeds is reassembled`() {
        // Split mid-word inside "US5WA22M" so the cell name arrives over two feed() calls.
        val marker = "US5WA22M"
        val split = catalogFixture.indexOf(marker) + 3
        assertTrue(split > 3, "fixture must contain $marker")

        val bytes = catalogFixture.encodeToByteArray()
        val h = TailCollector(cellTail)
        ExpatSax(h).use { sax ->
            sax.feed(bytes.copyOfRange(0, split), split)
            sax.feed(bytes.copyOfRange(split, bytes.size), bytes.size - split)
            sax.finish()
        }
        assertEquals(listOf("US5WA22M"), h.values)
    }

    @Test
    fun `tiny chunks produce the same result as one big feed`() {
        val h = TailCollector(cellTail)
        parse(catalogFixture, h, chunkSize = 7)
        assertEquals(listOf("US5WA22M"), h.values)
    }

    @Test
    fun `attributes are readable and NUL terminated array is respected`() {
        var rootAtts: Map<String, String>? = null
        val h = object : SaxHandler {
            var depth = 0
            override fun startElement(name: String, atts: XmlAttrs) {
                if (depth++ == 0) rootAtts = attsToMap(atts)
            }
        }
        parse(catalogFixture, h)
        assertEquals("http://www.isotc211.org/2005/gmd", rootAtts?.get("xmlns"))
        assertEquals("http://www.isotc211.org/2005/gco", rootAtts?.get("xmlns:gco"))
    }

    @Test
    fun `element with no attributes yields an empty map`() {
        val seen = mutableListOf<Map<String, String>>()
        val h = object : SaxHandler {
            override fun startElement(name: String, atts: XmlAttrs) {
                if (name == "composedOf") seen.add(attsToMap(atts))
            }
        }
        parse(catalogFixture, h)
        assertEquals(listOf(emptyMap()), seen)
    }

    @Test
    fun `malformed xml throws ExpatException with location`() {
        val ex = assertFailsWith<ExpatException> {
            parse("<a><b></a>", object : SaxHandler {})
        }
        assertTrue(ex.message!!.contains("line"), "message should carry a location: ${ex.message}")
    }

    @Test
    fun `truncated document is detected at finish`() {
        assertFailsWith<ExpatException> {
            parse("<a><b>text", object : SaxHandler {})
        }
    }

    @Test
    fun `handler exception aborts the parse and surfaces to the caller`() {
        val boom = object : SaxHandler {
            override fun startElement(name: String, atts: XmlAttrs) {
                if (name == "MD_Metadata") throw IllegalStateException("boom")
            }
        }
        val ex = assertFailsWith<IllegalStateException> { parse(catalogFixture, boom) }
        assertEquals("boom", ex.message)
    }

    @Test
    fun `feeding an empty buffer is a no-op`() {
        val h = TailCollector(cellTail)
        val bytes = catalogFixture.encodeToByteArray()
        ExpatSax(h).use { sax ->
            sax.feed(ByteArray(0))
            sax.feed(bytes)
            sax.feed(ByteArray(16), 0)
            sax.finish()
        }
        assertEquals(listOf("US5WA22M"), h.values)
    }

    @Test
    fun `utf8 multi byte characters survive chunking`() {
        val xml = "<r><v>café — über</v></r>"
        val h = TailCollector(arrayOf("r", "v"))
        parse(xml, h, chunkSize = 3)
        assertEquals(listOf("café — über"), h.values)
    }

    @Test
    fun `entity escapes are decoded`() {
        val xml = "<r><v>a &amp; b &lt;c&gt;</v></r>"
        val h = TailCollector(arrayOf("r", "v"))
        parse(xml, h)
        assertEquals(listOf("a & b <c>"), h.values)
    }
}

class ElementPathTest {

    @Test
    fun `endsWith matches only a full tail`() {
        val p = ElementPath()
        listOf("DS_Series", "composedOf", "identificationInfo", "citation", "title").forEach(p::push)
        assertTrue(p.endsWith(arrayOf("citation", "title")))
        assertTrue(p.endsWith(arrayOf("identificationInfo", "citation", "title")))
        assertTrue(!p.endsWith(arrayOf("sourceCitation", "title")))
        assertTrue(!p.endsWith(arrayOf("title", "citation")))
    }

    @Test
    fun `tail longer than the stack never matches`() {
        val p = ElementPath()
        p.push("a")
        assertTrue(!p.endsWith(arrayOf("root", "a")))
    }

    @Test
    fun `push and pop track depth and leaf`() {
        val p = ElementPath()
        assertEquals(0, p.depth)
        assertEquals(null, p.leaf())
        p.push("a")
        p.push("b")
        assertEquals(2, p.depth)
        assertEquals("b", p.leaf())
        assertEquals("a", p.at(1))
        assertEquals("b", p.pop())
        assertEquals(1, p.depth)
    }
}
