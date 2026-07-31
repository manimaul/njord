@file:OptIn(ExperimentalForeignApi::class)

import kotlinx.cinterop.*
import libexpat.XML_CharacterDataHandler
import libexpat.XML_EndElementHandler
import libexpat.XML_GetCurrentColumnNumber
import libexpat.XML_GetCurrentLineNumber
import libexpat.XML_GetErrorCode
import libexpat.XML_ErrorString
import libexpat.XML_Parse
import libexpat.XML_Parser
import libexpat.XML_ParserCreate
import libexpat.XML_ParserFree
import libexpat.XML_SetCharacterDataHandler
import libexpat.XML_SetElementHandler
import libexpat.XML_SetUserData
import libexpat.XML_StartElementHandler
import libexpat.XML_StopParser
import libexpat.XML_STATUS_ERROR

class ExpatException(message: String) : RuntimeException(message)

/**
 * Expat's raw attribute array: `{key, value, key, value, ..., NULL}`. Passed through untouched so
 * that the vast majority of elements, whose attributes nobody reads, cost nothing. Use
 * [attsToMap] on the few where they matter.
 */
typealias XmlAttrs = CPointer<CPointerVar<ByteVar>>?

/**
 * Pure Kotlin callback surface for [ExpatSax]. No cinterop types leak out except the raw
 * attribute array, which callers normally ignore (use [attsToMap] on the rare element where
 * attributes matter - it allocates, so do not call it unconditionally).
 *
 * Implementations may throw: [ExpatSax] traps the throwable, aborts the parse and rethrows it
 * from [ExpatSax.feed]/[ExpatSax.finish]. They must not suspend - a C stack frame sits between
 * `feed()` and the callback.
 */
interface SaxHandler {
    fun startElement(name: String, atts: XmlAttrs) {}
    fun endElement(name: String) {}

    /**
     * Character data as UTF-8 bytes. Expat never splits a multi byte UTF-8 sequence across
     * calls, but it does split text nodes at input buffer boundaries, so a single text node may
     * arrive over several calls and must be accumulated until [endElement].
     */
    fun characters(bytes: ByteArray) {}
}

/** Reads expat's NUL terminated `{key, value, key, value, ..., NULL}` attribute array. */
fun attsToMap(atts: XmlAttrs): Map<String, String> {
    if (atts == null) return emptyMap()
    val out = LinkedHashMap<String, String>(4)
    var i = 0
    while (true) {
        val k = atts[i] ?: break
        val v = atts[i + 1] ?: break
        out[k.toKString()] = v.toKString()
        i += 2
    }
    return out
}

/**
 * Streaming SAX parser over libexpat. Feed it bytes as they arrive, call [finish] at end of
 * input, and always [close] it (it holds both a C parser and a [StableRef] GC root).
 *
 * ```
 * ExpatSax(handler).use { sax ->
 *     while (true) {
 *         val n = channel.readAvailable(buf, 0, buf.size)
 *         if (n == -1) break
 *         sax.feed(buf, n)
 *     }
 *     sax.finish()
 * }
 * ```
 *
 * Namespace processing is deliberately NOT enabled (`XML_ParserCreate`, not
 * `XML_ParserCreateNS`) - handlers see raw qualified names such as `gco:CharacterString`.
 * Callers that care should assert the document's prefix bindings on the root element.
 */
class ExpatSax(private val handler: SaxHandler) : AutoCloseable {

    private val selfRef: StableRef<ExpatSax> = StableRef.create(this)

    private val parser: XML_Parser = XML_ParserCreate(null) ?: run {
        selfRef.dispose()
        throw ExpatException("XML_ParserCreate failed")
    }

    private var closed = false

    /** Set when a handler threw; rethrown from [feed]/[finish] once the parser has stopped. */
    private var pendingFailure: Throwable? = null

    init {
        XML_SetUserData(parser, selfRef.asCPointer())
        XML_SetElementHandler(parser, startElementCb, endElementCb)
        XML_SetCharacterDataHandler(parser, charDataCb)
    }

    // Called from the static trampolines below. Must not be private - top level functions in
    // this file cannot see private class members.
    internal fun onStart(name: String, atts: XmlAttrs) = guard {
        handler.startElement(name, atts)
    }

    internal fun onEnd(name: String) = guard { handler.endElement(name) }

    internal fun onChars(bytes: ByteArray) = guard { handler.characters(bytes) }

    /**
     * A Kotlin exception unwinding through a C frame terminates the process, so handler
     * throwables are trapped here, the parse is aborted, and the throwable is re-raised from
     * the Kotlin side in [check].
     */
    private inline fun guard(block: () -> Unit) {
        if (pendingFailure != null) return
        try {
            block()
        } catch (t: Throwable) {
            pendingFailure = t
            XML_StopParser(parser, 0u) // resumable = XML_FALSE
        }
    }

    /** Feeds [length] bytes of [bytes] to the parser. Safe to call with a partially filled buffer. */
    fun feed(bytes: ByteArray, length: Int = bytes.size) {
        check(!closed) { "ExpatSax already closed" }
        // usePinned { addressOf(0) } throws on an empty array, and expat treats a zero length
        // chunk as a no-op anyway.
        if (length <= 0) return
        val status = bytes.usePinned { pinned ->
            XML_Parse(parser, pinned.addressOf(0), length, 0)
        }
        checkStatus(status)
    }

    /** Signals end of input. Must be called exactly once, or truncation goes undetected. */
    fun finish() {
        check(!closed) { "ExpatSax already closed" }
        checkStatus(XML_Parse(parser, null, 0, 1))
    }

    private fun checkStatus(status: UInt) {
        pendingFailure?.let {
            pendingFailure = null
            throw it
        }
        if (status.toInt() == XML_STATUS_ERROR) {
            val msg = XML_ErrorString(XML_GetErrorCode(parser))?.toKString() ?: "unknown error"
            throw ExpatException(
                "expat error at line ${XML_GetCurrentLineNumber(parser)}," +
                        " column ${XML_GetCurrentColumnNumber(parser)}: $msg"
            )
        }
    }

    override fun close() {
        if (closed) return
        closed = true
        XML_ParserFree(parser)
        // StableRef is a GC root - failing to dispose leaks this parser and its whole handler graph.
        selfRef.dispose()
    }
}

private fun startElementRaw(
    userData: COpaquePointer?,
    name: CPointer<ByteVar>?,
    atts: CPointer<CPointerVar<ByteVar>>?,
) {
    userData?.asStableRef<ExpatSax>()?.get()?.onStart(name?.toKString() ?: return, atts)
}

private fun endElementRaw(userData: COpaquePointer?, name: CPointer<ByteVar>?) {
    userData?.asStableRef<ExpatSax>()?.get()?.onEnd(name?.toKString() ?: return)
}

private fun charDataRaw(userData: COpaquePointer?, s: CPointer<ByteVar>?, len: Int) {
    if (s == null || len <= 0) return
    userData?.asStableRef<ExpatSax>()?.get()?.onChars(s.readBytes(len))
}

// Named top level functions rather than lambdas: staticCFunction infers the C signature from the
// function reference, which avoids fighting the generated typealiases.
private val startElementCb: XML_StartElementHandler = staticCFunction(::startElementRaw)
private val endElementCb: XML_EndElementHandler = staticCFunction(::endElementRaw)
private val charDataCb: XML_CharacterDataHandler = staticCFunction(::charDataRaw)
