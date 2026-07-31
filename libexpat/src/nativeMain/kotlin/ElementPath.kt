/**
 * A stack of raw qualified element names ("title", "gco:CharacterString", ...) tracking where the
 * parser currently is in the document tree.
 *
 * Push on [SaxHandler.startElement], pop on [SaxHandler.endElement].
 *
 * This exists because leaf element names alone are ambiguous in real world XML. ISO-19115, for
 * example, puts `<title><gco:CharacterString>` under both `identificationInfo` (the value you
 * want) and `dataQualityInfo/.../sourceCitation` (a decoy). Only the ancestor path tells them
 * apart.
 */
class ElementPath {
    private val stack = ArrayList<String>(32)

    val depth: Int
        get() = stack.size

    fun push(name: String) {
        stack.add(name)
    }

    fun pop(): String = stack.removeAt(stack.lastIndex)

    fun leaf(): String? = stack.lastOrNull()

    /** The element [fromEnd] levels above the leaf. `at(0)` is the leaf itself. */
    fun at(fromEnd: Int): String? = stack.getOrNull(stack.size - 1 - fromEnd)

    fun clear() {
        stack.clear()
    }

    /**
     * True when the current path ends with [tail], where `tail.last()` is the current leaf.
     *
     * Compares leaf first so that a non-matching path bails out after one or two string
     * comparisons - this runs for every element in the document, so the fast reject matters.
     */
    fun endsWith(tail: Array<String>): Boolean {
        if (stack.size < tail.size) return false
        var i = tail.size - 1
        var j = stack.size - 1
        while (i >= 0) {
            if (stack[j] != tail[i]) return false
            i--
            j--
        }
        return true
    }

    override fun toString(): String = stack.joinToString("/")
}
