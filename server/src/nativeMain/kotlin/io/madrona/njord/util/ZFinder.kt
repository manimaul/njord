package io.madrona.njord.util

class ZFinder(
    private val oneToOneZoom: Int = 28
) {

    init {
        if (oneToOneZoom !in 1..40) {
            throw IllegalArgumentException("oneToOneZoom must be between 1 and 40")
        }
    }

    /**
     * Scale is the ratio of distances of a map. ex 17999 means 17999:1
     */
    fun findZoom(scale: Int) : Int {
        var zoom = oneToOneZoom
        var zScale = scale.toDouble()
        while (zScale > 1.0) {
            zScale /= 2.0
            zoom -= 1
        }
        return zoom
    }
}

