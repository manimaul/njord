package io.madrona.njord.viewmodel

import io.madrona.njord.geojson.GeoJsonObject
import io.madrona.njord.js.MapLibre
import io.madrona.njord.js.moveEnd
import io.madrona.njord.js.onClick
import io.madrona.njord.js.renderedFeatures
import io.madrona.njord.model.Depth
import io.madrona.njord.model.Theme
import io.madrona.njord.model.stylePath

actual class ChartViewController actual constructor() {
    var mapView: MapLibre.Map? = null
        set(value) {
            field = value
            value?.moveEnd { onMoveEnd?.invoke(it) }
            value?.onClick { onClick?.invoke(it) }
        }

    actual var onMoveEnd: ((MapLocation) -> Unit)? = null
    actual var onClick: ((MapPoint) -> Unit)? = null

    actual fun move(location: MapLocation) {
    }

    actual fun fitBounds(bounds: MapLocation) {
    }

    actual fun queryRenderedFeatures(
        topLeft: MapPoint, bottomRight: MapPoint
    ): List<GeoJsonObject> {
        return mapView?.renderedFeatures(topLeft, bottomRight) ?: emptyList()
    }

    actual fun setStyle(theme: Theme, depth: Depth) {
        val style = stylePath(theme, depth)
        println("setting style $style")
        mapView?.setStyle(style)
    }
}
