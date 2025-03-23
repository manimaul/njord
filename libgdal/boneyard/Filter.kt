/**
 * A filter which can be passed to a VectorTile decoder to optimize performance by only decoding layers of interest.
 */
abstract class Filter {
    abstract fun include(layerName: String): Boolean

    /**
     * A filter that only lets a single named layer be decoded.
     */
    class Single(private val layerName: String) : Filter() {
        override fun include(layerName: String): Boolean {
            return this.layerName == layerName
        }
    }

    /**
     * A filter that only allows the named layers to be decoded.
     */
    class Any(private val layerNames: Set<String>) : Filter() {
        override fun include(layerName: String): Boolean {
            return this.layerNames.contains(layerName)
        }
    }

    companion object {
        val ALL: Filter = object : Filter() {
            override fun include(layerName: String): Boolean {
                return true
            }
        }
    }
}
